package com.openbe.queen.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbe.queen.hive.BeeRegistry;
import com.openbe.queen.hive.BeeWorkspace;
import com.openbe.queen.stinger.StingerLibrary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * 蜂巢管理 API：
 *  - 生命周期：spawn / terminate
 *  - 技能插槽：stingers list / assign
 *  - 参数热调：live config read / write
 *  - API Key 管理（供 Jike 等外部接入）
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ManagementController {

    private static final Path   OPENBE_HOME    = Paths.get(System.getProperty("user.home"), ".openbe");
    private static final String CONFIG_PREFIX  = "openbe:config:";
    private static final String STINGER_PREFIX = "openbe:stingers:";
    private static final String APIKEYS_KEY    = "openbe:apikeys";
    private static final String BEENAMES_KEY   = "openbe:beenames";

    private final StringRedisTemplate redisTemplate;
    private final BeeWorkspace        beeWorkspace;
    private final BeeRegistry         beeRegistry;
    private final ObjectMapper        objectMapper = new ObjectMapper();

    public ManagementController(StringRedisTemplate redisTemplate,
                                BeeWorkspace beeWorkspace,
                                BeeRegistry beeRegistry) {
        this.redisTemplate = redisTemplate;
        this.beeWorkspace  = beeWorkspace;
        this.beeRegistry   = beeRegistry;
    }

    // ── 生命周期 ──────────────────────────────────────────

    /**
     * 孵化系统蜂。
     *
     * 支持物种（species/type）：WORKER / SOLDIER / NURSE / SCOUT / MECHANIC
     * 可选 hiveId：若提供，将在对应蜂巢目录下初始化 11 文件工作区，并向子进程传递环境变量。
     */
    @PostMapping("/bees/spawn")
    public ResponseEntity<Map<String, Object>> spawnBee(@RequestBody Map<String, String> body) {
        // 兼容前端发 "type"、"species" 或 "template"
        String species = body.containsKey("species") ? body.get("species").toUpperCase()
            : body.containsKey("type")     ? body.get("type").toUpperCase()
            : body.getOrDefault("template", "WORKER").toUpperCase();

        String jarName = switch (species) {
            case "NURSE"  -> "nurse-bee.jar";
            default       -> "worker-bee.jar"; // WORKER / SOLDIER / SCOUT / MECHANIC
        };

        Path jarPath = OPENBE_HOME.resolve(jarName);
        if (!Files.exists(jarPath)) {
            return ResponseEntity.badRequest().body(Map.of("error", "jar 不存在: " + jarPath));
        }

        String javaExe = ProcessHandle.current().info().command()
            .orElse(System.getProperty("java.home") + "/bin/java");
        String name    = body.getOrDefault("name", "").trim();
        String hiveId  = body.getOrDefault("hiveId", "").trim();

        // 生成 beeId：{species小写}-{随机8位}
        String beeId   = species.toLowerCase() + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 初始化 hive 工作区（如有 hiveId），传入自定义名字确保 IDENTITY/SOUL 写入正确
        if (!hiveId.isEmpty()) {
            try { beeWorkspace.initWithName(hiveId, beeId, species, name.isEmpty() ? species : name); }
            catch (Exception e) {
                System.err.printf("[ManagementController] 工作区初始化失败: %s%n", e.getMessage());
            }
        }

        String logFile = OPENBE_HOME.resolve(species.toLowerCase() + "-" + beeId + ".log").toString();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                javaExe, "--sun-misc-unsafe-memory-access=allow",
                "-Dopenbe.bee.species=" + species,
                "-Dopenbe.hive.id=" + hiveId,
                "-Dopenbe.bee.id=" + beeId,
                "-jar", jarPath.toString()
            )
                .directory(OPENBE_HOME.toFile())
                .redirectOutput(new File(logFile))
                .redirectErrorStream(true);

            Process process = pb.start();
            long pid = process.pid();

            // worker-bee.jar 始终以 WORKER 类型上报心跳；nurse-bee.jar 以 NURSE 上报
            String healthType = "NURSE".equals(species) ? "NURSE" : "WORKER";

            // 存自定义名字（key 与 DashboardController 查找方式一致）
            if (!name.isEmpty()) {
                redisTemplate.opsForHash().put(BEENAMES_KEY, healthType + ":" + pid, name);
            }

            // 存实际物种映射，供 DashboardController 注入 displaySpecies
            redisTemplate.opsForHash().put("openbe:beespecies", healthType + ":" + pid, species);

            // 存 beeId 映射，供 WillPanel 读取工作区文件
            redisTemplate.opsForHash().put("openbe:beeid", healthType + ":" + pid, beeId);

            // 写入持久化注册表，重启后可恢复
            beeRegistry.register(new BeeRegistry.BeeEntry(beeId, species, healthType, name, hiveId));

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status",  "spawned");
            resp.put("species", species);
            resp.put("beeId",   beeId);
            resp.put("hiveId",  hiveId);
            resp.put("jar",     jarName);
            resp.put("name",    name);
            resp.put("pid",     pid);
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 按 beeId 删除单只蜜蜂：终止进程（若在线）+ 清理 Redis + 注销注册表 */
    @DeleteMapping("/bees/registry/{beeId}")
    public ResponseEntity<Map<String, Object>> removeBeeById(@PathVariable("beeId") String beeId) {
        // 从注册表找到该蜜蜂信息
        BeeRegistry.BeeEntry entry = beeRegistry.getAll().stream()
            .filter(e -> beeId.equals(e.beeId))
            .findFirst().orElse(null);

        // 在 Redis openbe:beeid 反查 TYPE:PID
        Map<Object, Object> beeIdMap = redisTemplate.opsForHash().entries("openbe:beeid");
        for (Map.Entry<Object, Object> e : beeIdMap.entrySet()) {
            if (!beeId.equals(e.getValue().toString())) continue;
            String typePid = e.getKey().toString();
            String[] parts = typePid.split(":", 2);
            if (parts.length >= 2) {
                try {
                    long pid = Long.parseLong(parts[1]);
                    ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
                } catch (NumberFormatException ignored) {}
            }
            redisTemplate.delete("openbe:health:" + typePid);
            redisTemplate.opsForHash().delete(BEENAMES_KEY,        typePid);
            redisTemplate.opsForHash().delete("openbe:beespecies", typePid);
            redisTemplate.opsForHash().delete("openbe:beeid",      typePid);
        }

        beeRegistry.unregisterById(beeId);
        return ResponseEntity.ok(Map.of("status", "removed", "beeId", beeId));
    }

    @DeleteMapping("/bees/{type}")
    public ResponseEntity<Map<String, Object>> terminateBee(@PathVariable("type") String type) {
        // 扫描 openbe:health:{TYPE}:* 找到所有同类实例，全部终止
        String pattern = "openbe:health:" + type.toUpperCase() + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "already_offline", "pids", List.of()));
        }
        List<Long> terminated = new ArrayList<>();
        for (String key : keys) {
            Object pidObj = redisTemplate.opsForHash().get(key, "pid");
            if (pidObj == null) continue;
            try {
                long pid = Long.parseLong(pidObj.toString());
                ProcessHandle.of(pid).ifPresent(ph -> { ph.destroy(); terminated.add(pid); });
                redisTemplate.delete(key);
                redisTemplate.opsForHash().delete(BEENAMES_KEY, type.toUpperCase() + ":" + pid);
            } catch (Exception ignored) {}
        }
        // 从持久化注册表中清除
        beeRegistry.unregisterByHealthType(type);
        return ResponseEntity.ok(Map.of("status", "terminated", "pids", terminated));
    }

    // ── 技能插槽 ──────────────────────────────────────────

    @GetMapping("/stingers")
    public ResponseEntity<List<Map<String, Object>>> listStingers() {
        Path dir = StingerLibrary.getStingersDir();
        List<String> files = new ArrayList<>();
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> !Files.isDirectory(p))
                      .map(p -> p.getFileName().toString())
                      .sorted()
                      .forEach(files::add);
            } catch (IOException ignored) {}
        }
        return ResponseEntity.ok(StingerLibrary.enrichFileList(files));
    }

    @GetMapping("/bees/{type}/stingers")
    public ResponseEntity<List<String>> getBeeStingers(@PathVariable("type") String type) {
        String raw = redisTemplate.opsForValue().get(STINGER_PREFIX + type.toUpperCase());
        if (raw == null) return ResponseEntity.ok(List.of());
        try {
            String[] arr = objectMapper.readValue(raw, String[].class);
            return ResponseEntity.ok(Arrays.asList(arr));
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @PutMapping("/bees/{type}/stingers")
    public ResponseEntity<Map<String, Object>> saveBeeStingers(
            @PathVariable("type") String type,
            @RequestBody List<String> stingers) {
        try {
            String json = objectMapper.writeValueAsString(stingers);
            redisTemplate.opsForValue().set(STINGER_PREFIX + type.toUpperCase(), json);
            // 克隆蜂刺脚本到蜜蜂私有 skills/ 目录
            int provisioned = provisionSkillsForBeeType(type, stingers);
            return ResponseEntity.ok(Map.of("status", "saved", "count", stingers.size(),
                "provisioned", provisioned));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Clone & Bind：将蜂刺脚本复制到该类型所有蜜蜂的 skills/ 私有目录。
     * 蜜蜂执行时优先使用私有副本，互不影响。
     */
    private int provisionSkillsForBeeType(String type, List<String> stingers) {
        String healthType = "NURSE".equalsIgnoreCase(type) ? "NURSE" : "WORKER";
        int count = 0;
        for (BeeRegistry.BeeEntry entry : beeRegistry.getAll()) {
            if (!healthType.equalsIgnoreCase(entry.healthType)) continue;
            if (entry.hiveId == null || entry.hiveId.isBlank()) continue;
            Path skillsDir = OPENBE_HOME.resolve("hives")
                .resolve(entry.hiveId).resolve(entry.beeId).resolve("skills");
            try {
                Files.createDirectories(skillsDir);
                for (String stingerName : stingers) {
                    Path src = StingerLibrary.getStingersDir().resolve(stingerName);
                    if (!Files.exists(src)) continue;
                    Path dst = skillsDir.resolve(stingerName);
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    if (stingerName.endsWith(".sh")) {
                        try {
                            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(dst);
                            perms.add(PosixFilePermission.OWNER_EXECUTE);
                            Files.setPosixFilePermissions(dst, perms);
                        } catch (Exception ignored) { dst.toFile().setExecutable(true); }
                    }
                }
                System.out.printf("[ManagementController] ✅ 蜂刺植入 %s/%s: %s%n",
                    entry.hiveId, entry.beeId, stingers);
                count++;
            } catch (Exception e) {
                System.err.printf("[ManagementController] 植入失败 %s: %s%n", entry.beeId, e.getMessage());
            }
        }
        return count;
    }

    // ── 参数热调 ──────────────────────────────────────────

    @GetMapping("/bees/{type}/config")
    public ResponseEntity<Map<Object, Object>> getBeeConfig(@PathVariable("type") String type) {
        Map<Object, Object> cfg = redisTemplate.opsForHash().entries(CONFIG_PREFIX + type.toUpperCase());
        return ResponseEntity.ok(cfg);
    }

    @PutMapping("/bees/{type}/config")
    public ResponseEntity<Map<String, Object>> saveBeeConfig(
            @PathVariable("type") String type,
            @RequestBody Map<String, String> config) {
        String key = CONFIG_PREFIX + type.toUpperCase();
        config.forEach((k, v) -> {
            if (v == null || v.isBlank()) redisTemplate.opsForHash().delete(key, k);
            else redisTemplate.opsForHash().put(key, k, v);
        });
        return ResponseEntity.ok(Map.of("status", "saved", "beeType", type.toUpperCase()));
    }

    // ── API Key 管理 ──────────────────────────────────────

    @GetMapping("/settings/apikeys")
    public ResponseEntity<List<Map<String, Object>>> listApiKeys() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(APIKEYS_KEY);
        List<Map<String, Object>> keys = new ArrayList<>();
        raw.forEach((id, json) -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = objectMapper.readValue(json.toString(), Map.class);
                entry.put("id", id.toString());
                keys.add(entry);
            } catch (Exception ignored) {}
        });
        keys.sort(Comparator.comparingLong(m -> -((Number) m.getOrDefault("createdAt", 0L)).longValue()));
        return ResponseEntity.ok(keys);
    }

    @PostMapping("/settings/apikeys")
    public ResponseEntity<Map<String, Object>> createApiKey(@RequestBody Map<String, String> body) {
        String id  = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String key = "ob_" + UUID.randomUUID().toString().replace("-", "");
        String name = body.getOrDefault("name", "Unnamed Key");
        Map<String, Object> entry = Map.of("name", name, "key", key, "createdAt", System.currentTimeMillis());
        try {
            redisTemplate.opsForHash().put(APIKEYS_KEY, id, objectMapper.writeValueAsString(entry));
            Map<String, Object> resp = new LinkedHashMap<>(entry);
            resp.put("id", id);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/settings/apikeys/{id}")
    public ResponseEntity<Map<String, Object>> deleteApiKey(@PathVariable("id") String id) {
        redisTemplate.opsForHash().delete(APIKEYS_KEY, id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    // ── 蜜蜂 API 模型配置（保存到本地 JSON 文件）─────────────

    private Path apikeyConfigPath(String type) {
        Path dir = OPENBE_HOME.resolve("config");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir.resolve(type.toLowerCase() + "-apikey.json");
    }

    @GetMapping("/bees/{type}/apikey")
    public ResponseEntity<Map<String, Object>> getBeeApiKey(@PathVariable("type") String type) {
        Path path = apikeyConfigPath(type);
        if (!Files.exists(path)) {
            return ResponseEntity.ok(Map.of("provider", "ollama", "model", "", "apiKey", "", "baseUrl", ""));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = objectMapper.readValue(path.toFile(), Map.class);
            // 返回时隐藏 key 尾部（保留前4位用于展示）
            String raw = (String) cfg.getOrDefault("apiKey", "");
            if (raw.length() > 4) {
                cfg = new LinkedHashMap<>(cfg);
                cfg.put("apiKeyMasked", raw.substring(0, 4) + "••••");
            }
            return ResponseEntity.ok(cfg);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/bees/{type}/apikey")
    public ResponseEntity<Map<String, Object>> saveBeeApiKey(
            @PathVariable("type") String type,
            @RequestBody Map<String, String> body) {
        Path path = apikeyConfigPath(type);
        try {
            // 如果 apiKey 为空且文件已存在，保留原有 key
            if ((body.get("apiKey") == null || body.get("apiKey").isBlank()) && Files.exists(path)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = objectMapper.readValue(path.toFile(), Map.class);
                Map<String, Object> merged = new LinkedHashMap<>(existing);
                body.forEach((k, v) -> { if (v != null && !v.isBlank()) merged.put(k, v); });
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), merged);
            } else {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), body);
            }
            System.out.printf("[QUEEN] 已保存 %s 的 API 配置到 %s%n", type.toUpperCase(), path);
            return ResponseEntity.ok(Map.of("status", "saved", "path", path.toString()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 净化协议状态 ──────────────────────────────────────

    /**
     * 读取 purify.py 写入的净化状态文件。
     * 若尚未运行过净化脚本，返回 pending 状态。
     */
    @GetMapping("/system/purify-status")
    public ResponseEntity<Map<String, Object>> getPurifyStatus() {
        Path path = OPENBE_HOME.resolve("purify-status.json");
        if (!Files.exists(path)) {
            return ResponseEntity.ok(Map.of("status", "pending", "purifiedAt", "", "beesProcessed", 0));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(path.toFile(), Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "pending", "purifiedAt", "", "beesProcessed", 0));
        }
    }

    // ── 蜂王安全配置 ──────────────────────────────────────

    @GetMapping("/queen/security")
    public ResponseEntity<Map<String, Object>> getQueenSecurity() {
        Path path = OPENBE_HOME.resolve("queen-security.json");
        if (!Files.exists(path)) return ResponseEntity.ok(Map.of("allowExternal", false));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = objectMapper.readValue(path.toFile(), Map.class);
            return ResponseEntity.ok(cfg);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("allowExternal", false));
        }
    }

    @PostMapping("/queen/security")
    public ResponseEntity<Map<String, Object>> saveQueenSecurity(
            @RequestBody Map<String, Object> body) {
        Path path = OPENBE_HOME.resolve("queen-security.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), body);
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
