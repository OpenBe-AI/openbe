package com.openbe.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openbe.common.LaneColor;
import com.openbe.common.Pheromone;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 信息素车道路由器。
 *
 * 监听器注册时序说明：
 *  - RedisMessageListenerContainer 是 SmartLifecycle Bean，在 Spring context refresh()
 *    结束时由 DefaultLifecycleProcessor 调用 start()。
 *  - 服务 Bean 构造函数在 refresh() 过程中执行，早于 SmartLifecycle.start()。
 *  - 因此在构造函数中调用 listenLane() → addMessageListener() 可确保所有监听器
 *    在容器首次 start() 之前注册完毕，避免动态注册的异步竞态。
 */
@Service
public class LaneQueueRouter {

    private static final String TOPIC_PREFIX = "openbe.lane.";

    private final StringRedisTemplate              redisTemplate;
    private final RedisMessageListenerContainer    listenerContainer;
    private final ObjectMapper                     objectMapper;

    public LaneQueueRouter(StringRedisTemplate redisTemplate,
                           RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate     = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.objectMapper      = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── 发布 ────────────────────────────────────────────────────────────────

    public void emitPheromone(Pheromone pheromone) {
        String topic = topicFor(pheromone.getLaneColor());
        try {
            String json = objectMapper.writeValueAsString(pheromone);
            System.out.printf("\033[90m[Gateway] 📤 -> %s | taskId: %s | target: %s\033[0m%n",
                topic, pheromone.getTaskId(), pheromone.getTargetBee());
            redisTemplate.convertAndSend(topic, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("信息素序列化失败: " + e.getMessage(), e);
        }
    }

    // ── 订阅 ────────────────────────────────────────────────────────────────

    /**
     * 注册车道监听器。
     *
     * 必须在 Bean 构造函数中调用（而非 @EventListener 回调），确保监听器在
     * RedisMessageListenerContainer.start() 之前注册，避免动态注册竞态。
     */
    public void listenLane(LaneColor color, Consumer<Pheromone> processor) {
        String channelName = topicFor(color);

        MessageListener listener = (Message message, byte[] pattern) -> {
            String raw = "(empty)";
            try {
                raw = new String(message.getBody(), StandardCharsets.UTF_8);
                System.out.printf("\033[90m[Gateway] 📨 收到消息 -> %s | %.80s\033[0m%n",
                    channelName, raw);
                Pheromone pheromone = objectMapper.readValue(raw, Pheromone.class);
                processor.accept(pheromone);
            } catch (Exception e) {
                System.err.printf("[Gateway] ✗ 消息处理异常 -> %s | body: %.80s | err: %s%n",
                    channelName, raw, e.getMessage());
            }
        };

        listenerContainer.addMessageListener(listener, new ChannelTopic(channelName));
        System.out.printf("\033[32m[Gateway] ✓ 监听器已注册 -> %s\033[0m%n", channelName);
    }

    // ── 通用频道订阅（工作区同步等）────────────────────────────────────────

    /**
     * 订阅任意 Redis Pub/Sub 频道，接收原始 JSON 字符串。
     * 必须在 Bean 构造函数中调用，保证在容器 start() 前注册。
     */
    public void subscribeChannel(String channel, java.util.function.Consumer<String> handler) {
        listenerContainer.addMessageListener(
            (message, pattern) -> handler.accept(new String(message.getBody(), StandardCharsets.UTF_8)),
            new ChannelTopic(channel));
        System.out.printf("\033[32m[Gateway] ✓ 频道监听器已注册 -> %s\033[0m%n", channel);
    }

    /** 向任意频道发布原始字符串消息 */
    public void publishChannel(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private String topicFor(LaneColor color) {
        return TOPIC_PREFIX + color.name().toLowerCase();
    }
}
