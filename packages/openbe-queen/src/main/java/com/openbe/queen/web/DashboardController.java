package com.openbe.queen.web;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.openbe.queen.hive.BeeRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Dashboard REST API
 *  GET /api/bees  — 返回所有蜜蜂的实时健康状态
 *  GET /api/soul  — 返回 soul.md 的内容
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DashboardController {

    private static final String HEALTH_PREFIX = "openbe:health:";
    private static final String BEENAMES_KEY  = "openbe:beenames";
    private static final Path   SOUL_FILE     =
        Paths.get(System.getProperty("user.home"), ".openbe", "workspace", "soul.md");

    private final StringRedisTemplate redisTemplate;
    private final BeeRegistry         beeRegistry;

    public DashboardController(StringRedisTemplate redisTemplate, BeeRegistry beeRegistry) {
        this.redisTemplate = redisTemplate;
        this.beeRegistry   = beeRegistry;
    }

    @GetMapping("/bees")
    public ResponseEntity<List<Map<String, Object>>> getBees() {
        // 扫描所有 openbe:health:* 键（格式：openbe:health:{TYPE}:{pid}）
        Set<String> keys = redisTemplate.keys(HEALTH_PREFIX + "*");
        List<Map<String, Object>> bees = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
                if (raw.isEmpty()) continue;
                Map<String, Object> bee = new LinkedHashMap<>();
                raw.forEach((k, v) -> bee.put(k.toString(), v.toString()));
                // 确保 beeType 字段存在
                if (!bee.containsKey("beeType")) {
                    String[] parts = key.replace(HEALTH_PREFIX, "").split(":");
                    bee.put("beeType", parts[0]);
                }
                bees.add(bee);
            }
        }

        // 合并自定义名字（由 Queen 在 spawn 时存入 openbe:beenames）
        Map<Object, Object> beeNames    = redisTemplate.opsForHash().entries(BEENAMES_KEY);
        Map<Object, Object> beeSpecies  = redisTemplate.opsForHash().entries("openbe:beespecies");
        Map<Object, Object> beeIds      = redisTemplate.opsForHash().entries("openbe:beeid");
        for (Map<String, Object> bee : bees) {
            String type = bee.getOrDefault("beeType", "").toString().toUpperCase();
            String pid  = bee.getOrDefault("pid", "").toString();
            String key  = type + ":" + pid;
            Object nameObj    = beeNames.get(key);
            Object speciesObj = beeSpecies.get(key);
            Object beeIdObj   = beeIds.get(key);
            if (nameObj    != null) bee.put("beeName",       nameObj.toString());
            if (speciesObj != null) bee.put("displaySpecies", speciesObj.toString());
            if (beeIdObj   != null) bee.put("beeId",         beeIdObj.toString());
        }

        // 构建 beeId → hiveId 映射（来自持久化注册表），补全在线蜂缺失的 hiveId
        Map<String, String> beeIdToHiveId = new HashMap<>();
        for (BeeRegistry.BeeEntry entry : beeRegistry.getAll()) {
            if (entry.hiveId != null && !entry.hiveId.isBlank()) {
                beeIdToHiveId.put(entry.beeId, entry.hiveId);
            }
        }
        for (Map<String, Object> bee : bees) {
            if (bee.containsKey("hiveId")) continue; // 已有 hiveId（离线蜂）
            Object beeIdObj = bee.get("beeId");
            if (beeIdObj == null) continue;
            String hiveId = beeIdToHiveId.get(beeIdObj.toString());
            if (hiveId != null) bee.put("hiveId", hiveId);
        }

        // 从持久化注册表中补充离线蜜蜂（进程已停止但信息保留）
        Set<String> activeBeeIds = new HashSet<>();
        bees.forEach(b -> { Object id = b.get("beeId"); if (id != null) activeBeeIds.add(id.toString()); });

        for (BeeRegistry.BeeEntry entry : beeRegistry.getAll()) {
            if (activeBeeIds.contains(entry.beeId)) continue; // 已在线，跳过
            Map<String, Object> offline = new LinkedHashMap<>();
            offline.put("beeType",       entry.healthType != null ? entry.healthType.toUpperCase() : "WORKER");
            offline.put("displaySpecies", entry.species != null ? entry.species.toUpperCase() : "WORKER");
            offline.put("beeName",       entry.name != null ? entry.name : "");
            offline.put("beeId",         entry.beeId);
            offline.put("hiveId",        entry.hiveId != null ? entry.hiveId : "");
            offline.put("status",        "OFFLINE");
            offline.put("memoryMB",      "0");
            offline.put("heartbeat",     "0");
            offline.put("pid",           "");
            bees.add(offline);
        }

        // 按 beeType + pid 排序，保证展示顺序稳定
        bees.sort(Comparator.comparing(b -> b.getOrDefault("beeType", "").toString()
            + b.getOrDefault("pid", "").toString()));

        return ResponseEntity.ok(bees);
    }

    @GetMapping("/soul")
    public ResponseEntity<Map<String, Object>> getSoul() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (Files.exists(SOUL_FILE)) {
                result.put("content", Files.readString(SOUL_FILE));
                result.put("exists",  true);
            } else {
                result.put("content", "# soul.md 尚未生成\n护士蜂完成第一次酿蜜后将自动出现。");
                result.put("exists",  false);
            }
        } catch (IOException e) {
            result.put("content", "读取失败: " + e.getMessage());
            result.put("exists",  false);
        }
        return ResponseEntity.ok(result);
    }
}
