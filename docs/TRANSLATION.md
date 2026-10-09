# Translation setup

Set `chat.translation-enabled: true` in the Paper config, then enable and configure one or more providers. In proxy mode, provider configuration lives on each Paper backend that handles translation. Keys belong in the private server config, never in a public repository.

## Providers

The default provider priority is configured at `translation.provider-order`.

| Provider id | API type | Main settings |
| --- | --- | --- |
| `google-cloud` | Google Cloud Translation v2 | `api-keys` |
| `gemini` | Gemini OpenAI-compatible endpoint | `base-url`, `models`, `api-keys` |
| `openai` | OpenAI Chat Completions | `base-url`, `models`, `api-keys` |
| `openrouter` | OpenRouter Chat Completions | `base-url`, `models`, `api-keys` |
| `groq` | Groq Chat Completions | `base-url`, `models`, `api-keys` |
| `anthropic` | Anthropic Messages API | `models`, `api-keys` |
| `xai` | xAI Chat Completions | `base-url`, `models`, `api-keys` |
| `ollama` | Local OpenAI-compatible endpoint | `base-url`, `model`; keys may be blank |

Example with multiple provider keys and fallback models:

```yaml
translation:
  provider-order: [gemini, openai, google-cloud, ollama]
  providers:
    gemini:
      enabled: true
      base-url: 'https://generativelanguage.googleapis.com/v1beta/openai'
      models: [gemini-3.8-flash, gemini-2.5-flash]
      api-keys:
        - 'GEMINI_KEY_A'
        - 'GEMINI_KEY_B'
    openai:
      enabled: true
      models: [gpt-4.1-mini, gpt-4o-mini]
      api-keys: ['OPENAI_KEY']
    google-cloud:
      enabled: true
      api-keys: ['GOOGLE_TRANSLATE_KEY']
    ollama:
      enabled: false
      base-url: 'http://127.0.0.1:11434/v1'
      model: 'llama3.1:8b'
      api-keys: ['']
```

Use model identifiers supported by the endpoint you configured. Keep Ollama bound to a trusted local/network interface; chat text is sent to that service.

## Batching and recipient locales

Paper/Folia snapshots the chat event's player audience before asking for translations. A provider request includes the source hint and the target locales needed by those listeners. VentureChat's adapter uses its pre-send recipient set for local channels. It does not infer a target from every online player.

AI providers receive a JSON array of messages and a list of target locales together. The prompt describes the sender's selected language as a hint, not a guaranteed source language. It asks for exactly one output for each message and target, preserves commands and names, and permits context-sensitive Minecraft humor without changing meaning. Google Cloud v2 is called once per target locale with the batch of message texts.

Nearby jobs are collected for `batch-window-ms`, grouped by identical target locale sets and source hints, then sent up to `batch-size` messages at once. Successful individual translations are cached in memory for ten minutes. The cache clears on restart.

## Fallback and rate limits

BetterChat rotates through configured keys/providers and falls back after request failures. A provider key that returns HTTP 429 is quarantined for the process lifetime and will not be retried until restart. Other configured keys remain available. If every request fails or the queue is full, `chat.show-original-on-translation-failure` controls whether listeners see the original message.

Cloud translation is paid usage in most account configurations. Provider billing, privacy terms, model availability, and regional language support are controlled by each provider and can change independently of BetterChat.

## Language coverage and romanization

The language menu includes Minecraft's locale variants and the ISO-639-1 language codes available from Java. `/bc language <name-or-code>` also accepts a custom language name or BCP-47-style tag up to 64 characters, including languages without a Minecraft locale or two-letter ISO code. For example, `/bc language bn`, `/bc language Bengali`, and `/bc language Toki Pona` are valid choices. Custom choices are stored as normalized lowercase identifiers.

If the target language has no locale in Minecraft, AI providers are instructed to translate into the target language and write the result using Latin letters with conventional romanization. BetterChat checks the returned text and rejects non-Latin-script output for these targets so the next AI provider can try. For example, Bengali `এখানে এসো` becomes `Ekhane Asho`. The AI can translate Bengali typed in either native script or Latin letters. Languages that Minecraft supports continue to use their normal script.

Google Cloud Translation v2 cannot enforce the output script. For a target outside Minecraft's locale list, BetterChat skips Google Cloud and uses a configured AI provider. If none is available, chat follows `chat.show-original-on-translation-failure`. The `/translate <target-language> <text>` command uses automatic source detection and the shared translation queue; language codes work best when a language name has multiple words. Players and the console can use it whenever a provider is configured, even when automatic chat translation is disabled. The `betterchat.translate` permission is granted by default.
