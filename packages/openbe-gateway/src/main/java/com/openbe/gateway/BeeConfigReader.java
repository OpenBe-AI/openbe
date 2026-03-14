package com.openbe.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 蜜蜂运行时配置读取器。
 *
 * Dashboard 的「蜜蜂参数热调」通过 PUT /api/bees/{type}/config 将新配置写入
 * Redis Hash openbe:config:{beeType}，本组件在每次 Ollama 调用前读取，
 * 实现模型名称、Temperature、System Prompt 的即时生效（零重启）。
 */
@Component
public class BeeConfigReader {

    private static final String CONFIG_PREFIX = "openbe:config:";

    @Value("${openbe.bee.type:UNKNOWN}")
    private String beeType;

    private final StringRedisTemplate redisTemplate;

    public BeeConfigReader(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 读取动态模型名，未配置时返回 null（调用方使用自己的 defaultModel）*/
    public String getModel() {
        return get("model");
    }

    /** 读取 Temperature，未配置时返回 null */
    public Double getTemperature() {
        String v = get("temperature");
        if (v == null) return null;
        try { return Double.parseDouble(v); } catch (Exception e) { return null; }
    }

    /** 读取 System Prompt，未配置时返回 null */
    public String getSystemPrompt() {
        return get("systemPrompt");
    }

    /** 读取指定蜂类型的某个配置项（跨蜂读取，如 chatSource=QUEEN 时读 QUEEN 的配置） */
    public String getForType(String targetBeeType, String field) {
        try {
            Object v = redisTemplate.opsForHash().get(CONFIG_PREFIX + targetBeeType.toUpperCase(), field);
            if (v == null) return null;
            String s = v.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    private String get(String field) {
        try {
            Object v = redisTemplate.opsForHash().get(CONFIG_PREFIX + beeType, field);
            if (v == null) return null;
            String s = v.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }
}
