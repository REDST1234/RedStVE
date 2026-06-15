package com.bytedance.aivideo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@SpringBootTest
class AiVideoApplicationTests {

    // 伪造（Mock）一个 ChromaApi，欺骗 Spring Boot，让它以为数据库已经连上了
    @MockitoBean
    private ChromaApi chromaApi;

    // 伪造一个 Redis 连接工厂，防止它启动时去连本地 Redis 报错
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
        // 这个空方法仅仅用来测试 Spring 容器能不能成功启动
    }
}
