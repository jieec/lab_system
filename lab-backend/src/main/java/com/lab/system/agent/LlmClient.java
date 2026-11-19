package com.lab.system.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** OpenAI-compatible streaming client. Provider errors deliberately exclude response bodies and credentials. */
@Component
@RequiredArgsConstructor
public class LlmClient {
    private final AgentProperties properties;
    private final ObjectMapper json;

    public Map<String, Object> complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                        Consumer<String> onToken) throws IOException {
        URI uri = URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions");
        if (!"https".equals(uri.getScheme())) throw new IOException("模型服务必须使用 HTTPS");
        HttpURLConnection connection = openConnection(uri);
        int timeout = Math.clamp(properties.getTimeoutSeconds(), 5, 60) * 1000;
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + properties.getApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "text/event-stream");
        Map<String, Object> body = new LinkedHashMap<>(Map.of("model", properties.getModel(), "messages", messages,
                "tools", tools, "stream", true, "max_tokens", 1800));
        if (uri.getHost().equals("api.deepseek.com")) body.put("thinking", Map.of("type", "disabled"));
        try {
            try (OutputStream out = connection.getOutputStream()) { json.writeValue(out, body); }
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException(switch (status) {
                case 401, 403 -> "模型密钥无效或没有调用权限";
                case 402 -> "模型账户余额不足，请充值后重试";
                case 429 -> "模型服务限流，请稍后重试";
                default -> "模型服务暂不可用（HTTP " + status + "）";
            });
            StringBuilder content = new StringBuilder();
            Map<Integer, Map<String, StringBuilder>> calls = new TreeMap<>();
            boolean done = false;
            long deadline = System.nanoTime() + timeout * 1_000_000L;
            int bytes = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    bytes += line.length();
                    if (bytes > 262144 || System.nanoTime() > deadline) throw new IOException("模型响应超出时间或长度限制");
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) { done = true; break; }
                    JsonNode root = json.readTree(data);
                    if (root.has("error")) throw new IOException("模型流式响应异常");
                    JsonNode choice = root.path("choices").path(0);
                    JsonNode delta = choice.path("delta");
                    if (delta.path("content").isTextual()) {
                        String token = delta.path("content").asText();
                        content.append(token);
                        onToken.accept(token);
                    }
                    for (JsonNode call : delta.path("tool_calls")) {
                        int index = call.path("index").asInt(-1);
                        if (index < 0 || index >= 8) throw new IOException("工具调用数量异常");
                        var parts = calls.computeIfAbsent(index, k -> new HashMap<>());
                        append(parts, "id", call.path("id"));
                        append(parts, "name", call.path("function").path("name"));
                        append(parts, "arguments", call.path("function").path("arguments"));
                    }
                }
            }
            if (!done) throw new IOException("模型响应中断，请重试");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("role", "assistant");
            result.put("content", content.toString());
            if (!calls.isEmpty()) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (var parts : calls.values()) {
                    String id = part(parts, "id"), name = part(parts, "name"), args = part(parts, "arguments");
                    if (id.isBlank() || name.isBlank() || args.length() > 10000) throw new IOException("工具参数无效");
                    list.add(Map.of("id", id, "type", "function", "function", Map.of("name", name, "arguments", args)));
                }
                result.put("tool_calls", list);
            }
            return result;
        } finally { connection.disconnect(); }
    }

    HttpURLConnection openConnection(URI uri) throws IOException {
        return (HttpURLConnection) uri.toURL().openConnection();
    }

    private static void append(Map<String, StringBuilder> parts, String key, JsonNode value) {
        if (value.isTextual()) parts.computeIfAbsent(key, k -> new StringBuilder()).append(value.asText());
    }
    private static String part(Map<String, StringBuilder> parts, String key) {
        return parts.getOrDefault(key, new StringBuilder()).toString();
    }
}
