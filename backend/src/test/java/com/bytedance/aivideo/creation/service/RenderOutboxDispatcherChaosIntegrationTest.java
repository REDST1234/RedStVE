package com.bytedance.aivideo.creation.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.config.RabbitMQConfig;
import com.bytedance.aivideo.creation.entity.CreationProjectEntity;
import com.bytedance.aivideo.creation.entity.OutboxMessageEntity;
import com.bytedance.aivideo.creation.entity.RenderRecordEntity;
import com.bytedance.aivideo.creation.mapper.CreationProjectMapper;
import com.bytedance.aivideo.creation.mapper.OutboxMessageMapper;
import com.bytedance.aivideo.creation.mapper.RenderRecordMapper;
import com.bytedance.aivideo.engine.remotion.dto.RenderTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@SpringBootTest(properties = {
        // 关闭调度器自动触发，测试里用手动调用 dispatcher 的方式精确控制时序。
        "render.outbox.dispatcher.fixed-delay-ms=3600000",
        "render.outbox.dispatcher.recover-fixed-delay-ms=3600000",
        "render.runtime.timeout-check-fixed-delay-ms=3600000",
        // 本测试只验证“生产端投递状态机”，不需要真正启动消费者。
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RenderOutboxDispatcherChaosIntegrationTest {

    private static final String PROJECT_STATUS_CREATED = "CREATED";
    private static final String PROJECT_STATUS_QUEUED = "QUEUED";
    private static final String PROJECT_STATUS_FAILED = "FAILED";
    private static final String OUTBOX_TYPE_RENDER_SUBMIT = "RENDER_SUBMIT";
    private static final String OUTBOX_STATUS_PENDING = "PENDING";
    private static final String OUTBOX_STATUS_SENT = "SENT";
    private static final String OUTBOX_STATUS_DEAD = "DEAD";
    private static final String RENDER_STATUS_CREATED = "CREATED";
    private static final String RENDER_STATUS_QUEUED = "QUEUED";
    private static final String RENDER_STATUS_FAILED = "FAILED";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("aivideo")
            .withPassword("root");

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ChromaApi chromaApi;

    @MockitoBean
    private LettuceConnectionFactory redisConnectionFactory;

    // MySQL / RabbitMQ / Toxiproxy 共用一个网络，模拟“dispatcher -> proxy -> broker”的真实链路。
    private static final Network network = Network.newNetwork();

    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.13-management")
            .withNetwork(network)
            .withNetworkAliases("rabbitmq");

    @Container
    static ToxiproxyContainer toxiproxy = new ToxiproxyContainer()
            .withNetwork(network);

    private static ToxiproxyContainer.ContainerProxy rabbitMqProxy;

    @DynamicPropertySource
    static void configureRabbitProperties(DynamicPropertyRegistry registry) {
        // Spring Rabbit 不是直连 broker，而是先连到 Toxiproxy。
        // 这样测试里只需切 proxy 状态，就能稳定复现断网、恢复等网络故障。
        rabbitMqProxy = toxiproxy.getProxy("rabbitmq", 5672);
        registry.add("spring.rabbitmq.host", toxiproxy::getHost);
        registry.add("spring.rabbitmq.port", rabbitMqProxy::getProxyPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @Autowired
    private RenderOutboxDispatcher renderOutboxDispatcher;

    @Autowired
    private OutboxMessageMapper outboxMessageMapper;

    @Autowired
    private RenderRecordMapper renderRecordMapper;

    @Autowired
    private CreationProjectMapper creationProjectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CachingConnectionFactory rabbitConnectionFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        // 每个用例都从“可重试但不会无限等待”的统一初始态开始。
        ReflectionTestUtils.setField(renderOutboxDispatcher, "maxRetryCount", 4);
        rabbitMqProxy.setConnectionCut(false);
        rabbitMqProxy.toxics().getAll().forEach(toxic -> {
            try {
                toxic.remove();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        rabbitConnectionFactory.resetConnection();
        purgeQueues();
        cleanupTables();
    }

    @Test
    @DisplayName("混沌状态机 1：断网时 dispatcher 应保持 CREATED，并把 outbox 打回 PENDING 等待重试")
    void shouldKeepCreatedAndScheduleRetryWhenRabbitNetworkIsCut() throws IOException {
        SeededChain seeded = seedPendingRenderChain();

        // 直接切断 proxy 到 broker 的连接，模拟“发送瞬间网络不可达”。
        rabbitMqProxy.setConnectionCut(true);
        rabbitConnectionFactory.resetConnection();

        // 不等定时器，手动驱动一次 dispatcher，聚焦验证本轮状态推进。
        renderOutboxDispatcher.dispatchPendingMessages();

        OutboxMessageEntity outbox = getOutbox(seeded.renderId());
        RenderRecordEntity renderRecord = getRenderRecord(seeded.renderId());
        CreationProjectEntity project = getProject(seeded.projectId());

        Assertions.assertEquals(OUTBOX_STATUS_PENDING, outbox.getStatus());
        Assertions.assertEquals(1, outbox.getRetryCount());
        Assertions.assertNotNull(outbox.getNextRetryAt());
        Assertions.assertTrue(outbox.getNextRetryAt().isAfter(LocalDateTime.now().minusSeconds(1)));
        Assertions.assertNotNull(outbox.getLastError());

        Assertions.assertEquals(RENDER_STATUS_CREATED, renderRecord.getStatus());
        Assertions.assertEquals(PROJECT_STATUS_CREATED, project.getStatus());
    }

    @Test
    @DisplayName("混沌状态机 2：首次断网失败后，网络恢复时应重试成功并推进到 QUEUED")
    void shouldAdvanceToQueuedAfterNetworkRecovery() throws IOException {
        SeededChain seeded = seedPendingRenderChain();

        // 第一轮先制造失败，验证 outbox 会留下“待重试”的中间态。
        rabbitMqProxy.setConnectionCut(true);
        rabbitConnectionFactory.resetConnection();
        renderOutboxDispatcher.dispatchPendingMessages();

        OutboxMessageEntity firstAttempt = getOutbox(seeded.renderId());
        Assertions.assertNotNull(firstAttempt);
        Assertions.assertEquals(OUTBOX_STATUS_PENDING, firstAttempt.getStatus());
        Assertions.assertEquals(1, firstAttempt.getRetryCount());
        // 测试不等待真实退避时间，直接把 nextRetryAt 调回过去，模拟“到达下一次可重试窗口”。
        outboxMessageMapper.update(
                null,
                new LambdaUpdateWrapper<OutboxMessageEntity>()
                        .eq(OutboxMessageEntity::getId, firstAttempt.getId())
                        .set(OutboxMessageEntity::getNextRetryAt, LocalDateTime.now().minusSeconds(1))
        );

        // 第二轮恢复网络后再次派发，验证链路能从失败中恢复，而不是卡死在 PENDING。
        rabbitMqProxy.setConnectionCut(false);
        rabbitConnectionFactory.resetConnection();

        renderOutboxDispatcher.dispatchPendingMessages();

        OutboxMessageEntity outbox = getOutbox(seeded.renderId());
        RenderRecordEntity renderRecord = getRenderRecord(seeded.renderId());
        CreationProjectEntity project = getProject(seeded.projectId());

        Assertions.assertEquals(OUTBOX_STATUS_SENT, outbox.getStatus());
        Assertions.assertEquals(1, outbox.getRetryCount());
        Assertions.assertNull(outbox.getLastError());
        Assertions.assertEquals(RENDER_STATUS_QUEUED, renderRecord.getStatus());
        Assertions.assertEquals(PROJECT_STATUS_QUEUED, project.getStatus());
    }

    @Test
    @DisplayName("混沌状态机 3：重试预算耗尽后，链路应收口到 DEAD / FAILED")
    void shouldMarkRenderChainFailedWhenRetryBudgetIsExhausted() throws IOException {
        SeededChain seeded = seedPendingRenderChain();

        // 把最大重试次数压到 0，等价于“这次失败后立即触发最终收口逻辑”。
        ReflectionTestUtils.setField(renderOutboxDispatcher, "maxRetryCount", 0);
        rabbitMqProxy.setConnectionCut(true);
        rabbitConnectionFactory.resetConnection();

        renderOutboxDispatcher.dispatchPendingMessages();

        OutboxMessageEntity outbox = getOutbox(seeded.renderId());
        RenderRecordEntity renderRecord = getRenderRecord(seeded.renderId());
        CreationProjectEntity project = getProject(seeded.projectId());

        Assertions.assertEquals(OUTBOX_STATUS_DEAD, outbox.getStatus());
        Assertions.assertEquals(1, outbox.getRetryCount());
        Assertions.assertNotNull(outbox.getLastError());
        Assertions.assertEquals(RENDER_STATUS_FAILED, renderRecord.getStatus());
        Assertions.assertEquals(PROJECT_STATUS_FAILED, project.getStatus());
    }

    private SeededChain seedPendingRenderChain() {
        // 这里显式造出“业务记录 + 渲染记录 + outbox 消息”三段链路，
        // 让每个测试都从最小但完整的最终一致性单元开始。
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String projectId = "it_project_" + suffix;
        String renderId = "it_render_" + suffix;

        CreationProjectEntity project = new CreationProjectEntity();
        project.setProjectId(projectId);
        project.setTitle("Chaos Integration Project");
        project.setDescription("dispatcher chaos integration test");
        project.setStatus(PROJECT_STATUS_CREATED);
        project.setLatestRenderId(renderId);
        creationProjectMapper.insert(project);

        RenderRecordEntity renderRecord = new RenderRecordEntity();
        renderRecord.setProjectId(projectId);
        renderRecord.setRenderId(renderId);
        renderRecord.setStatus(RENDER_STATUS_CREATED);
        renderRecord.setAspectRatio("16:9");
        renderRecordMapper.insert(renderRecord);

        OutboxMessageEntity outbox = new OutboxMessageEntity();
        outbox.setMessageId("msg_" + suffix);
        outbox.setMessageType(OUTBOX_TYPE_RENDER_SUBMIT);
        outbox.setBizKey(renderId);
        outbox.setPayload(buildRenderTaskPayload(renderId));
        outbox.setStatus(OUTBOX_STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        outboxMessageMapper.insert(outbox);

        return new SeededChain(projectId, renderId);
    }

    private String buildRenderTaskPayload(String renderId) {
        try {
            RenderTaskRequest request = new RenderTaskRequest();
            request.setTaskId(renderId);
            request.setUserId("test_user_001");
            request.setCost(10);
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build render task payload", e);
        }
    }

    private void cleanupTables() {
        outboxMessageMapper.delete(null);
        renderRecordMapper.delete(null);
        creationProjectMapper.delete(null);
    }

    private void purgeQueues() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(RabbitMQConfig.QUEUE_RENDER_TASK);
            channel.queuePurge(RabbitMQConfig.QUEUE_RENDER_DLQ);
            return null;
        });
    }

    private OutboxMessageEntity getOutbox(String renderId) {
        return outboxMessageMapper.selectOne(
                new LambdaQueryWrapper<OutboxMessageEntity>()
                        .eq(OutboxMessageEntity::getBizKey, renderId)
                        .last("LIMIT 1")
        );
    }

    private RenderRecordEntity getRenderRecord(String renderId) {
        return renderRecordMapper.selectOne(
                new LambdaQueryWrapper<RenderRecordEntity>()
                        .eq(RenderRecordEntity::getRenderId, renderId)
                        .last("LIMIT 1")
        );
    }

    private CreationProjectEntity getProject(String projectId) {
        return creationProjectMapper.selectOne(
                new LambdaQueryWrapper<CreationProjectEntity>()
                        .eq(CreationProjectEntity::getProjectId, projectId)
                        .last("LIMIT 1")
        );
    }

    private record SeededChain(String projectId, String renderId) {
    }
}
