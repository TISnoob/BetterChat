package com.tis199.betterchat.common.storage;

import com.tis199.betterchat.common.model.PlayerPreferences;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PreferenceStore extends AutoCloseable {
    CompletableFuture<Optional<PlayerPreferences>> load(UUID uniqueId);
    CompletableFuture<Void> save(PlayerPreferences preferences);
    @Override void close();
}
