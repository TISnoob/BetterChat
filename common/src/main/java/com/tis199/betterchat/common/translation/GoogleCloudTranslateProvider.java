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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Batches message text by target locale using Google Cloud Translation API v2. */
public final class GoogleCloudTranslateProvider implements TranslationProvider {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String keyId;
    private final String apiKey;
    private final Duration timeout;

    public GoogleCloudTranslateProvider(String keyId, String apiKey) {
        this(keyId, apiKey, Duration.ofSeconds(25));
    }

    public GoogleCloudTranslateProvider(String keyId, String apiKey, Duration timeout) {
        this.keyId = keyId;
        this.apiKey = apiKey;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(25) : timeout;
    }

    @Override public String id() { return "google-cloud"; }
    @Override public String keyId() { return keyId; }
    @Override public boolean supportsLatinScriptOutput() { return false; }

    @Override
    public CompletableFuture<Map<String, List<String>>> translate(
            List<String> messages, List<String> targetLanguages, String probableSourceLanguage) {
        Map<String, CompletableFuture<List<String>>> tasks = new LinkedHashMap<>();
        for (String target : targetLanguages) tasks.put(target, translateTo(messages, target));
        CompletableFuture<?>[] all = tasks.values().toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(all).thenApply(ignored -> {
            Map<String, List<String>> result = new LinkedHashMap<>();
            tasks.forEach((language, task) -> result.put(language, task.join()));
            return Map.copyOf(result);
        });
    }

    private CompletableFuture<List<String>> translateTo(List<String> messages, String target) {
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        messages.forEach(contents::add);
        body.add("q", contents);
        body.addProperty("target", target.replace('_', '-'));
        body.addProperty("format", "text");
        String endpoint = "https://translation.googleapis.com/language/translate/v2?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TranslationException("Google Cloud Translation returned HTTP " + response.statusCode(), response.statusCode());
            }
            try {
                JsonArray translations = JsonParser.parseString(response.body()).getAsJsonObject()
                        .getAsJsonObject("data").getAsJsonArray("translations");
                if (translations.size() != messages.size()) throw new IllegalArgumentException("Unexpected batch length");
                List<String> output = new ArrayList<>(translations.size());
                translations.forEach(item -> output.add(item.getAsJsonObject().get("translatedText").getAsString()));
                return List.copyOf(output);
            } catch (RuntimeException exception) {
                throw new TranslationException("Google Cloud Translation returned an invalid response", 502, exception);
            }
        });
    }
}
