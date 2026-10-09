package com.tis199.betterchat.common.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Restricted YAML access for the two proxy platforms, which do not expose Bukkit's config API. */
public final class YamlConfig {
    private final Map<String, Object> root;

    private YamlConfig(Map<String, Object> root) { this.root = root; }

    @SuppressWarnings("unchecked")
    public static YamlConfig load(Path file, InputStream defaults) throws IOException {
        if (Files.notExists(file)) {
            Files.createDirectories(file.getParent());
            try (InputStream input = defaults) {
                if (input != null) Files.copy(input, file);
                else Files.createFile(file);
            }
        } else if (defaults != null) {
            defaults.close();
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (Reader reader = Files.newBufferedReader(file)) {
            Object value = yaml.load(reader);
            return new YamlConfig(value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of());
        }
    }

    public Object get(String path) {
        Object current = root;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
        }
        return current;
    }

    public String string(String path, String fallback) {
        Object value = get(path);
        return value == null ? fallback : String.valueOf(value);
    }

    public boolean bool(String path, boolean fallback) {
        Object value = get(path);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public int integer(String path, int fallback) {
        Object value = get(path);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public double decimal(String path, double fallback) {
        Object value = get(path);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public List<String> strings(String path) {
        Object value = get(path);
        if (!(value instanceof List<?> items)) return List.of();
        return items.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> maps(String path) {
        Object value = get(path);
        if (!(value instanceof List<?> items)) return List.of();
        return items.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    public Map<String, Object> root() { return Collections.unmodifiableMap(root); }
}
