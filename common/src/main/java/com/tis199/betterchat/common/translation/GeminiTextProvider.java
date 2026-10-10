package com.tis199.betterchat.common.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Gemini's native generateContent adapter using ordinary text input and BetterChat's fixed system prompt. */
public final class GeminiTextProvider implements TranslationProvider {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String keyId;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final double temperature;

    public GeminiTextProvider(String keyId, String model, String apiKey, Duration timeout,
                              double temperature) {
        this.keyId = keyId;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(35) : timeout;
        this.temperature = temperature;
    }

    @Override public String id() { return "gemini"; }
    @Override public String keyId() { return keyId; }

    @Override
    public CompletableFuture<Map<String, List<String>>> translate(
            List<String> messages, List<String> targetLanguages, String probableSourceLanguage) {
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemText = new JsonObject();
        systemText.addProperty("text", TranslationPrompt.render(targetLanguages, probableSourceLanguage, messages.size()));
        systemParts.add(systemText);
        systemInstruction.add("parts", systemParts);

        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userText = new JsonObject();
        userText.addProperty("text", OpenAiCompatibleProvider.messagesJson(messages).toString());
        userParts.add(userText);
        userContent.add("parts", userParts);
        contents.add(userContent);

        JsonObject generation = new JsonObject();
        generation.addProperty("temperature", temperature);
        int outputTokens = Math.max(512, messages.size() * targetLanguages.size() * 220);
        generation.addProperty("maxOutputTokens", Math.min(8192, outputTokens));

        JsonObject requestBody = new JsonObject();
        requestBody.add("system_instruction", systemInstruction);
        requestBody.add("contents", contents);
        requestBody.add("generationConfig", generation);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20")
                + ":generateContent?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString())).build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TranslationException("HTTP " + response.statusCode()
                        + OpenAiCompatibleProvider.errorDetail(response.body()), response.statusCode());
            }
            try {
                JsonArray parts = JsonParser.parseString(response.body()).getAsJsonObject()
                        .getAsJsonArray("candidates").get(0).getAsJsonObject()
                        .getAsJsonObject("content").getAsJsonArray("parts");
                StringBuilder text = new StringBuilder();
                for (int index = 0; index < parts.size(); index++) {
                    JsonObject part = parts.get(index).getAsJsonObject();
                    if (part.has("thought") && part.get("thought").getAsBoolean()) continue;
                    if (part.has("text")) text.append(part.get("text").getAsString());
                }
                if (text.length() == 0) throw new IllegalArgumentException("Gemini returned no text candidate");
                return OpenAiCompatibleProvider.parseTranslations(text.toString(), targetLanguages, messages.size());
            } catch (RuntimeException exception) {
                throw new TranslationException("Gemini returned an invalid translation response", 502, exception);
            }
        });
    }
}
