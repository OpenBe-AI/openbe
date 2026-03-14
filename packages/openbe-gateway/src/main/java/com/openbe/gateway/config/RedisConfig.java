package com.openbe.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Redis 订阅监听器容器配置。
 *
 * 关键点：
 *  1. 显式配置 ThreadPoolTaskExecutor，命名线程便于调试，保证非守护线程。
 *  2. 配置 ErrorHandler，订阅回调异常不再静默吞掉。
 *  3. 监听器必须在容器 start() 之前注册（即在 Bean 构造函数中），
 *     容器启动时才能一次性建立所有订阅，避免动态 addMessageListener 的竞态。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        // 显式线程池：命名线程，便于日志追踪；非守护线程保活 JVM
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setThreadNamePrefix("redis-listener-");
        executor.setDaemon(false);
        executor.initialize();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(executor);
        container.setErrorHandler(e ->
            System.err.printf("[Gateway] ✗ Redis 监听器错误: %s%n", e.getMessage()));

        return container;
    }
}
