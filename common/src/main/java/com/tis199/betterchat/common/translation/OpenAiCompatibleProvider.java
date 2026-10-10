package com.tis199.betterchat.common.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** OpenAI-compatible chat completion provider, including local Ollama endpoints. */
public final class OpenAiCompatibleProvider implements TranslationProvider {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String providerId;
    private final String keyId;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final double temperature;
    private final Duration timeout;

    public OpenAiCompatibleProvider(String providerId, String keyId, String baseUrl,
                                    String model, String apiKey, double temperature) {
        this(providerId, keyId, baseUrl, model, apiKey, temperature, Duration.ofSeconds(35));
    }

    public OpenAiCompatibleProvider(String providerId, String keyId, String baseUrl,
                                    String model, String apiKey, double temperature, Duration timeout) {
        this.providerId = providerId;
        this.keyId = keyId;
        this.baseUrl = trimSlash(baseUrl);
        this.model = model;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.temperature = temperature;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(35) : timeout;
    }

    @Override public String id() { return providerId; }
    @Override public String keyId() { return keyId; }

    @Override
    public CompletableFuture<Map<String, List<String>>> translate(
            List<String> messages, List<String> targetLanguages, String probableSourceLanguage) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", temperature);
        JsonArray chatMessages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", TranslationPrompt.render(targetLanguages, probableSourceLanguage, messages.size()));
        chatMessages.add(system);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        // Chat-completions content must be text (or provider-specific typed text blocks), not our raw JSON array.
        user.addProperty("content", messagesJson(messages).toString());
        chatMessages.add(user);
        requestBody.add("messages", chatMessages);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));
        if (!apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
        return HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new TranslationException("HTTP " + response.statusCode()
                                + errorDetail(response.body()), response.statusCode());
                    }
                    try {
                        JsonObject envelope = JsonParser.parseString(response.body()).getAsJsonObject();
                        String content = envelope.getAsJsonArray("choices").get(0).getAsJsonObject()
                                .getAsJsonObject("message").get("content").getAsString();
                        return parseTranslations(content, targetLanguages, messages.size());
                    } catch (RuntimeException exception) {
                        throw new TranslationException("Provider " + providerId + " returned an invalid translation batch", 502, exception);
                    }
                });
    }

    static JsonArray messagesJson(List<String> messages) {
        JsonArray array = new JsonArray();
        for (int index = 0; index < messages.size(); index++) {
            JsonObject message = new JsonObject();
            message.addProperty("id", index);
            message.addProperty("text", messages.get(index));
            array.add(message);
        }
        return array;
    }

    static String systemPrompt(List<String> targets, String probableSourceLanguage, int count) {
        return TranslationPrompt.render(targets, probableSourceLanguage, count);
    }

    static Map<String, List<String>> parseTranslations(String response, List<String> targets, int expectedCount) {
        JsonObject root = parseObject(response);
        JsonObject translations = root.getAsJsonObject("translations");
        if (translations == null) throw new IllegalArgumentException("Missing translations object");
        java.util.Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        for (String target : targets) {
            JsonArray rows = translations.getAsJsonArray(target);
            if (rows == null || rows.size() != expectedCount) throw new IllegalArgumentException("Missing translation rows for " + target);
            String[] translated = new String[expectedCount];
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                JsonObject row = rows.get(rowIndex).getAsJsonObject();
                if (!row.has("id") || !row.has("text")) {
                    throw new IllegalArgumentException("Translation row for " + target + " must contain id and text");
                }
                var idValue = row.get("id");
                if (!idValue.isJsonPrimitive() || !idValue.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("Translation id for " + target + " must be an integer");
                }
                int id;
                try {
                    id = idValue.getAsBigDecimal().intValueExact();
                } catch (ArithmeticException | NumberFormatException exception) {
                    throw new IllegalArgumentException("Translation id for " + target + " must be an integer", exception);
                }
                if (id < 0 || id >= expectedCount) {
                    throw new IllegalArgumentException("Translation id out of range for " + target + ": " + id);
                }
                if (translated[id] != null) {
                    throw new IllegalArgumentException("Duplicate translation id for " + target + ": " + id);
                }
                var textValue = row.get("text");
                if (!textValue.isJsonPrimitive() || !textValue.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Translation text for " + target + " must be a string");
                }
                String value = textValue.getAsString();
                if (com.tis199.betterchat.common.model.LanguageCatalog.requiresLatinScript(target)
                        && !com.tis199.betterchat.common.model.LanguageCatalog.isLatinScript(value)) {
                    throw new IllegalArgumentException("Non-Latin output returned for " + target);
                }
                translated[id] = value;
            }
            List<String> ordered = new ArrayList<>(expectedCount);
            for (int id = 0; id < expectedCount; id++) {
                if (translated[id] == null) throw new IllegalArgumentException("Missing translation id for " + target + ": " + id);
                ordered.add(translated[id]);
            }
            result.put(target, List.copyOf(ordered));
        }
        return Map.copyOf(result);
    }

    private static JsonObject parseObject(String response) {
        String json = response == null ? "" : response.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException ignored) {
            int start = json.indexOf('{');
            if (start < 0) throw new IllegalArgumentException("No JSON object in model response");
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int index = start; index < json.length(); index++) {
                char character = json.charAt(index);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (inString && character == '\\') {
                    escaped = true;
                    continue;
                }
                if (character == '"') {
                    inString = !inString;
                } else if (!inString && character == '{') {
                    depth++;
                } else if (!inString && character == '}' && --depth == 0) {
                    return JsonParser.parseString(json.substring(start, index + 1)).getAsJsonObject();
                }
            }
            throw new IllegalArgumentException("No complete JSON object in model response");
        }
    }

    private static String trimSlash(String value) {
        String result = value == null || value.isBlank() ? "https://api.openai.com/v1" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    static String errorDetail(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error") && root.get("error").isJsonObject()) {
                JsonObject error = root.getAsJsonObject("error");
                for (String field : List.of("message", "status", "code")) {
                    if (error.has(field) && error.get(field).isJsonPrimitive()) {
                        String value = error.get(field).getAsString().replaceAll("[\\p{Cntrl}]", " ").trim();
                        if (!value.isBlank()) return ": " + value.substring(0, Math.min(240, value.length()));
                    }
                }
            }
        } catch (RuntimeException ignored) { }
        return "";
    }
}
