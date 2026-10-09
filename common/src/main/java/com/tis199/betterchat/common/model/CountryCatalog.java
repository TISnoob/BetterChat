package com.tis199.betterchat.common.model;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** ISO-3166 country codes plus the special Earth flag. */
public final class CountryCatalog {
    private static final Map<String, String> COUNTRIES = create();

    private CountryCatalog() { }

    private static Map<String, String> create() {
        Map<String, String> result = new TreeMap<>();
        for (String code : Locale.getISOCountries()) {
            Locale country = new Locale.Builder().setRegion(code).build();
            result.put(code, country.getDisplayCountry(Locale.ENGLISH));
        }
        result.put("EARTH", "Earth (global)");
        result.put("ZZ", "Unknown / hidden");
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, String> all() { return COUNTRIES; }

    public static boolean contains(String code) {
        return COUNTRIES.containsKey(PlayerPreferences.normalizeCountry(code));
    }

    public static String flag(String code) {
        String normalized = PlayerPreferences.normalizeCountry(code);
        if (normalized.equals("EARTH")) return "🌍";
        if (normalized.length() != 2 || !normalized.chars().allMatch(c -> c >= 'A' && c <= 'Z')) return "🌐";
        int first = 0x1F1E6 + normalized.charAt(0) - 'A';
        int second = 0x1F1E6 + normalized.charAt(1) - 'A';
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    /** Private-use glyph rendered by the optional BetterChat resource pack. */
    public static String glyph(String code) {
        String normalized = PlayerPreferences.normalizeCountry(code);
        int value;
        if (normalized.equals("EARTH")) value = 0xE6FF;
        else if (normalized.length() == 2 && normalized.chars().allMatch(c -> c >= 'A' && c <= 'Z'))
            value = 0xE000 + (normalized.charAt(0) - 'A') * 26 + normalized.charAt(1) - 'A';
        else return "🌐";
        return Character.toString(value);
    }
}
