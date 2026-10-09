package com.tis199.betterchat.common.translation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface TranslationProvider {
    String id();
    String keyId();
    /** Whether this provider can follow BetterChat's Latin-script transliteration instruction. */
    default boolean supportsLatinScriptOutput() { return true; }
    CompletableFuture<Map<String, List<String>>> translate(
            List<String> messages, List<String> targetLanguages, String probableSourceLanguage);
}
