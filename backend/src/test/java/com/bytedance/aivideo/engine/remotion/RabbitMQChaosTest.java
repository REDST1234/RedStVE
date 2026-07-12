package com.bytedance.aivideo.engine.remotion;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.amqp.AmqpException;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public class RabbitMQChaosTest {

    // 为了让 Spring 容器顺利启动，顺带起一个 MySQL
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("aivideo")
            .withPassword("root");

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ChromaApi chromaApi;

    @MockitoBean
    private LettuceConnectionFactory redisConnectionFactory;

    // ================= 混沌工程核心沙盒配置 =================

    // 1. 创建一个共享的 Docker 虚拟网络
    private static final Network network = Network.newNetwork();

    // 2. 在虚拟网络中启动真实的 RabbitMQ 容器
    @Container
    public static final RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.13-management")
            .withNetwork(network)
            .withNetworkAliases("rabbitmq");

    // 3. 在同一个虚拟网络中启动 Toxiproxy 容器
    @Container
    public static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer()
            .withNetwork(network);

    private static ToxiproxyContainer.ContainerProxy rabbitMqProxy;

    // 4. 动态劫持 Spring Boot 的配置，让所有发件请求落入代理陷阱
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 让 Toxiproxy 将本地端口代理到内部的 "rabbitmq:5672"
        rabbitMqProxy = toxiproxy.getProxy("rabbitmq", 5672);

        // 强行修改 Spring 的 RabbitMQ 连接地址为 Toxiproxy 的代理地址
        registry.add("spring.rabbitmq.host", toxiproxy::getHost);
        registry.add("spring.rabbitmq.port", rabbitMqProxy::getProxyPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    // 这次我们要测试真实的 RabbitTemplate，所以不 Mock 它！
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CachingConnectionFactory rabbitConnectionFactory;

    @BeforeEach
    void setUp() throws IOException {
        // ContainerProxy 会用内部带名字的 bandwidth toxic 模拟断网，先撤掉它再做通用清理。
        rabbitMqProxy.setConnectionCut(false);

        // 每次测试前，清空代理上的所有毒药，确保网络恢复正常
        rabbitMqProxy.toxics().getAll().forEach(toxic -> {
            try {
                toxic.remove();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        rabbitConnectionFactory.resetConnection();
    }

    @Test
    @DisplayName("混沌测试 1：模拟发送时物理断网")
    void testNetworkCut() throws IOException {
        // 1. 正常情况，网络畅通，发消息应该成功
        rabbitTemplate.convertAndSend("amq.direct", "test-key", "Normal Message Before Chaos");

        // 2. 直接切断代理连接，模拟发件链路彻底中断
        rabbitMqProxy.setConnectionCut(true);
        rabbitConnectionFactory.resetConnection();

        log.warn("☠️ 毒药已注入：物理网络已被强行切断！");

        // 3. 此时发送消息，必然由于 Socket Closed 而抛出异常！
        Assertions.assertThrows(AmqpException.class, () -> {
            rabbitTemplate.convertAndSend("amq.direct", "test-key", "Failed Message During Chaos");
        }, "未能正确识别断网状态！你的代码在断网时会挂起！");

        log.info("✅ 断网测试通过：成功拦截到了底层的断网异常，有了它，业务层就能触发退钱补偿了。");
    }

    @Test
    @DisplayName("混沌测试 2：模拟弱网高延迟环境 (High Latency)")
    void testHighLatency() throws IOException {
        log.info("开始弱网测试，这需要花费几秒钟...");

        // 1. 注入慢毒药：让所有进出的网络包强行延迟 3000ms（模拟极度拥堵的网络）
        rabbitMqProxy.toxics().latency("slow_network_upstream", ToxicDirection.UPSTREAM, 3000);
        rabbitMqProxy.toxics().latency("slow_network_downstream", ToxicDirection.DOWNSTREAM, 3000);
        rabbitConnectionFactory.resetConnection();

        long start = System.currentTimeMillis();

        try {
            // 因为网络极慢，这里会阻塞好几秒钟
            rabbitTemplate.convertAndSend("amq.direct", "test-key", "Slow Message");
        } catch (Exception e) {
            log.error("弱网导致发送失败：{}", e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        log.info("⏳ 弱网下发件耗时：{} ms", duration);
        
        // 断言验证：发送耗时要明显变慢，而不是脆弱地精确卡死在 3000ms。
        Assertions.assertTrue(duration >= 2000, "弱网毒药未能生效！");
        log.info("✅ 弱网测试执行完毕。");
    }
}
