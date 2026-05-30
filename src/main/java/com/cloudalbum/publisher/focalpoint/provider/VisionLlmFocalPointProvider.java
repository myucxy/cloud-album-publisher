package com.cloudalbum.publisher.focalpoint.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class VisionLlmFocalPointProvider implements FocalPointProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PROMPT = "分析此图片，找出最重要的视觉焦点区域（人脸、显著物体等）。" +
            "返回 JSON 数组: [{\"x\":0.5,\"y\":0.3,\"confidence\":0.95,\"regionType\":\"FACE\",\"regionWidth\":0.2,\"regionHeight\":0.3}]" +
            "坐标为归一化 0.0-1.0（左上角为原点）。最多返回 5 个焦点，按 confidence 降序。" +
            "仅返回 JSON 数组，不要其他文字。";

    @Override
    public String getProviderType() {
        return "VISION_LLM";
    }

    @Override
    public List<FocalPointResult> detect(InputStream imageInputStream, Map<String, Object> extraConfig) {
        String apiEndpoint = (String) extraConfig.get("apiEndpoint");
        String apiKey = (String) extraConfig.get("apiKey");
        String modelName = (String) extraConfig.get("modelName");

        if (!StringUtils.hasText(apiEndpoint) || !StringUtils.hasText(modelName)) {
            log.warn("Vision LLM configuration incomplete");
            return Collections.emptyList();
        }

        try {
            // Read and encode image to base64
            byte[] imageBytes = readAllBytes(imageInputStream);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Build request
            Map<String, Object> request = buildRequest(modelName, base64Image, extraConfig);
            String requestBody = objectMapper.writeValueAsString(request);

            // Call API
            String response = callApi(apiEndpoint, apiKey, requestBody);

            // Parse response
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Vision LLM focal point detection failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildRequest(String modelName, String base64Image, Map<String, Object> extraConfig) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", modelName);

        Integer maxTokens = (Integer) extraConfig.get("maxTokens");
        request.put("max_tokens", maxTokens != null ? maxTokens : 1024);

        // Build messages
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();

        // Text content
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", PROMPT);
        content.add(textContent);

        // Image content
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, String> imageUrl = new HashMap<>();
        imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);

        message.put("content", content);
        messages.add(message);
        request.put("messages", messages);

        // Extra params (don't overwrite core fields)
        Set<String> protectedKeys = Set.of("model", "messages", "max_tokens");
        String extraParams = (String) extraConfig.get("extraParams");
        if (StringUtils.hasText(extraParams)) {
            try {
                Map<String, Object> params = objectMapper.readValue(extraParams, Map.class);
                params.forEach((k, v) -> {
                    if (!protectedKeys.contains(k)) {
                        request.putIfAbsent(k, v);
                    }
                });
            } catch (Exception e) {
                log.debug("Failed to parse extra params: {}", e.getMessage());
            }
        }

        return request;
    }

    private String callApi(String apiEndpoint, String apiKey, String requestBody) throws IOException {
        URL url = URI.create(apiEndpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (StringUtils.hasText(apiKey)) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorBody = readAllBytesAsString(conn.getErrorStream());
                throw new IOException("API returned status " + responseCode + ": " + errorBody);
            }

            return readAllBytesAsString(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private List<FocalPointResult> parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return Collections.emptyList();
            }

            String content = choices.get(0).get("message").get("content").asText();

            // Extract JSON array by finding first [ and last ]
            int start = content.indexOf('[');
            int end = content.lastIndexOf(']');
            if (start < 0 || end <= start) {
                log.debug("No JSON array found in LLM response, using center fallback");
                return Collections.singletonList(new FocalPointResult(0.5, 0.5, 0.0, "CENTER", 0.0, 0.0));
            }

            String jsonArrayStr = content.substring(start, end + 1);
            JsonNode array = objectMapper.readTree(jsonArrayStr);
            List<FocalPointResult> results = new ArrayList<>();

            for (JsonNode item : array) {
                double x = item.has("x") ? item.get("x").asDouble() : 0.5;
                double y = item.has("y") ? item.get("y").asDouble() : 0.5;
                double confidence = item.has("confidence") ? item.get("confidence").asDouble() : 0.5;
                String regionType = item.has("regionType") ? item.get("regionType").asText() : "SALIENCY";
                double regionWidth = item.has("regionWidth") ? item.get("regionWidth").asDouble() : 0.0;
                double regionHeight = item.has("regionHeight") ? item.get("regionHeight").asDouble() : 0.0;

                // Validate coordinates
                x = Math.max(0.0, Math.min(1.0, x));
                y = Math.max(0.0, Math.min(1.0, y));
                confidence = Math.max(0.0, Math.min(1.0, confidence));

                results.add(new FocalPointResult(x, y, confidence, regionType, regionWidth, regionHeight));
            }

            return results;
        } catch (Exception e) {
            log.warn("Failed to parse Vision LLM response: {}", e.getMessage());
            return Collections.singletonList(new FocalPointResult(0.5, 0.5, 0.0, "CENTER", 0.0, 0.0));
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        if (is == null) {
            return new byte[0];
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        return bos.toByteArray();
    }

    private String readAllBytesAsString(InputStream is) throws IOException {
        return new String(readAllBytes(is), StandardCharsets.UTF_8);
    }
}
