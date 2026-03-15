package com.openbe.queen.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 版本管理 API
 *  GET /api/version/current — 返回当前运行版本
 *  GET /api/version/latest  — 查询 GitHub 最新 Release，与当前版本对比
 */
@RestController
@RequestMapping("/api/version")
@CrossOrigin(origins = "*")
public class VersionController {

    /** 从 JAR 内 pom.properties 读取版本（编译时写入，准确反映构建版本） */
    private static final String CURRENT_VERSION = readPomVersion();

    private static final String GITHUB_API =
        "https://api.github.com/repos/OpenBe-AI/openbe/releases/latest";

    /** 缓存：避免每次前端轮询都打 GitHub API（缓存 1 小时）*/
    private volatile Map<String, Object> cachedLatest = null;
    private volatile Instant cacheTime = Instant.EPOCH;
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    // ── 当前版本 ────────────────────────────────────────────────────────────
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> current() {
        return ResponseEntity.ok(Map.of("version", CURRENT_VERSION));
    }

    // ── 最新版本（GitHub Release） ─────────────────────────────────────────
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latest() {
        if (cachedLatest != null && Duration.between(cacheTime, Instant.now()).compareTo(CACHE_TTL) < 0) {
            return ResponseEntity.ok(cachedLatest);
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenBe/" + CURRENT_VERSION)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return ResponseEntity.status(502).body(Map.of("error", "GitHub API returned " + resp.statusCode()));
            }

            String body = resp.body();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("version",  extractJsonString(body, "tag_name").replaceFirst("^v", ""));
            result.put("notes",    extractJsonString(body, "body"));
            result.put("url",      extractJsonString(body, "html_url"));
            result.put("checkedAt", Instant.now().toString());

            cachedLatest = result;
            cacheTime = Instant.now();
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(504).body(Map.of("error", "无法连接 GitHub: " + e.getMessage()));
        }
    }

    // ── 工具：从 JAR pom.properties 读版本 ─────────────────────────────────
    private static String readPomVersion() {
        try (InputStream is = VersionController.class.getResourceAsStream(
                "/META-INF/maven/com.openbe/openbe-queen/pom.properties")) {
            if (is == null) return "unknown";
            Properties p = new Properties();
            p.load(is);
            return p.getProperty("version", "unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 极简 JSON 字符串字段提取（避免引入额外 JSON 依赖） */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return "";
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i)); continue;
            }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }
}
