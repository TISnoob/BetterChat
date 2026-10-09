package com.tis199.betterchat.common.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Native Anthropic Messages API adapter. */
public final class AnthropicProvider implements TranslationProvider {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String keyId;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final double temperature;

    public AnthropicProvider(String keyId, String model, String apiKey) {
        this(keyId, model, apiKey, Duration.ofSeconds(35), 0.25);
    }

    public AnthropicProvider(String keyId, String model, String apiKey, Duration timeout, double temperature) {
        this.keyId = keyId;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(35) : timeout;
        this.temperature = temperature;
    }

    @Override public String id() { return "anthropic"; }
    @Override public String keyId() { return keyId; }

    @Override
    public CompletableFuture<Map<String, List<String>>> translate(
            List<String> messages, List<String> targetLanguages, String probableSourceLanguage) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", Math.max(512, messages.size() * 180));
        body.addProperty("temperature", temperature);
        body.addProperty("system", OpenAiCompatibleProvider.systemPrompt(targetLanguages, probableSourceLanguage, messages.size()));
        JsonArray contents = new JsonArray();
        for (int index = 0; index < messages.size(); index++) {
            JsonObject item = new JsonObject();
            item.addProperty("id", index);
            item.addProperty("text", messages.get(index));
            contents.add(item);
        }
        JsonArray chat = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.add("content", contents);
        chat.add(user);
        body.add("messages", chat);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(timeout).header("Content-Type", "application/json")
                .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TranslationException("Anthropic returned HTTP " + response.statusCode(), response.statusCode());
            }
            try {
                JsonArray blocks = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("content");
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < blocks.size(); i++) {
                    JsonObject block = blocks.get(i).getAsJsonObject();
                    if (block.has("text")) text.append(block.get("text").getAsString());
                }
                return OpenAiCompatibleProvider.parseTranslations(text.toString(), targetLanguages, messages.size());
            } catch (RuntimeException exception) {
                throw new TranslationException("Anthropic returned an invalid translation batch", 502, exception);
            }
        });
    }
}
