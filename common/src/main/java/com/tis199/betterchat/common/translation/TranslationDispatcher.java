package com.tis199.betterchat.common.translation;

import com.tis199.betterchat.common.model.LanguageCatalog;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Rotates configured keys/providers, falls back on errors, and quarantines 429 keys until restart. */
public final class TranslationDispatcher {
    private final List<TranslationProvider> providers;
    private final Consumer<String> providerNotice;
    private final AtomicInteger cursor = new AtomicInteger();
    private final Map<String, Boolean> disabledKeys = new ConcurrentHashMap<>();
    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final int CACHE_LIMIT = 20_000;
    private record CacheKey(String message, String target, String sourceHint) { }
    private record CacheEntry(String translated, long expiresAtMillis) { }

    public TranslationDispatcher(List<TranslationProvider> providers) {
        this(providers, ignored -> { });
    }

    public TranslationDispatcher(List<TranslationProvider> providers, Consumer<String> providerNotice) {
        this.providers = List.copyOf(providers);
        this.providerNotice = providerNotice == null ? ignored -> { } : providerNotice;
    }

    public CompletableFuture<Map<String, List<String>>> translate(
            List<String> messages, List<String> targetLanguages, String probableSourceLanguage) {
        if (providers.isEmpty()) return CompletableFuture.failedFuture(new IllegalStateException("No translation provider is configured"));
        boolean needsLatinScript = targetLanguages.stream().anyMatch(LanguageCatalog::requiresLatinScript);
        int first = nextActive(cursor.getAndIncrement(), needsLatinScript);
        if (first < 0) return CompletableFuture.failedFuture(new IllegalStateException(needsLatinScript
                ? "No active AI translation provider can transliterate the requested language into Latin script"
                : "All configured API keys are disabled"));

        Map<String, Map<String, String>> initialHits = new java.util.HashMap<>();
        Set<String> uniqueMissing = new LinkedHashSet<>();
        for (String message : messages) {
            Map<String, String> targetHits = new java.util.HashMap<>();
            for (String target : targetLanguages) {
                String hit = cached(message, target, probableSourceLanguage);
                if (hit != null) {
                    targetHits.put(target, hit);
                } else {
                    uniqueMissing.add(message);
                }
            }
            initialHits.put(message, targetHits);
        }

        CompletableFuture<Map<String, List<String>>> fresh;
        List<String> missing = List.copyOf(uniqueMissing);
        if (missing.isEmpty()) {
            fresh = CompletableFuture.completedFuture(Map.of());
        } else {
            fresh = tryProvider(first, missing, targetLanguages, probableSourceLanguage, needsLatinScript, 0).thenApply(result -> {
                long expiresAt = System.currentTimeMillis() + CACHE_TTL_MILLIS;
                for (String target : targetLanguages) {
                    List<String> rows = result.get(target);
                    if (rows == null) continue;
                    for (int index = 0; index < missing.size() && index < rows.size(); index++) {
                        cache.put(new CacheKey(missing.get(index), target, probableSourceLanguage),
                                new CacheEntry(rows.get(index), expiresAt));
                    }
                }
                trimCache();
                return result;
            });
        }

        return fresh.thenApply(freshResult -> {
            Map<String, Integer> missingIndexMap = new java.util.HashMap<>();
            for (int i = 0; i < missing.size(); i++) {
                missingIndexMap.put(missing.get(i), i);
            }

            Map<String, List<String>> assembled = new java.util.LinkedHashMap<>();
            for (String target : targetLanguages) {
                List<String> rows = new ArrayList<>(messages.size());
                List<String> freshRows = freshResult.get(target);
                for (String message : messages) {
                    Map<String, String> cachedForMsg = initialHits.get(message);
                    String value = cachedForMsg != null ? cachedForMsg.get(target) : null;
                    if (value == null && freshRows != null && missingIndexMap.containsKey(message)) {
                        int idx = missingIndexMap.get(message);
                        if (idx < freshRows.size()) {
                            value = freshRows.get(idx);
                        }
                    }
                    if (value == null) {
                        value = cached(message, target, probableSourceLanguage);
                    }
                    if (value == null) throw new IllegalStateException("A translation was not returned for " + target);
                    rows.add(value);
                }
                assembled.put(target, List.copyOf(rows));
            }
            return Map.copyOf(assembled);
        });
    }

    public boolean isDisabled(String keyId) { return disabledKeys.containsKey(keyId); }

    private String cached(String message, String target, String sourceHint) {
        CacheKey key = new CacheKey(message, target, sourceHint);
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.expiresAtMillis() < System.currentTimeMillis()) {
            cache.remove(key, entry);
            return null;
        }
        return entry.translated();
    }

    private void trimCache() {
        if (cache.size() <= CACHE_LIMIT) return;
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
        if (cache.size() > CACHE_LIMIT) {
            var iterator = cache.keySet().iterator();
            int toRemove = cache.size() - CACHE_LIMIT;
            while (toRemove > 0 && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
        }
    }

    private CompletableFuture<Map<String, List<String>>> tryProvider(
            int start, List<String> messages, List<String> targets, String sourceHint,
            boolean needsLatinScript, int attempts) {
        if (attempts >= providers.size()) {
            return CompletableFuture.failedFuture(new IllegalStateException("All configured translation keys are unavailable"));
        }
        TranslationProvider provider = providers.get(start);
        CompletableFuture<Map<String, List<String>>> request;
        try {
            request = provider.translate(messages, targets, sourceHint);
        } catch (RuntimeException failure) {
            request = CompletableFuture.failedFuture(failure);
        }
        return request.handle((result, failure) -> {
            if (failure == null) return CompletableFuture.completedFuture(result);
            Throwable cause = unwrap(failure);
            String failureType = cause instanceof TranslationException exception
                    ? "HTTP " + exception.statusCode()
                    : cause.getClass().getSimpleName();
            String detail = cause instanceof TranslationException exception
                    ? exception.getMessage() : cause.getMessage();
            if (detail != null) {
                detail = detail.replaceAll("[\\p{Cntrl}]", " ").trim();
                if (cause instanceof TranslationException exception) {
                    String statusPrefix = "HTTP " + exception.statusCode() + ":";
                    if (detail.startsWith(statusPrefix)) detail = detail.substring(statusPrefix.length()).trim();
                }
                if (detail.length() > 240) detail = detail.substring(0, 240);
            }
            providerNotice.accept("Translation provider " + provider.id() + " key " + provider.keyId()
                    + " failed (" + failureType + (detail == null || detail.isBlank() ? "" : ": " + detail) + ").");
            if (cause instanceof TranslationException exception && exception.statusCode() == 429) {
                if (disabledKeys.putIfAbsent(provider.keyId(), true) == null) {
                    providerNotice.accept("Provider " + provider.id() + " key " + provider.keyId()
                            + " returned HTTP 429 and is disabled until restart.");
                }
            }
            int next = nextActive(start + 1, needsLatinScript);
            if (next < 0) return CompletableFuture.<Map<String, List<String>>>failedFuture(cause);
            return tryProvider(next, messages, targets, sourceHint, needsLatinScript, attempts + 1);
        }).thenCompose(future -> future);
    }

    private int nextActive(int from, boolean needsLatinScript) {
        if (providers.isEmpty()) return -1;
        int size = providers.size();
        int normalized = Math.floorMod(from, size);
        for (int offset = 0; offset < size; offset++) {
            int index = (normalized + offset) % size;
            TranslationProvider provider = providers.get(index);
            if (!disabledKeys.containsKey(provider.keyId())
                    && (!needsLatinScript || provider.supportsLatinScriptOutput())) return index;
        }
        return -1;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) current = current.getCause();
        return current;
    }
}
