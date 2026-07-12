package com.bytedance.aivideo.engine.remotion;

import com.bytedance.aivideo.account.service.AccountService;
import com.bytedance.aivideo.config.RabbitMQConfig;
import com.bytedance.aivideo.creation.dto.remotion.CompositionScript;
import com.bytedance.aivideo.engine.remotion.dto.RenderResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.mockito.Answers;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import lombok.extern.slf4j.Slf4j;

/**
 * 混沌工程测试类：专门验证各种极限环境下的容灾降级能力
 */
@SpringBootTest
@Slf4j
@ActiveProfiles("test") // 使用 test 配置，因为我们使用了 Testcontainers 来模拟临时数据库，配合 CI 环境
@Testcontainers(disabledWithoutDocker = true)
public class RemotionResilienceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("aivideo")
            .withPassword("root");

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ChromaApi chromaApi;

    @MockitoBean
    private LettuceConnectionFactory redisConnectionFactory;

    @Autowired
    private RemotionServiceClient remotionServiceClient;

    // 劫持真实的 RabbitTemplate，模拟网络故障
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    // 劫持账户服务，用来验证是否触发了退钱动作，而不需要真的连数据库
    @MockitoBean
    private AccountService accountService;

    @Test
    @DisplayName("混沌测试：模拟 RabbitMQ 宕机或网络断连时的发件箱兜底补偿")
    void testMQDownSynchronousCompensation() {
        String taskId = "CHAOS-TASK-001";
        CompositionScript dummyScript = new CompositionScript();

        // 1. 模拟网络异常：当向 RabbitMQ 发送消息时，强制抛出连接异常！
        Mockito.doThrow(new AmqpConnectException("Connection refused: connect", new java.net.ConnectException()))
                .when(rabbitTemplate).convertAndSend(
                        eq(RabbitMQConfig.EXCHANGE_REMOTION),
                        eq(RabbitMQConfig.ROUTING_KEY_RENDER),
                        any(Object.class));

        // 2. 模拟发起一次渲染任务
        RenderResponse response = remotionServiceClient.submitRenderTask(taskId, dummyScript);

        // 3. 断言验证：系统不应该白屏崩溃，而应该返回 FAILED 状态并告知前端退钱成功
        Assertions.assertEquals("FAILED", response.getStatus(), "网络异常时，任务状态必须是 FAILED");
        Assertions.assertTrue(response.getError().contains("MQ Down"), "必须返回明确的网络故障提示");

        // 4. 断言验证核心逻辑：确保立即同步调用了退钱接口！
        Mockito.verify(accountService, Mockito.times(1))
                .refundPoints(
                        eq("test_user_001"),
                        eq(10),
                        eq("SYNC_REFUND_" + taskId),
                        any(String.class));

        log.info("✅ 混沌测试通过：成功拦截网络异常，并执行同步退款！");
    }
}
