package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.config.YamlConfig;
import com.tis199.betterchat.common.translation.GoogleCloudTranslateProvider;
import com.tis199.betterchat.common.translation.AnthropicProvider;
import com.tis199.betterchat.common.translation.OpenAiCompatibleProvider;
import com.tis199.betterchat.common.translation.TranslationProvider;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

final class TranslationProviders {
    private TranslationProviders() { }

    static List<TranslationProvider> create(YamlConfig config) {
        List<TranslationProvider> providers = new ArrayList<>();
        Duration timeout = Duration.ofSeconds(Math.max(1, config.integer("translation.timeout-seconds", 35)));
        for (String id : config.strings("translation.provider-order")) {
            String root = "translation.providers." + id;
            if (!config.bool(root + ".enabled", false)) continue;
            List<String> keys = config.strings(root + ".api-keys");
            if (id.equalsIgnoreCase("ollama") && keys.isEmpty()) keys = List.of("");
            List<String> models = config.strings(root + ".models");
            String defaultModel = switch (id.toLowerCase()) {
                case "gemini" -> "gemini-3.8-flash";
                case "openrouter" -> "google/gemini-3.8-flash";
                case "groq" -> "openai/gpt-oss-20b";
                case "anthropic" -> "claude-sonnet-4-5";
                case "xai" -> "grok-4";
                case "ollama" -> "llama3.1:8b";
                default -> "gpt-4.1-mini";
            };
            if (models.isEmpty()) models = List.of(config.string(root + ".model", defaultModel));
            int index = 0;
            for (String key : keys) {
                if (!id.equalsIgnoreCase("ollama") && key.isBlank()) continue;
                String keyId = id + "-" + (++index);
                if (id.equalsIgnoreCase("google-cloud")) {
                    providers.add(new GoogleCloudTranslateProvider(keyId, key, timeout));
                } else {
                    String defaultUrl = switch (id.toLowerCase()) {
                        case "gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai";
                        case "openrouter" -> "https://openrouter.ai/api/v1";
                        case "groq" -> "https://api.groq.com/openai/v1";
                        case "xai" -> "https://api.x.ai/v1";
                        case "ollama" -> "http://127.0.0.1:11434/v1";
                        default -> "https://api.openai.com/v1";
                    };
                    for (String model : models) {
                        String modelKeyId = keyId;
                        if (id.equalsIgnoreCase("anthropic")) providers.add(new AnthropicProvider(modelKeyId, model, key,
                                timeout, config.decimal("translation.temperature", 0.25)));
                        else providers.add(new OpenAiCompatibleProvider(id, modelKeyId,
                                config.string(root + ".base-url", defaultUrl), model, key,
                                config.decimal("translation.temperature", 0.25), timeout));
                    }
                }
            }
        }
        return List.copyOf(providers);
    }
}
