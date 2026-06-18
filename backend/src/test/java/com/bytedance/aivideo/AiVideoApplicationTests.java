package com.bytedance.aivideo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.Answers;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AiVideoApplicationTests {

    // 自动在 Docker 中启动一个临时的 MySQL 8.0 容器，并将其连接信息注入到 Spring 中
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("aivideo")
            .withPassword("root");

    // 伪造（Mock）一个 ChromaApi，欺骗 Spring Boot，让它以为数据库已经连上了
    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ChromaApi chromaApi;

    // 伪造一个 Redis 连接工厂，防止它启动时去连本地 Redis 报错
    @MockitoBean
    private LettuceConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void contextLoads() {
        // 这个空方法仅仅用来测试 Spring 容器能不能成功启动
    }
}
