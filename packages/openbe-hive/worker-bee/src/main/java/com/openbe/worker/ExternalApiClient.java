package com.openbe.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 外部 API 模型客户端 — 支持 OpenAI / Anthropic / Custom（OpenAI 兼容）。
 * 蜜蜂通过读取 ~/.openbe/config/{type}-apikey.json 来调用该客户端。
 */
@Component
public class ExternalApiClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient buildClient(int timeoutSec) {
        return new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30,   TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .callTimeout(timeoutSec + 30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 调用外部 API 模型。
     *
     * @param prompt       用户问题
     * @param provider     "openai" | "anthropic" | "custom"
     * @param model        模型名
     * @param apiKey       API Key
     * @param customBase   自定义 baseUrl（custom provider 时使用；openai/anthropic 可为 null）
     * @param temperature  采样温度
     * @param systemPrompt 系统提示词
     */
    public String call(String prompt, String provider, String model,
                       String apiKey, String customBase,
                       Double temperature, String systemPrompt) {

        if (provider == null || provider.isBlank()) provider = "openai";
        provider = provider.toLowerCase().trim();

        try {
            return switch (provider) {
                case "anthropic" -> callAnthropic(prompt, model, apiKey, temperature, systemPrompt);
                default          -> callOpenAiCompat(prompt, model, apiKey,
                                        resolveBase(provider, customBase), temperature, systemPrompt);
            };
        } catch (Exception e) {
            return "外部 API 调用失败（" + provider + "）：" + e.getMessage();
        }
    }

    // ─── OpenAI / Custom 兼容格式 ────────────────────────────────────────────

    private String callOpenAiCompat(String prompt, String model, String apiKey,
                                    String base, Double temperature, String systemPrompt)
            throws IOException {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model != null && !model.isBlank() ? model : "gpt-4o-mini");
        body.put("stream", false);
        if (temperature != null) body.put("temperature", temperature);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", prompt);

        String url = base.replaceAll("/+$", "") + "/chat/completions";

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(body.toString(), JSON_TYPE))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .build();

        try (Response response = buildClient(120).newCall(request).execute()) {
            if (response.body() == null) return "API 返回空响应";
            String raw = response.body().string();
            if (!response.isSuccessful()) return "API 错误 " + response.code() + ": " + raw;
            JsonNode node = objectMapper.readTree(raw);
            return node.path("choices").path(0).path("message").path("content").asText("(无回复)");
        }
    }

    // ─── Anthropic Messages API ──────────────────────────────────────────────

    private String callAnthropic(String prompt, String model, String apiKey,
                                 Double temperature, String systemPrompt)
            throws IOException {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model != null && !model.isBlank() ? model : "claude-3-5-sonnet-20241022");
        body.put("max_tokens", 4096);
        if (temperature != null) body.put("temperature", temperature);
        if (systemPrompt != null && !systemPrompt.isBlank()) body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);

        Request request = new Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(RequestBody.create(body.toString(), JSON_TYPE))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .build();

        try (Response response = buildClient(120).newCall(request).execute()) {
            if (response.body() == null) return "Anthropic API 返回空响应";
            String raw = response.body().string();
            if (!response.isSuccessful()) return "Anthropic API 错误 " + response.code() + ": " + raw;
            JsonNode node = objectMapper.readTree(raw);
            return node.path("content").path(0).path("text").asText("(无回复)");
        }
    }

    private String resolveBase(String provider, String customBase) {
        if ("custom".equals(provider) && customBase != null && !customBase.isBlank()) return customBase;
        if ("openai".equals(provider)) return "https://api.openai.com/v1";
        return customBase != null && !customBase.isBlank() ? customBase : "https://api.openai.com/v1";
    }
}
