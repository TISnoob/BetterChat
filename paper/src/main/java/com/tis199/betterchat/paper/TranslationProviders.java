package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.config.YamlConfig;
import com.tis199.betterchat.common.translation.AnthropicProvider;
import com.tis199.betterchat.common.translation.GeminiTextProvider;
import com.tis199.betterchat.common.translation.OpenAiCompatibleProvider;
import com.tis199.betterchat.common.translation.TranslationProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class TranslationProviders {
    private static final Set<String> TEXT_MODEL_PROVIDERS = Set.of(
            "gemini", "openai", "openrouter", "groq", "anthropic", "xai", "ollama");

    private TranslationProviders() { }

    static List<TranslationProvider> create(YamlConfig config) {
        List<TranslationProvider> providers = new ArrayList<>();
        Duration timeout = Duration.ofSeconds(Math.max(1, config.integer("translation.timeout-seconds", 35)));
        double temperature = config.decimal("translation.temperature", 0.25);
        for (String configuredId : config.strings("translation.provider-order")) {
            String id = configuredId.toLowerCase(Locale.ROOT);
            if (!TEXT_MODEL_PROVIDERS.contains(id)) continue;
            String root = "translation.providers." + id;
            if (!config.bool(root + ".enabled", false)) continue;
            List<String> keys = config.strings(root + ".api-keys");
            if (id.equals("ollama") && keys.isEmpty()) keys = List.of("");
            List<String> models = config.strings(root + ".models");
            String defaultModel = switch (id) {
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
                if (!id.equals("ollama") && key.isBlank()) continue;
                String keyId = id + "-" + (++index);
                if (id.equals("gemini")) {
                    for (String model : models) {
                        providers.add(new GeminiTextProvider(keyId, model, key, timeout, temperature));
                    }
                } else {
                    String defaultUrl = switch (id) {
                        case "openrouter" -> "https://openrouter.ai/api/v1";
                        case "groq" -> "https://api.groq.com/openai/v1";
                        case "xai" -> "https://api.x.ai/v1";
                        case "ollama" -> "http://127.0.0.1:11434/v1";
                        default -> "https://api.openai.com/v1";
                    };
                    for (String model : models) {
                        if (id.equals("anthropic")) providers.add(new AnthropicProvider(keyId, model, key,
                                timeout, temperature));
                        else providers.add(new OpenAiCompatibleProvider(id, keyId,
                                config.string(root + ".base-url", defaultUrl), model, key,
                                temperature, timeout));
                    }
                }
            }
        }
        return List.copyOf(providers);
    }
}
