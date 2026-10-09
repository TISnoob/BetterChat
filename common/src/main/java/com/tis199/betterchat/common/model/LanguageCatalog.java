package com.tis199.betterchat.common.model;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Minecraft locales and the ISO-639 languages known to the running Java runtime. */
public final class LanguageCatalog {
    private static final String[] MINECRAFT_LOCALES = {
            "af_za", "ar_sa", "ast_es", "az_az", "ba_ru", "bar", "be_by", "bg_bg", "br_fr", "brb",
            "bs_ba", "ca_es", "cs_cz", "cy_gb", "da_dk", "de_at", "de_ch", "de_de", "el_gr", "en_au",
            "en_ca", "en_gb", "en_nz", "en_pt", "en_ud", "en_us", "eo_uy", "es_ar", "es_cl", "es_ec",
            "es_es", "es_mx", "es_uy", "es_ve", "et_ee", "eu_es", "fa_ir", "fi_fi", "fil_ph", "fo_fo",
            "fr_ca", "fr_fr", "fra_de", "fur_it", "fy_nl", "ga_ie", "gd_gb", "gl_es", "got_de", "gv_im",
            "haw_us", "he_il", "hi_in", "hr_hr", "hu_hu", "hy_am", "id_id", "ig_ng", "is_is", "it_it",
            "ja_jp", "ka_ge", "kk_kz", "kn_in", "ko_kr", "kw_gb", "la_la", "lb_lu", "li_li", "lmo",
            "lt_lt", "lv_lv", "mi_nz", "mk_mk", "mn_mn", "ms_my", "mt_mt", "nah", "nds_de", "nl_be",
            "nl_nl", "nn_no", "no_no", "oc_fr", "pa_in", "pl_pl", "pt_br", "pt_pt", "ro_ro", "ru_ru",
            "se_no", "sk_sk", "sl_si", "so_so", "sq_al", "sr_sp", "sv_se", "sxu", "ta_in", "th_th",
            "tl_ph", "tok", "tr_tr", "tt_ru", "uk_ua", "val_es", "vec_it", "vi_vn", "yi_de", "yo_ng",
            "zh_cn", "zh_hk", "zh_tw"
    };
    private static final Set<String> MINECRAFT_CODES = Set.of(MINECRAFT_LOCALES);
    private static final Set<String> MINECRAFT_BASE_CODES = MINECRAFT_CODES.stream()
            .map(code -> code.split("_", 2)[0]).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Pattern LANGUAGE_INPUT = Pattern.compile("[\\p{L}\\p{M}][\\p{L}\\p{M}0-9 _.'()/-]{0,63}");
    private static final Map<String, String> LANGUAGES = create();
    private static final Map<String, String> ALIASES = Map.of(
            "bangla", "bn", "farsi", "fa", "mandarin", "zh", "modern greek", "el",
            "moldovan", "ro", "burmese", "my", "castilian", "es", "haitian creole", "ht"
    );

    private LanguageCatalog() { }

    private static Map<String, String> create() {
        Map<String, String> result = new TreeMap<>();
        for (String code : MINECRAFT_LOCALES) {
            Locale locale = toLocale(code);
            String name = locale.getDisplayName(Locale.ENGLISH);
            if (name == null || name.isBlank()) name = code;
            result.put(code, name + " (" + code + ")");
        }
        // Java ships its ISO-639-1 language list, which is broader than Minecraft's locale list.
        for (String code : Locale.getISOLanguages()) {
            Locale locale = Locale.forLanguageTag(code);
            String name = locale.getDisplayLanguage(Locale.ENGLISH);
            if (name != null && !name.isBlank()) result.putIfAbsent(code, name + " (" + code + ")");
        }
        result.put("en", "English (en)");
        result.put("bn", "Bengali (Bangla) (bn)");
        result.put("fa", "Persian (Farsi) (fa)");
        result.put("en_us", "English (United States)");
        result.put("en_gb", "English (United Kingdom)");
        return Collections.unmodifiableMap(result);
    }

    public static Locale toLocale(String code) {
        String normalized = PlayerPreferences.normalizeLanguage(code);
        String[] parts = normalized.split("_", 2);
        return parts.length == 1 ? Locale.forLanguageTag(parts[0])
                : Locale.forLanguageTag(parts[0] + "-" + parts[1]);
    }

    public static Map<String, String> all() { return LANGUAGES; }

    /** Resolves ISO language codes, Minecraft locale codes, English names, aliases, or a custom language name. */
    public static String resolve(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = PlayerPreferences.normalizeLanguage(input);
        if (normalized.equals("auto")) return null;
        if (LANGUAGES.containsKey(normalized)) return normalized;
        String alias = ALIASES.get(normalized.replace('_', ' '));
        if (alias != null) return alias;
        for (Map.Entry<String, String> entry : LANGUAGES.entrySet()) {
            String label = entry.getValue().toLowerCase(Locale.ROOT);
            if (label.equals(normalized) || label.startsWith(normalized + " (")) return entry.getKey();
        }
        if (normalized.length() > 64 || !LANGUAGE_INPUT.matcher(normalized).matches()) return null;
        // Keep arbitrary BCP-47-like tags and named languages usable even when the JVM has no entry for them.
        return normalized;
    }

    public static boolean contains(String code) { return resolve(code) != null; }

    /** True when Minecraft does not list the selected target locale and AI must use Latin letters. */
    public static boolean requiresLatinScript(String language) {
        String normalized = PlayerPreferences.normalizeLanguage(language);
        String baseLanguage = normalized.split("_", 2)[0];
        return !MINECRAFT_BASE_CODES.contains(baseLanguage);
    }

    public static boolean isLatinScript(String text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (Character.isLetterOrDigit(codePoint) && script != Character.UnicodeScript.LATIN
                    && script != Character.UnicodeScript.COMMON) return false;
            if (Character.getType(codePoint) == Character.NON_SPACING_MARK
                    || Character.getType(codePoint) == Character.COMBINING_SPACING_MARK) {
                if (script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.INHERITED
                        && script != Character.UnicodeScript.COMMON) return false;
            }
        }
        return true;
    }

    public static String displayName(String language) {
        String normalized = PlayerPreferences.normalizeLanguage(language);
        String known = LANGUAGES.get(normalized);
        if (known != null) return known;
        String name = normalized.replace('_', ' ').replace('-', ' ');
        StringBuilder title = new StringBuilder(name.length());
        boolean capitalize = true;
        for (char character : name.toCharArray()) {
            title.append(capitalize ? Character.toTitleCase(character) : character);
            capitalize = Character.isWhitespace(character) || character == '-' || character == '\'';
        }
        return title + " (custom)";
    }
}
