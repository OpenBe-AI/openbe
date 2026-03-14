package com.openbe.queen.stinger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import java.io.ByteArrayOutputStream;

/**
 * StingerController — Stinger 脚本执行接口。
 *
 * POST /api/stingers/{name}/execute
 *   Body: { "approved": true/false, "hiveId": "...", "args": ["arg1", "arg2"] }
 *
 * 安全门协议：
 *  1. 前端首次调用时 approved=false。
 *  2. 后端若发现是危险 Stinger（isDangerous=true），返回 requiresApproval=true。
 *  3. 前端弹出 Security Gate 让用户确认。
 *  4. 用户点击"授权"后，前端重新调用 approved=true。
 *  5. 后端执行并返回结果。
 */
@RestController
@RequestMapping("/api/stingers")
@CrossOrigin(origins = "*")
public class StingerController {

    private static final Path STINGERS_DIR = StingerLibrary.getStingersDir();
    private static final int  TIMEOUT_SECONDS = 30;
    private static final String STINGER_PREFIX = "openbe:stingers:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    public StingerController(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper  = objectMapper;
    }

    // ── 执行 Stinger ──────────────────────────────────────

    @PostMapping("/{name}/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body) {

        // 安全文件名
        String safeName = name.replaceAll("[^A-Za-z0-9._\\-]", "");
        Path script = STINGERS_DIR.resolve(safeName);

        if (!Files.exists(script)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Stinger 不存在: " + safeName));
        }

        boolean dangerous = StingerLibrary.isDangerous(safeName);
        boolean approved  = body != null && Boolean.TRUE.equals(body.get("approved"));

        // 安全门：危险操作未授权则要求确认
        if (dangerous && !approved) {
            return ResponseEntity.ok(Map.of(
                "requiresApproval", true,
                "dangerous",        true,
                "dangerMessage",    StingerLibrary.dangerMessage(safeName)
            ));
        }

        // 构建命令
        List<String> cmd = buildCommand(script, body);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(STINGERS_DIR.toFile())
                .redirectErrorStream(true);

            Process proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append("\n");
                    if (out.length() > 8192) break; // 限制输出大小
                }
            }

            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int exitCode = finished ? proc.exitValue() : -1;
            if (!finished) proc.destroyForcibly();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("stinger",  safeName);
            resp.put("exitCode", exitCode);
            resp.put("output",   out.toString().trim());
            resp.put("success",  exitCode == 0);
            if (!finished) resp.put("timeout", true);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 读取蜂刺代码 ──────────────────────────────────────

    @GetMapping("/{name}/code")
    public ResponseEntity<Map<String, Object>> getStingerCode(@PathVariable String name) {
        String safeName = name.replaceAll("[^A-Za-z0-9._\\-]", "");
        Path script = STINGERS_DIR.resolve(safeName);
        if (!Files.exists(script)) return ResponseEntity.notFound().build();
        try {
            String code = Files.readString(script);
            Path metaPath = STINGERS_DIR.resolve(safeName + ".meta.json");
            Map<String, Object> meta = new LinkedHashMap<>();
            if (Files.exists(metaPath)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = objectMapper.readValue(metaPath.toFile(), Map.class);
                    meta.putAll(m);
                } catch (Exception ignored) {}
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("name", safeName);
            resp.put("code", code);
            resp.put("meta", meta);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 更新蜂刺（保存代码 + 元数据）───────────────────────

    @PutMapping("/{name}")
    public ResponseEntity<Map<String, Object>> updateStinger(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        String safeName = name.replaceAll("[^A-Za-z0-9._\\-]", "");
        Path script = STINGERS_DIR.resolve(safeName);
        try {
            String code = String.valueOf(body.getOrDefault("code", "")).trim();
            Files.writeString(script, code,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            if (safeName.endsWith(".sh")) script.toFile().setExecutable(true);

            // 更新 meta sidecar
            Path metaPath = STINGERS_DIR.resolve(safeName + ".meta.json");
            Map<String, Object> meta = new LinkedHashMap<>();
            if (Files.exists(metaPath)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = objectMapper.readValue(metaPath.toFile(), Map.class);
                    meta.putAll(m);
                } catch (Exception ignored) {}
            }
            if (body.containsKey("description")) meta.put("description", body.get("description"));
            if (body.containsKey("icon"))        meta.put("icon",        body.get("icon"));
            if (body.containsKey("rarity"))      meta.put("rarity",      body.get("rarity"));
            Files.writeString(metaPath,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return ResponseEntity.ok(Map.of("status", "updated", "name", safeName));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 锻造自定义蜂刺 ────────────────────────────────────

    @PostMapping("/forge")
    public ResponseEntity<Map<String, Object>> forge(
            @RequestBody Map<String, Object> body) {

        String rawName    = String.valueOf(body.getOrDefault("name", "custom")).trim();
        String description= String.valueOf(body.getOrDefault("description", "自定义蜂刺")).trim();
        String code       = String.valueOf(body.getOrDefault("code", "#!/bin/bash\necho 'Hello from Stinger!'")).trim();
        String icon       = String.valueOf(body.getOrDefault("icon", "⚡")).trim();
        String rarity     = String.valueOf(body.getOrDefault("rarity", "blue")).trim();

        // 安全文件名：仅保留字母数字下划线，追加 .sh
        String safeName = rawName.replaceAll("[^A-Za-z0-9_\\-]", "_")
                                 .replaceAll("_+", "_")
                                 .toLowerCase();
        if (safeName.isBlank()) safeName = "custom_stinger";
        if (!safeName.endsWith(".sh")) safeName = safeName + ".sh";

        try {
            Files.createDirectories(STINGERS_DIR);

            // 写入脚本
            Path scriptPath = STINGERS_DIR.resolve(safeName);
            String header = "#!/bin/bash\n# 蜂刺: " + rawName + "\n# 描述: " + description + "\n\n";
            String fullCode = code.startsWith("#!") ? code : header + code;
            Files.writeString(scriptPath, fullCode,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            scriptPath.toFile().setExecutable(true);

            // 写入 sidecar 元数据
            Path metaPath = STINGERS_DIR.resolve(safeName + ".meta.json");
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name",        rawName);
            meta.put("description", description);
            meta.put("icon",        icon);
            meta.put("rarity",      rarity);
            meta.put("dangerous",   false);
            meta.put("forged",      true);
            Files.writeString(metaPath,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status",  "forged");
            resp.put("name",    safeName);
            resp.put("rawName", rawName);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 删除自定义蜂刺 ────────────────────────────────────

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, Object>> deleteStinger(@PathVariable String name) {
        String safeName = name.replaceAll("[^A-Za-z0-9._\\-]", "");
        try {
            Path script = STINGERS_DIR.resolve(safeName);
            Path meta   = STINGERS_DIR.resolve(safeName + ".meta.json");
            boolean deleted = Files.deleteIfExists(script);
            Files.deleteIfExists(meta);
            if (!deleted) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("status", "deleted", "name", safeName));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 导入蜂刺 ──────────────────────────────────────────

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importStinger(
            @RequestParam("file") MultipartFile file) {

        String origName = file.getOriginalFilename();
        if (origName == null || origName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件名为空"));
        }
        String safeName = origName.replaceAll("[^A-Za-z0-9._\\-]", "");
        if (!safeName.endsWith(".sh") && !safeName.endsWith(".scpt")
                && !safeName.endsWith(".js") && !safeName.endsWith(".ts")) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .sh .scpt .js .ts 格式"));
        }

        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }

        boolean dangerous = hasDangerousPatterns(new String(bytes));

        try {
            Files.createDirectories(STINGERS_DIR);
            Path dest = STINGERS_DIR.resolve(safeName);
            Files.write(dest, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            if (safeName.endsWith(".sh")) dest.toFile().setExecutable(true);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status",    "imported");
        resp.put("name",      safeName);
        resp.put("dangerous", dangerous);
        if (dangerous) resp.put("warning", "检测到高危 API 调用，请谨慎使用");
        return ResponseEntity.ok(resp);
    }

    // ── 导出蜂刺技能包 ────────────────────────────────────

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportStingers(
            @RequestParam(required = false) String beeType) {

        // 获取该蜂蛹已装载的蜂刺列表
        List<String> toExport = new ArrayList<>();
        if (beeType != null && !beeType.isBlank()) {
            String raw = redisTemplate.opsForValue().get(STINGER_PREFIX + beeType.toUpperCase());
            if (raw != null) {
                try { toExport = Arrays.asList(objectMapper.readValue(raw, String[].class)); }
                catch (Exception ignored) {}
            }
        }
        // 若无指定或空列表，导出库中所有蜂刺
        if (toExport.isEmpty()) {
            try (var stream = Files.list(STINGERS_DIR)) {
                stream.filter(p -> !Files.isDirectory(p))
                      .map(p -> p.getFileName().toString())
                      .forEach(toExport::add);
            } catch (IOException ignored) {}
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 打包脚本文件
            for (String name : toExport) {
                Path f = STINGERS_DIR.resolve(name);
                if (!Files.exists(f)) continue;
                zos.putNextEntry(new ZipEntry("stingers/" + name));
                zos.write(Files.readAllBytes(f));
                zos.closeEntry();
            }
            // 写入 Manifest
            zos.putNextEntry(new ZipEntry("manifest.json"));
            List<Map<String, Object>> manifest = StingerLibrary.enrichFileList(toExport);
            zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zos.closeEntry();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }

        String filename = (beeType != null && !beeType.isBlank())
            ? "openbe-stingers-" + beeType + ".zip"
            : "openbe-stingers-all.zip";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(baos.toByteArray());
    }

    // ── 工具方法 ──────────────────────────────────────────

    private boolean hasDangerousPatterns(String content) {
        String[] patterns = { "sudo", "rm -rf", "chmod 777", "chown root",
            "eval(", "exec(", "do shell script", "/private/var", "launchd" };
        String lower = content.toLowerCase();
        for (String p : patterns) if (lower.contains(p)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<String> buildCommand(Path script, Map<String, Object> body) {
        String name = script.getFileName().toString();
        List<String> args = new ArrayList<>();

        if (body != null && body.get("args") instanceof List<?> rawArgs) {
            rawArgs.forEach(a -> args.add(a.toString()));
        }

        List<String> cmd = new ArrayList<>();
        if (name.endsWith(".sh")) {
            cmd.add("/bin/bash");
            cmd.add(script.toString());
        } else if (name.endsWith(".scpt")) {
            cmd.add("osascript");
            cmd.add(script.toString());
        } else {
            cmd.add(script.toString());
        }
        cmd.addAll(args);
        return cmd;
    }
}
