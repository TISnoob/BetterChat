package com.tis199.betterchat.common.model;

import java.util.Locale;
import java.util.UUID;

/** Persistent player settings shared by the server and proxy artifacts. */
public record PlayerPreferences(UUID uniqueId, String language, String country, boolean automaticCountry) {
    public PlayerPreferences {
        if (uniqueId == null) throw new IllegalArgumentException("uniqueId is required");
        language = normalizeLanguage(language);
        country = normalizeCountry(country);
    }

    public static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) return "en_us";
        return language.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    public static String normalizeCountry(String country) {
        if (country == null || country.isBlank()) return "EARTH";
        return country.trim().toUpperCase(Locale.ROOT);
    }

    public PlayerPreferences withLanguage(String value) {
        return new PlayerPreferences(uniqueId, value, country, automaticCountry);
    }

    public PlayerPreferences withCountry(String value, boolean automatic) {
        return new PlayerPreferences(uniqueId, language, value, automatic);
    }
}
