# AI chat and translation setup

Chat behavior and AI provider settings live in `plugins/BetterChat/ai.yml`. Set `chat.translation-enabled: true` to translate chat for each recipient; `/translate <target-language> <text>` uses the same provider chain even when automatic chat translation is off. In proxy mode, configure AI on each Paper backend that handles chat. Keep API keys out of public repositories.

## Text-model providers

BetterChat sends ordinary text-generation requests with a system instruction; it does not use a dedicated translation endpoint. Gemini uses its native `generateContent` API, following MCAIA's provider-specific request style. OpenAI, OpenRouter, Groq, xAI, and Ollama use their chat-completions-compatible APIs; Anthropic uses its Messages API.

| Provider id | API style | Main settings |
| --- | --- | --- |
| `gemini` | Native Gemini text generation | `models`, `api-keys` |
| `openai` | OpenAI Chat Completions | `base-url`, `models`, `api-keys` |
| `openrouter` | OpenRouter Chat Completions | `base-url`, `models`, `api-keys` |
| `groq` | Groq Chat Completions | `base-url`, `models`, `api-keys` |
| `anthropic` | Anthropic Messages API | `models`, `api-keys` |
| `xai` | xAI Chat Completions | `base-url`, `models`, `api-keys` |
| `ollama` | Local OpenAI-compatible chat endpoint | `base-url`, `model`; key can be blank |

Example configuration in `ai.yml`:

```yaml
chat:
  translation-enabled: true

translation:
  provider-order: [gemini, openai, ollama]
  providers:
    gemini:
      enabled: true
      models: [gemini-3.8-flash, gemini-2.5-flash]
      api-keys: ['GEMINI_KEY_A', 'GEMINI_KEY_B']
    openai:
      enabled: true
      models: [gpt-4.1-mini, gpt-4o-mini]
      api-keys: ['OPENAI_KEY']
    ollama:
      enabled: false
      base-url: 'http://127.0.0.1:11434/v1'
      model: 'llama3.1:8b'
      api-keys: ['']
```

The system instruction is fixed in the plugin and cannot be customized in `ai.yml`. BetterChat fills in the sender's probable source-language hint, the active recipients' target-language/script metadata, and the batch size at runtime. Messages are sent as ordinary text containing JSON records such as `[ {"id":0,"text":"hello"} ]`; player text is treated as data, not instructions.

Use model identifiers accepted by the provider. Gemini's `base-url` is intentionally not configurable because requests go to its native API. Keep Ollama bound to a trusted local/network interface; chat text is sent to that service.

## Batching and recipient locales

Paper/Folia snapshots the chat event's player audience before requesting translations. A provider receives the source hint and only the target languages needed by those listeners. VentureChat uses its pre-send recipient set for local channels. BetterChat does not infer a target from every online player.

Nearby jobs are collected for `translation.batch-window-ms`, grouped by identical target locale sets and source hints, and sent in batches up to `translation.batch-size` (32 by default). For example, ten queued messages with the same targets and source hint can be translated in one provider request. A larger queue is split across requests, and jobs with different target sets or source hints are separate batches. Each input gets a numeric ID; the model must echo that ID in each target's result rows, and BetterChat maps results by ID rather than array position. Missing, duplicate, out-of-range, or malformed IDs invalidate the response so the configured fallback provider can try. Successful per-message translations are cached in memory for ten minutes; the cache clears on restart.

## Fallback and language coverage

BetterChat rotates through configured keys/providers and model fallbacks after request failures or invalid output. A key that returns HTTP 429 is quarantined for the process lifetime; other configured keys remain available. Logs include the provider's safe error message when available, without logging credentials. If every request fails or the queue is full, `chat.show-original-on-translation-failure` controls whether recipients see the original message.

The language menu includes Minecraft locales and Java's available ISO-639-1 languages. `/bc language <name-or-code>` also accepts custom names or BCP-47-style tags, such as `bn`, `Bengali`, or `Toki Pona`. For a target without a Minecraft locale, the prompt instructs AI models to write Latin-letter conventional romanization; BetterChat checks and rejects non-Latin results so the next model can try. Thus Bengali `এখানে এসো` should be returned as `Ekhane Asho` for the custom/ISO `bn` choice; Minecraft-supported languages continue using their normal scripts.

The `/translate <target-language> <text>` command uses automatic source detection and the shared translation queue. Language codes work best when a language name has multiple words. Players and the console can use it whenever a text-model provider is configured, even if automatic translation is disabled. The `betterchat.translate` permission is granted by default.

Players can use `/translation off` to always receive the original chat message, or `/translation on` to receive translations for their selected language again. This preference is saved per player. An opted-out player's language is omitted from a request unless another opted-in recipient shares that language; in that case the one translation is still delivered only to opted-in recipients.

When upgrading from a version that kept `chat` and `translation` in `config.yml`, BetterChat copies those sections to `ai.yml` once. The translation instruction is plugin-owned and is not read from configuration.
