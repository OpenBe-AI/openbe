package com.openbe.nurse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 护士蜂专用 Ollama 客户端。
 * 支持动态 model、temperature、系统提示词热调（via Dashboard）。
 */
@Component
public class NurseOllamaClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    static final String DEFAULT_HONEY_SYSTEM =
        "你是一只护士蜂，请将以下对话提炼为极简的 JSON 格式事实（提炼核心知识点），" +
        "不超过50字，绝对不要输出任何废话。只输出一个合法的 JSON 对象，不要有任何多余文字。";

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

    public String distill(String question, String answer) {
        return distill(question, answer, null, null, null);
    }

    /**
     * 完整调用入口，支持热调参数。
     *
     * @param question     原始问题
     * @param answer       工蜂回答
     * @param modelOverride 动态模型名（null 使用默认）
     * @param temperature  采样温度（null 使用 Ollama 默认）
     * @param systemOverride 系统提示词覆盖（null 使用默认酿蜜提示词）
     */
    public String distill(String question, String answer,
                          String modelOverride, Double temperature, String systemOverride) {
        String model  = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : defaultModel;
        String system = (systemOverride != null && !systemOverride.isBlank()) ? systemOverride : DEFAULT_HONEY_SYSTEM;
        String prompt = "问题：" + question + "\n\n回答：" + answer;
        String url    = baseUrl.replaceAll("/+$", "") + "/api/generate";

        try {
            ObjectNode req = objectMapper.createObjectNode();
            req.put("model",  model);
            req.put("system", system);
            req.put("prompt", prompt);
            req.put("stream", false);
            if (temperature != null) {
                req.putObject("options").put("temperature", temperature);
            }

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(req.toString(), JSON_TYPE))
                .build();

            try (Response response = buildClient().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return "{\"error\":\"Ollama 未响应\",\"code\":" + response.code() + "}";
                }
                JsonNode node = objectMapper.readTree(response.body().string());
                String raw = node.path("response").asText("{}").trim();
                raw = raw.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("```$", "").trim();
                return raw.isEmpty() ? "{\"error\":\"模型返回空内容\"}" : raw;
            }

        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                return "{\"error\":\"酿蜜超时，模型未在 " + timeoutSeconds + "s 内响应\"}";
            }
            return "{\"error\":\"" + msg + "\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
