package com.bytedance.aivideo.test.logging;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 为所有 JUnit 5 测试补充统一的日志上下文，保证手动执行测试时日志可追溯。
 */
public class TestLogContextExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger(TestLogContextExtension.class);
    private static final String TEST_RUN_ID_PROPERTY = "aivideo.test.run-id";

    @Override
    public void beforeAll(ExtensionContext context) {
        ensureTestRunId();
        String testClass = context.getRequiredTestClass().getSimpleName();
        log.info("==== TEST CLASS START: {} | runId={} ====", testClass, System.getProperty(TEST_RUN_ID_PROPERTY));
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        ensureTestRunId();
        String testClass = context.getRequiredTestClass().getSimpleName();
        String testMethod = context.getRequiredTestMethod().getName();
        String displayName = context.getDisplayName();

        MDC.put("testRunId", System.getProperty(TEST_RUN_ID_PROPERTY));
        MDC.put("testClass", testClass);
        MDC.put("testMethod", testMethod);

        log.info("---- TEST START: {}#{} | displayName={} ----", testClass, testMethod, displayName);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        try {
            String testClass = context.getRequiredTestClass().getSimpleName();
            String testMethod = context.getRequiredTestMethod().getName();
            String status = context.getExecutionException().isPresent() ? "FAILED" : "PASSED";
            log.info("---- TEST END: {}#{} | result={} ----", testClass, testMethod, status);
        } finally {
            MDC.remove("testMethod");
            MDC.remove("testClass");
            MDC.remove("testRunId");
        }
    }

    private static void ensureTestRunId() {
        if (System.getProperty(TEST_RUN_ID_PROPERTY) != null) {
            return;
        }
        synchronized (TestLogContextExtension.class) {
            if (System.getProperty(TEST_RUN_ID_PROPERTY) == null) {
                String runId = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                System.setProperty(TEST_RUN_ID_PROPERTY, runId);
            }
        }
    }
}
