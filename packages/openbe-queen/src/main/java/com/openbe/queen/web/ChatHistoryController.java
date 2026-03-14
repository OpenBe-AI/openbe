package com.openbe.queen.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 对话历史持久化 API
 *
 * 蜂巢蜜蜂：GET/POST /api/hives/{hiveId}/messages
 *           存储路径：~/.openbe/hives/{hiveId}/messages.json
 *
 * 系统蜜蜂：GET/POST /api/bees/{type}/messages
 *           存储路径：~/.openbe/system/{type}/messages.json
 */
@RestController
@CrossOrigin(origins = "*")
public class ChatHistoryController {

    private static final Path OPENBE_HOME =
        Paths.get(System.getProperty("user.home"), ".openbe");

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 蜂巢消息 ───────────────────────────────────────────

    @GetMapping("/api/hives/{hiveId}/messages")
    public ResponseEntity<List<Object>> loadHiveMessages(@PathVariable String hiveId) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve("messages.json");
        return ResponseEntity.ok(readMessages(file));
    }

    @PostMapping("/api/hives/{hiveId}/messages")
    public ResponseEntity<Map<String, Object>> saveHiveMessages(
            @PathVariable String hiveId,
            @RequestBody List<Object> messages) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve("messages.json");
        writeMessages(file, messages);
        return ResponseEntity.ok(Map.of("status", "saved", "count", messages.size()));
    }

    // ── 蜂巢蜂王对话历史 ──────────────────────────────────

    @GetMapping("/api/hives/{hiveId}/queen-messages")
    public ResponseEntity<List<Object>> loadHiveQueenMessages(@PathVariable String hiveId) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve("queen-messages.json");
        return ResponseEntity.ok(readMessages(file));
    }

    @PostMapping("/api/hives/{hiveId}/queen-messages")
    public ResponseEntity<Map<String, Object>> saveHiveQueenMessages(
            @PathVariable String hiveId,
            @RequestBody List<Object> messages) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve("queen-messages.json");
        writeMessages(file, messages);
        return ResponseEntity.ok(Map.of("status", "saved", "count", messages.size()));
    }

    // ── 蜂巢笔记（蜜罐）───────────────────────────────────

    @GetMapping("/api/hives/{hiveId}/notes")
    public ResponseEntity<Map<String, Object>> loadHiveNotes(@PathVariable String hiveId) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve("notes.md");
        if (!Files.exists(file)) return ResponseEntity.ok(Map.of("content", ""));
        try {
            return ResponseEntity.ok(Map.of("content", Files.readString(file)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("content", ""));
        }
    }

    @PostMapping("/api/hives/{hiveId}/notes")
    public ResponseEntity<Map<String, Object>> saveHiveNotes(
            @PathVariable String hiveId,
            @RequestBody Map<String, String> body) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve("notes.md");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, body.getOrDefault("content", ""));
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 蜂巢人设（soul.md）────────────────────────────────

    @GetMapping("/api/hives/{hiveId}/soul")
    public ResponseEntity<Map<String, Object>> loadHiveSoul(@PathVariable String hiveId) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve("soul.md");
        if (!Files.exists(file)) return ResponseEntity.ok(Map.of("content", ""));
        try {
            return ResponseEntity.ok(Map.of("content", Files.readString(file)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("content", ""));
        }
    }

    @PostMapping("/api/hives/{hiveId}/soul")
    public ResponseEntity<Map<String, Object>> saveHiveSoul(
            @PathVariable String hiveId,
            @RequestBody Map<String, String> body) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve("soul.md");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, body.getOrDefault("content", ""));
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 蜂巢工作蜂消息（按 beeId 隔离）────────────────────

    @GetMapping("/api/hives/{hiveId}/bees/{beeId}/messages")
    public ResponseEntity<List<Object>> loadHiveBeeMessages(
            @PathVariable String hiveId, @PathVariable String beeId) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve(beeId).resolve("messages.json");
        return ResponseEntity.ok(readMessages(file));
    }

    @PostMapping("/api/hives/{hiveId}/bees/{beeId}/messages")
    public ResponseEntity<Map<String, Object>> saveHiveBeeMessages(
            @PathVariable String hiveId, @PathVariable String beeId,
            @RequestBody List<Object> messages) {
        Path file = OPENBE_HOME.resolve("hives").resolve(hiveId).resolve(beeId).resolve("messages.json");
        writeMessages(file, messages);
        return ResponseEntity.ok(Map.of("status", "saved", "count", messages.size()));
    }

    // ── 系统蜜蜂消息 ───────────────────────────────────────

    @GetMapping("/api/bees/{type}/messages")
    public ResponseEntity<List<Object>> loadBeeMessages(@PathVariable String type) {
        Path file = sysMsgPath(type);
        return ResponseEntity.ok(readMessages(file));
    }

    @PostMapping("/api/bees/{type}/messages")
    public ResponseEntity<Map<String, Object>> saveBeeMessages(
            @PathVariable String type,
            @RequestBody List<Object> messages) {
        Path file = sysMsgPath(type);
        writeMessages(file, messages);
        return ResponseEntity.ok(Map.of("status", "saved", "count", messages.size()));
    }

    // ── 工具方法 ───────────────────────────────────────────

    private Path sysMsgPath(String type) {
        String clean = type.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        return OPENBE_HOME.resolve("system").resolve(clean).resolve("messages.json");
    }

    @SuppressWarnings("unchecked")
    private List<Object> readMessages(Path file) {
        if (!Files.exists(file)) return List.of();
        try {
            return objectMapper.readValue(file.toFile(), List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void writeMessages(Path file, List<Object> messages) {
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), messages);
        } catch (Exception e) {
            System.err.printf("[ChatHistory] 写入失败 %s: %s%n", file, e.getMessage());
        }
    }
}
