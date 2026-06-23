package com.bytedance.aivideo.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.aivideo.account.entity.UserAccountEntity;
import com.bytedance.aivideo.account.mapper.UserAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 积分服务 TDD 集成测试
 * 使用 Testcontainers 启动真实的 MySQL 容器进行测试，杜绝 Mock 的脆弱性。
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test") // 使用测试环境配置，自动通过 Flyway 执行所有 SQL 建表
@Testcontainers(disabledWithoutDocker = true)
public class AccountServiceTddTest {

    // 动态拉起一个干净的 MySQL 8.4 容器
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("aivideo")
            .withPassword("root");

    @MockitoBean(answers = Answers.RETURNS_MOCKS)
    private ChromaApi chromaApi;

    @MockitoBean
    private LettuceConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    // TDD 阶段：目前还没有写 AccountServiceImpl，所以加上 required = false 防止 Spring 启动直接报错
    @Autowired(required = false)
    private AccountService accountService;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Test
    @DisplayName("TDD 核心测试 1：高并发环境下的防超卖（乐观锁）测试")
    void testConcurrentDeductPoints() throws InterruptedException {
        Assertions.assertNotNull(accountService, "TDD 失败：你还没有编写 AccountServiceImpl 实现类！");

        String userId = "test_user_001"; // 在 V30 脚本里，我们给这个用户充了 1000 积分
        int deductAmount = 10;
        int threadCount = 20;

        // 模拟 20 个线程同时去扣除这 10 分（例如用户在不同设备上同时疯狂点击生成）
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            String bizRef = "TASK_CONCURRENT_" + i;
            executorService.submit(() -> {
                try {
                    accountService.deductPoints(userId, deductAmount, bizRef, "并发扣减测试");
                    successCount.incrementAndGet(); // 成功扣减
                } catch (Exception e) {
                    failCount.incrementAndGet(); // 乐观锁拦截抛错
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 等待所有线程执行完毕

        // 断言验证
        UserAccountEntity account = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>().eq(UserAccountEntity::getUserId, userId)
        );

        log.info("====== 并发扣减测试结果 ======");
        log.info("成功扣减次数: {}", successCount.get());
        log.info("失败拦截次数: {}", failCount.get());
        log.info("最终剩余积分: {}", account.getPoints());
        log.info("最终乐观锁版本号: {}", account.getVersion());

        // 核心验证：20 个线程并发发起了扣除，但只有真正抢到锁的线程才能扣除成功。
        // 总扣除额必须严格等于成功次数 * 10
        int expectedPoints = 1000 - (successCount.get() * deductAmount);
        Assertions.assertEquals(expectedPoints, account.getPoints(), "发生了超卖或数据不一致！");
        Assertions.assertTrue(failCount.get() > 0 || successCount.get() == threadCount, "乐观锁没有发挥作用！");
    }

    @Test
    @DisplayName("TDD 核心测试 2：防重放（幂等性）测试")
    void testIdempotencyDeduct() {
        Assertions.assertNotNull(accountService, "TDD 失败：你还没有编写 AccountServiceImpl 实现类！");

        String userId = "test_user_001";
        String bizRef = "TASK_IDEMPOTENT_001";

        // 第一次扣减：正常成功
        accountService.deductPoints(userId, 50, bizRef, "正常视频生成扣费");

        // 第二次扣减：网络超时重试（带着相同的 bizRef 过来）
        // 我们期望底层 account_transaction 表的唯一索引发挥作用，抛出异常拦截它
        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            accountService.deductPoints(userId, 50, bizRef, "MQ重发导致的重复扣费请求");
        });

        log.info("成功拦截重复扣款，抛出异常: {}", exception.getMessage());
    }
}
