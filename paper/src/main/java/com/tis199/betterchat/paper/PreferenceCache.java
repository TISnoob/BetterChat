package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.model.PlayerPreferences;
import com.tis199.betterchat.common.storage.PreferenceStore;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

final class PreferenceCache {
    private final PreferenceStore store;
    private final String defaultLanguage;
    private final String defaultCountry;
    private final boolean defaultAutomaticCountry;
    private final ConcurrentMap<UUID, PlayerPreferences> cache = new ConcurrentHashMap<>();

    PreferenceCache(PreferenceStore store, String defaultLanguage, String defaultCountry, boolean defaultAutomaticCountry) {
        this.store = store;
        this.defaultLanguage = defaultLanguage;
        this.defaultCountry = defaultCountry;
        this.defaultAutomaticCountry = defaultAutomaticCountry;
    }

    void load(Player player, Consumer<PlayerPreferences> callback) {
        UUID id = player.getUniqueId();
        store.load(id).thenAccept(saved -> {
            PlayerPreferences loaded = saved.orElseGet(() -> new PlayerPreferences(
                    id, defaultLanguage, defaultCountry, defaultAutomaticCountry));
            PlayerPreferences updatedWhileLoading = cache.putIfAbsent(id, loaded);
            PlayerPreferences effective = updatedWhileLoading == null ? loaded : updatedWhileLoading;
            if (saved.isEmpty() && updatedWhileLoading == null && cache.get(id) == loaded) store.save(loaded);
            callback.accept(cache.getOrDefault(id, effective));
        });
    }

    PlayerPreferences get(UUID id) {
        return cache.getOrDefault(id, new PlayerPreferences(id, defaultLanguage, defaultCountry, defaultAutomaticCountry));
    }

    void update(PlayerPreferences preferences) {
        cache.put(preferences.uniqueId(), preferences);
        store.save(preferences);
    }

    void remove(UUID id) { cache.remove(id); }
}
