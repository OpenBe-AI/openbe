package com.openbe.queen.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 即刻 Jike 桥接控制器（预留位）。
 * 提供 WebSocket 配对入口，未来对接 iPhone Jike App。
 */
@RestController
@RequestMapping("/jike")
@CrossOrigin(origins = "*")
public class JikeBridgeController {

    private volatile String currentPairCode = generateCode();
    private volatile boolean paired = false;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "paired", paired,
            "wsEndpoint", "ws://localhost:8080/api/logs/stream",
            "pairCode", currentPairCode
        ));
    }

    @PostMapping("/pair")
    public ResponseEntity<Map<String, Object>> pair(@RequestBody Map<String, String> body) {
        String code = body.getOrDefault("code", "");
        if (currentPairCode.equals(code)) {
            paired = true;
            return ResponseEntity.ok(Map.of("status", "paired", "message", "即刻配对成功"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "配对码不匹配"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        currentPairCode = generateCode();
        paired = false;
        return ResponseEntity.ok(Map.of("pairCode", currentPairCode));
    }

    private static String generateCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
