package com.openbe.gateway;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 蜜蜂健康上报组件。
 *
 * 每隔 10s 将当前蜜蜂的健康指标写入 Redis Hash（openbe:health:{beeType}），
 * TTL 30s，超时未续期则被视为离线。
 *
 * 使用方式：
 *  1. 在各 bee 的 application.yml 中配置 openbe.bee.type=QUEEN/WORKER/NURSE
 *  2. 注入 HealthReporter 后调用 setStatus() / setExtra() 更新状态
 */
@Component
public class HealthReporter {

    private static final String KEY_PREFIX     = "openbe:health:";
    private static final long   TTL_SECONDS    = 30;
    private static final int    INTERVAL_SEC   = 10;

    @Value("${openbe.bee.type:UNKNOWN}")
    private String beeType;

    @Value("${openbe.bee.name:}")
    private String beeName;

    private final StringRedisTemplate         redisTemplate;
    private volatile String                   currentStatus = "ONLINE";
    private final Map<String, String>         extraFields   = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "health-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public HealthReporter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void startHeartbeat() {
        heartbeat.scheduleAtFixedRate(this::flush, 1, INTERVAL_SEC, TimeUnit.SECONDS);
    }

    /** 立即更新状态并写入 Redis */
    public void setStatus(String status) {
        this.currentStatus = status;
        flush();
    }

    /** 写入附加字段（如 activeTasks, ollamaAvailable, totalHoney） */
    public void setExtra(String key, String value) {
        extraFields.put(key, value);
    }

    // 每个实例用 pid 区分，key = openbe:health:{beeType}:{pid}
    private final long selfPid = ProcessHandle.current().pid();

    private void flush() {
        try {
            Runtime rt    = Runtime.getRuntime();
            long usedMB   = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
            long totalMB  = rt.maxMemory() / 1_048_576;

            Map<String, Object> fields = new HashMap<>();
            fields.put("status",      currentStatus);
            fields.put("memoryMB",    String.valueOf(usedMB));
            fields.put("maxMemoryMB", String.valueOf(totalMB));
            fields.put("heartbeat",   String.valueOf(System.currentTimeMillis()));
            fields.put("pid",         String.valueOf(selfPid));
            fields.put("beeType",     beeType);
            if (beeName != null && !beeName.isBlank()) {
                fields.put("beeName", beeName);
            }
            fields.putAll(extraFields);

            // key 包含 pid，多实例互不覆盖
            String key = KEY_PREFIX + beeType + ":" + selfPid;
            redisTemplate.opsForHash().putAll(key, fields);
            redisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 心跳失败静默处理，不影响主业务
        }
    }
}
