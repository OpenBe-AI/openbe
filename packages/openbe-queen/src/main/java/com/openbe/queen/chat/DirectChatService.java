package com.openbe.queen.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openbe.queen.stinger.StingerLibrary;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蜂巢对话直连服务 — 不经过 Redis/WorkerBee，直接调用本地 Ollama 或外部 API。
 */
@Service
public class DirectChatService {

    private static final String OLLAMA_BASE = "http://localhost:11434";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    // ── 蜂刺工具调用格式 ──────────────────────────────────────────────────────
    // LLM 在回复中嵌入：<use_stinger name="script.sh" args="arg1 arg2"/>
    private static final Pattern STINGER_CALL = Pattern.compile(
        "<use_stinger\\s+name=\"([^\"]+)\"(?:\\s+args=\"([^\"]*)\")?\\s*/?>"
    );

    /**
     * 蜂刺感知对话（无私有目录版，向后兼容）
     */
    public String chatWithStingers(String question, String provider, String model,
                                   String apiKey, String baseUrl, Double temperature,
                                   String systemPrompt, List<Map<String, Object>> stingers) {
        return chatWithStingers(question, provider, model, apiKey, baseUrl, temperature,
            systemPrompt, stingers, null, null);
    }

    /**
     * 蜂刺感知对话：注入工具说明 → 解析 LLM 回复 → 执行蜂刺 → 二次 LLM 生成最终答案。
     * hiveId/beeId 用于优先读取蜜蜂私有 skills/ 脚本副本。
     */
    public String chatWithStingers(String question, String provider, String model,
                                   String apiKey, String baseUrl, Double temperature,
                                   String systemPrompt, List<Map<String, Object>> stingers,
                                   String hiveId, String beeId) {
        if (stingers == null || stingers.isEmpty()) {
            return chat(question, provider, model, apiKey, baseUrl, temperature, systemPrompt);
        }

        // ① 构建蜂刺工具说明，追加到 system prompt
        StringBuilder toolDocs = new StringBuilder();
        toolDocs.append("\n\n## 你可以使用以下蜂刺技能 (Tools)\n");
        toolDocs.append("当需要调用技能时，在回复中插入如下格式（可多次使用）：\n");
        toolDocs.append("<use_stinger name=\"脚本文件名\" args=\"参数1 参数2\"/>\n\n");
        toolDocs.append("可用技能：\n");
        for (Map<String, Object> s : stingers) {
            toolDocs.append("• ").append(s.get("id"))
                    .append(" ｜ ").append(s.getOrDefault("name", s.get("id")))
                    .append("：").append(s.getOrDefault("description", "")).append("\n");
        }
        toolDocs.append("\n调用技能后系统会自动执行并将结果告知你，你再给出最终回复。");

        String enhancedPrompt = (systemPrompt != null ? systemPrompt : "") + toolDocs;

        // ② 第一次 LLM 调用
        String firstResponse = chat(question, provider, model, apiKey, baseUrl, temperature, enhancedPrompt);

        // ③ 检测并执行蜂刺调用
        Matcher matcher = STINGER_CALL.matcher(firstResponse);
        if (!matcher.find()) {
            return firstResponse; // 没有工具调用，直接返回
        }

        StringBuilder stingerLog   = new StringBuilder();
        String        cleanResponse = firstResponse;

        matcher.reset();
        while (matcher.find()) {
            String scriptName = matcher.group(1);
            String argsStr    = matcher.group(2);
            List<String> args = (argsStr != null && !argsStr.isBlank())
                ? List.of(argsStr.trim().split("\\s+")) : List.of();

            String result = executeStinger(scriptName, args, hiveId, beeId);
            stingerLog.append("\n[蜂刺: ").append(scriptName).append("]\n")
                      .append("```\n").append(result).append("\n```\n");
            cleanResponse = cleanResponse.replace(matcher.group(0), "");
        }

        // ④ 第二次 LLM 调用：把执行结果告知模型，生成最终回复
        String followUp = question
            + "\n\n[系统: 以下是蜂刺执行结果，请结合结果给用户最终回复]"
            + stingerLog;
        String finalAnswer = chat(followUp, provider, model, apiKey, baseUrl, temperature, enhancedPrompt);

        // ⑤ 组合返回：工具执行记录 + 最终答案
        return "⚡ **蜂刺已调用**\n" + stingerLog + "\n---\n" + finalAnswer;
    }

    /** 执行蜂刺脚本，优先使用蜜蜂私有 skills/ 副本，返回输出文本 */
    private String executeStinger(String name, List<String> args, String hiveId, String beeId) {
        String safeName = name.replaceAll("[^A-Za-z0-9._\\-]", "");

        // 优先：蜜蜂私有 skills/ 目录（个人定制版）
        Path script = null;
        if (hiveId != null && beeId != null) {
            Path localScript = Paths.get(System.getProperty("user.home"), ".openbe",
                "hives", hiveId, beeId, "skills", safeName);
            if (Files.exists(localScript)) {
                script = localScript;
                System.out.printf("[DirectChat] 🐝 使用私有蜂刺: %s/%s/skills/%s%n", hiveId, beeId, safeName);
            }
        }
        // 兜底：全局蜂刺库
        if (script == null) {
            script = StingerLibrary.getStingersDir().resolve(safeName);
        }

        if (!Files.exists(script)) return "❌ 蜂刺不存在: " + safeName;

        try {
            List<String> cmd = new ArrayList<>();
            if (safeName.endsWith(".sh")) {
                cmd.add("/bin/bash"); cmd.add(script.toString());
            } else if (safeName.endsWith(".scpt")) {
                cmd.add("osascript"); cmd.add(script.toString());
            } else {
                cmd.add(script.toString());
            }
            cmd.addAll(args);

            Process proc = new ProcessBuilder(cmd)
                .directory(script.getParent().toFile())
                .redirectErrorStream(true)
                .start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append("\n");
                    if (out.length() > 4096) break;
                }
            }
            proc.waitFor(30, TimeUnit.SECONDS);
            String result = out.toString().trim();
            return result.isEmpty() ? "(无输出)" : result;
        } catch (Exception e) {
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    public String chat(String question, String provider, String model,
                       String apiKey, String baseUrl,
                       Double temperature, String systemPrompt) {
        if (provider == null || provider.isBlank() || "ollama".equalsIgnoreCase(provider)) {
            return callOllama(question, model, temperature, systemPrompt);
        }
        return switch (provider.toLowerCase().trim()) {
            case "anthropic" -> callAnthropic(question, model, apiKey, temperature, systemPrompt);
            default          -> callOpenAiCompat(question, model, apiKey,
                                    resolveBase(provider, baseUrl), temperature, systemPrompt);
        };
    }

    // ── Ollama ────────────────────────────────────────────────────────────────

    private String callOllama(String prompt, String model, Double temperature, String systemPrompt) {
        try {
            ObjectNode req = objectMapper.createObjectNode();
            req.put("model",  model != null && !model.isBlank() ? model : "llama3");
            req.put("stream", false);
            req.put("think",  false);   // 禁用思考模式，避免生成大量隐藏推理 token
            ArrayNode messages = req.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank())
                messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", prompt);
            if (temperature != null) req.putObject("options").put("temperature", temperature);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE + "/api/chat"))
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(req.toString()))
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                return "Ollama 错误（" + response.statusCode() + "）：" + response.body();
            JsonNode node = objectMapper.readTree(response.body());
            return node.path("message").path("content").asText("（无回复）");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("connect"))
                return "无法连接 Ollama，请确认 `ollama serve` 已启动";
            return "Ollama 调用异常：" + msg;
        }
    }

    // ── OpenAI 兼容（OpenAI / DeepSeek / Qwen / Custom）─────────────────────

    private String callOpenAiCompat(String prompt, String model, String apiKey,
                                    String base, Double temperature, String systemPrompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model",  model != null && !model.isBlank() ? model : "gpt-4o-mini");
            body.put("stream", false);
            if (temperature != null) body.put("temperature", temperature);
            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank())
                messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base.replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                return "API 错误（" + response.statusCode() + "）：" + response.body();
            JsonNode node = objectMapper.readTree(response.body());
            return node.path("choices").path(0).path("message").path("content").asText("（无回复）");
        } catch (Exception e) {
            return "外部 API 调用失败：" + e.getMessage();
        }
    }

    // ── Anthropic ─────────────────────────────────────────────────────────────

    private String callAnthropic(String prompt, String model, String apiKey,
                                 Double temperature, String systemPrompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model != null && !model.isBlank() ? model : "claude-3-5-sonnet-20241022");
            body.put("max_tokens", 4096);
            if (temperature != null) body.put("temperature", temperature);
            if (systemPrompt != null && !systemPrompt.isBlank()) body.put("system", systemPrompt);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "user").put("content", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type",       "application/json")
                .header("x-api-key",          apiKey)
                .header("anthropic-version",  "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                return "Anthropic 错误（" + response.statusCode() + "）：" + response.body();
            JsonNode node = objectMapper.readTree(response.body());
            return node.path("content").path(0).path("text").asText("（无回复）");
        } catch (Exception e) {
            return "Anthropic 调用失败：" + e.getMessage();
        }
    }

    private String resolveBase(String provider, String customBase) {
        if ("custom".equals(provider) && customBase != null && !customBase.isBlank()) return customBase;
        if ("openai".equals(provider))    return "https://api.openai.com/v1";
        if ("deepseek".equals(provider))  return "https://api.deepseek.com/v1";
        if ("qwen".equals(provider))      return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        return customBase != null && !customBase.isBlank() ? customBase : "https://api.openai.com/v1";
    }
}
