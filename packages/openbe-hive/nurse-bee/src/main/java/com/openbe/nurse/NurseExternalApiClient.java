package com.openbe.nurse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 护士蜂外部 API 客户端 — 支持 OpenAI / Anthropic / Custom。
 * NurseBeeService 通过 ~/.openbe/config/nurse-apikey.json 驱动。
 */
@Component
public class NurseExternalApiClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30,   TimeUnit.SECONDS)
            .readTimeout(120,   TimeUnit.SECONDS)
            .callTimeout(150,   TimeUnit.SECONDS)
            .build();
    }

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
            return "{\"error\":\"外部 API 调用失败（" + provider + "）：" + e.getMessage() + "\"}";
        }
    }

    private String callOpenAiCompat(String prompt, String model, String apiKey,
                                    String base, Double temperature, String systemPrompt)
            throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model != null && !model.isBlank() ? model : "gpt-4o-mini");
        body.put("stream", false);
        if (temperature != null) body.put("temperature", temperature);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank())
            messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", prompt);

        Request request = new Request.Builder()
            .url(base.replaceAll("/+$", "") + "/chat/completions")
            .post(RequestBody.create(body.toString(), JSON_TYPE))
            .header("Authorization", "Bearer " + apiKey)
            .build();

        try (Response response = buildClient().newCall(request).execute()) {
            if (response.body() == null) return "{\"error\":\"API 返回空响应\"}";
            String raw = response.body().string();
            if (!response.isSuccessful()) return "{\"error\":\"API " + response.code() + "\"}";
            JsonNode node = objectMapper.readTree(raw);
            return node.path("choices").path(0).path("message").path("content").asText("{}");
        }
    }

    private String callAnthropic(String prompt, String model, String apiKey,
                                  Double temperature, String systemPrompt) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model != null && !model.isBlank() ? model : "claude-3-5-sonnet-20241022");
        body.put("max_tokens", 1024);
        if (temperature != null) body.put("temperature", temperature);
        if (systemPrompt != null && !systemPrompt.isBlank()) body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);

        Request request = new Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(RequestBody.create(body.toString(), JSON_TYPE))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .build();

        try (Response response = buildClient().newCall(request).execute()) {
            if (response.body() == null) return "{\"error\":\"Anthropic API 空响应\"}";
            String raw = response.body().string();
            if (!response.isSuccessful()) return "{\"error\":\"Anthropic " + response.code() + "\"}";
            JsonNode node = objectMapper.readTree(raw);
            return node.path("content").path(0).path("text").asText("{}");
        }
    }

    private String resolveBase(String provider, String customBase) {
        if ("custom".equals(provider) && customBase != null && !customBase.isBlank()) return customBase;
        return "https://api.openai.com/v1";
    }
}
