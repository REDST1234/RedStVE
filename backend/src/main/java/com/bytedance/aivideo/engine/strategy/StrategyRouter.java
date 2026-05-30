package com.bytedance.aivideo.engine.strategy;

import com.bytedance.aivideo.common.error.ErrorCode;
import com.bytedance.aivideo.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class StrategyRouter {

    private final Map<String, StrategyExecutor> executorMap;

    public StrategyRouter(List<StrategyExecutor> executors) {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(StrategyExecutor::getStrategyType, Function.identity()));
        log.info("StrategyRouter initialized with executors: {}", executorMap.keySet());
    }

    public StrategyExecutor getExecutor(String strategyType) {
        StrategyExecutor executor = executorMap.get(strategyType);
        if (executor == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "未找到对应的策略执行器: " + strategyType);
        }
        return executor;
    }
}
