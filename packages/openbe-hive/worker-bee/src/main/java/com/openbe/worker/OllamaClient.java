package com.openbe.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Ollama 本地大模型客户端（OkHttp3）。
 * 支持动态 model、temperature、systemPrompt 注入（来自 Dashboard 热调配置）。
 */
@Component
public class OllamaClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    @Value("${openbe.llm.base-url}")
    private String baseUrl;

    @Value("${openbe.llm.model}")
    private String defaultModel;

    @Value("${openbe.llm.timeout-seconds}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(10,  TimeUnit.SECONDS)
            .writeTimeout(30,    TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds + 15, TimeUnit.SECONDS)
            .build();
    }

    public String generate(String prompt) {
        return generate(prompt, null, null, null);
    }

    public String generate(String prompt, String modelOverride) {
        return generate(prompt, modelOverride, null, null);
    }

    /**
     * 完整调用入口，支持运行时热调参数。
     *
     * @param prompt       用户问题
     * @param modelOverride 动态模型名（null 使用配置默认值）
     * @param temperature  采样温度（null 使用 Ollama 默认值）
     * @param systemPrompt 系统提示词（null 不注入）
     */
    public String generate(String prompt, String modelOverride,
                           Double temperature, String systemPrompt) {
        String model = (modelOverride != null && !modelOverride.isBlank())
            ? modelOverride : defaultModel;
        String url = baseUrl.replaceAll("/+$", "") + "/api/generate";

        try {
            // 使用 ObjectNode 动态构建请求，避免序列化 null 字段
            ObjectNode req = objectMapper.createObjectNode();
            req.put("model",  model);
            req.put("prompt", prompt);
            req.put("stream", false);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                req.put("system", systemPrompt);
            }
            if (temperature != null) {
                req.putObject("options").put("temperature", temperature);
            }

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(req.toString(), JSON_TYPE))
                .build();

            try (Response response = buildClient().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return "本地大脑未响应，请检查是否启动了 Ollama（状态码：" + response.code() + "）";
                }
                JsonNode node = objectMapper.readTree(response.body().string());
                return node.path("response").asText("本地大脑未响应");
            }

        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                return "本地大脑响应超时（" + timeoutSeconds + "s），请检查 Ollama 是否正常运行";
            }
            return "本地大脑未响应（" + msg + "）";
        } catch (Exception e) {
            return "调用本地大脑时发生异常：" + e.getMessage();
        }
    }
}
