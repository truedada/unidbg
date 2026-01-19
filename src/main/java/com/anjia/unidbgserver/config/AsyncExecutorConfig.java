package com.anjia.unidbgserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
public class AsyncExecutorConfig {

    /**
     * Spring Boot 在某些裁剪/打包场景下可能不会自动创建 applicationTaskExecutor（或被禁用）。
     * 本项目显式补一个同名 Bean，避免 CompletableFuture 默认落到 common pool。
     */
    @Bean(name = "applicationTaskExecutor")
    @ConditionalOnMissingBean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor(
        @Value("${spring.task.execution.pool.core-size:8}") int coreSize,
        @Value("${spring.task.execution.pool.max-size:8}") int maxSize,
        @Value("${spring.task.execution.pool.queue-capacity:256}") int queueCapacity,
        @Value("${spring.task.execution.pool.keep-alive:60s}") Duration keepAlive,
        @Value("${spring.task.execution.pool.allow-core-thread-timeout:true}") boolean allowCoreThreadTimeout,
        @Value("${spring.task.execution.thread-name-prefix:applicationTaskExecutor-}") String threadNamePrefix
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, coreSize));
        executor.setMaxPoolSize(Math.max(executor.getCorePoolSize(), maxSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setKeepAliveSeconds((int) Math.max(0, keepAlive.getSeconds()));
        executor.setAllowCoreThreadTimeOut(allowCoreThreadTimeout);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }

    /**
     * 章节预取/目录预取用的独立线程池：
     * 这些任务会被业务线程 join 等待，必须与业务线程池隔离，避免线程耗尽导致死锁。
     */
    @Bean(name = "fqPrefetchExecutor")
    @ConditionalOnMissingBean(name = "fqPrefetchExecutor")
    public Executor fqPrefetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("fq-prefetch-");
        executor.initialize();
        return executor;
    }
}
