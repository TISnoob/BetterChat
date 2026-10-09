package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.config.YamlConfig;
import com.tis199.betterchat.common.model.CountryCatalog;
import com.tis199.betterchat.common.model.PlayerPreferences;
import com.tis199.betterchat.common.model.ProxyChatMessage;
import com.tis199.betterchat.common.storage.JdbcPreferenceStore;
import com.tis199.betterchat.common.storage.PreferenceStore;
import com.tis199.betterchat.common.translation.TranslationBatchQueue;
import com.tis199.betterchat.common.translation.TranslationDispatcher;
import com.tis199.betterchat.common.translation.TranslationProvider;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.net.http.HttpClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BetterChatPaperPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final int BSTATS_PLUGIN_ID = 34609;
    static final String PROXY_CHANNEL = "betterchat:chat";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private volatile YamlConfig settings;
    private volatile PreferenceStore store;
    private volatile PreferenceCache preferences;
    private volatile TranslationBatchQueue translations;
    private volatile boolean translationProvidersConfigured;
    private volatile String knownFormatter;
    private volatile GroupAssignmentService groupAssignments;
    private BetterChatMenu menu;
    private ChatListener activeChatListener;
    private final ConcurrentMap<String, Long> seenProxyMessages = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> tabListDecorated = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        try {
            settings = YamlConfig.load(getDataFolder().toPath().resolve("config.yml"), getResource("config.yml"));
            store = openStore(settings);
        } catch (Exception exception) {
            getLogger().severe("Could not initialize BetterChat storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        createServices();
        menu = new BetterChatMenu(this);
        getServer().getPluginManager().registerEvents(this, this);
        activeChatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(activeChatListener, this);
        getServer().getPluginManager().registerEvents(new VentureChatAdapter(this), this);
        getServer().getPluginManager().registerEvents(menu, this);
        if (settings.bool("proxy-mode.enabled", false)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, PROXY_CHANNEL);
            getServer().getMessenger().registerIncomingPluginChannel(this, PROXY_CHANNEL, this);
        }
        getCommand("bc").setExecutor(new BetterChatCommand(this, menu));
        getCommand("translate").setExecutor(new TranslateCommand(this));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new BetterChatExpansion(this).register();
        }
        if (settings.bool("bstats.enabled", true)) new Metrics(this, BSTATS_PLUGIN_ID);
        getLogger().info("Enabled. Proxy mode is " + (settings.bool("proxy-mode.enabled", false) ? "on" : "off") + ".");
    }

    private void createServices() {
        preferences = new PreferenceCache(store, settings.string("defaults.language", "en_us"),
                settings.string("defaults.country", "EARTH"), settings.bool("flags.auto-detect-ip", false));
        List<TranslationProvider> providers = TranslationProviders.create(settings);
        translationProvidersConfigured = !providers.isEmpty();
        translations = new TranslationBatchQueue(new TranslationDispatcher(providers,
                message -> getLogger().warning(message)),
                settings.integer("translation.queue-capacity", 2048),
                settings.integer("translation.batch-window-ms", 45),
                settings.integer("translation.batch-size", 32));
        if (settings.bool("chat.translation-enabled", false) && providers.isEmpty()) {
            getLogger().warning("Chat translation is enabled, but no provider has an API key configured.");
        }
        groupAssignments = null;
        if (settings.bool("groups.enabled", false)) {
            String groupProvider = settings.string("groups.provider", "luckperms");
            if (groupProvider.equalsIgnoreCase("luckperms")
                    && Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                groupAssignments = new GroupAssignmentService(this);
            } else if (groupProvider.equalsIgnoreCase("vault")
                    && Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                groupAssignments = new GroupAssignmentService(this);
            } else {
                getLogger().warning("Group assignment is enabled, but the configured LuckPerms/Vault provider is not installed.");
            }
        }
        knownFormatter = detectedFormatter();
        if (settings.bool("chat.built-in-format", true)
                && settings.bool("chat.auto-disable-format-with-known-plugin", true) && knownFormatter != null) {
            getLogger().info("Detected " + knownFormatter + "; BetterChat's built-in formatter is disabled. Use the BetterChat PlaceholderAPI placeholders in that chat format.");
        }
    }

    private PreferenceStore openStore(YamlConfig config) throws Exception {
        String type = config.string("storage.type", "sqlite").toLowerCase();
        boolean proxyMode = config.bool("proxy-mode.enabled", false);
        if (proxyMode && !type.equals("mariadb")) {
            throw new IllegalStateException("proxy-mode.enabled requires storage.type: mariadb on every backend server");
        }
        if (proxyMode && config.string("proxy-mode.secret", "").length() < 32) {
            throw new IllegalStateException("proxy-mode.enabled requires proxy-mode.secret to contain at least 32 characters; use the same secret on the proxy and all backends");
        }
        int poolSize = config.integer("storage.mariadb.pool-size", 8);
        if (type.equals("sqlite")) {
            Path file = getDataFolder().toPath().resolve(config.string("storage.sqlite-file", "betterchat.db"));
            return new JdbcPreferenceStore("jdbc:sqlite:" + file.toAbsolutePath(), "", "", poolSize);
        }
        if (type.equals("mariadb")) {
            String host = config.string("storage.mariadb.host", "127.0.0.1");
            String database = config.string("storage.mariadb.database", "betterchat");
            int port = config.integer("storage.mariadb.port", 3306);
            String jdbc = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf8";
            return new JdbcPreferenceStore(jdbc, config.string("storage.mariadb.username", "betterchat"),
                    config.string("storage.mariadb.password", ""), poolSize);
        }
        throw new IllegalArgumentException("storage.type must be sqlite or mariadb");
    }

    private String detectedFormatter() {
        for (String name : settings.strings("chat.detected-formatters")) {
            if (Bukkit.getPluginManager().isPluginEnabled(name)) return name;
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String address = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        preferences.load(player, loaded -> {
            getServer().getGlobalRegionScheduler().execute(this, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null) {
                    refreshPlayer(online);
                    sendResourcePack(online);
                }
            });
            GroupAssignmentService groups = groupAssignments;
            if (groups != null) groups.apply(loaded);
            if (address != null && loaded.automaticCountry() && settings.bool("flags.auto-detect-ip", false))
                detectCountry(address, loaded.uniqueId());
        });
    }

    private void detectCountry(String address, UUID playerId) {
        if (address.equals("127.0.0.1") || address.equals("::1") || address.startsWith("10.") || address.startsWith("192.168.")) return;
        String endpoint = settings.string("flags.auto-detect-endpoint", "https://ipapi.co/{ip}/json/");
        String uri = endpoint.replace("{ip}", URLEncoder.encode(address, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(4)).GET().build();
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) return;
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                String country;
                if (json.has("country_code")) country = json.get("country_code").getAsString();
                else if (json.has("countryCode") && (!json.has("status") || json.get("status").getAsString().equals("success")))
                    country = json.get("countryCode").getAsString();
                else return;
                if (CountryCatalog.contains(country) && !isCountryBlacklisted(country)) {
                    PlayerPreferences updated = preferences.get(playerId).withCountry(country, true);
                    updatePreferences(updated);
                    getServer().getGlobalRegionScheduler().execute(this, () -> {
                        Player online = Bukkit.getPlayer(playerId);
                        if (online != null) refreshPlayer(online);
                    });
                }
            } catch (RuntimeException ignored) { }
        }).exceptionally(error -> null);
    }

    private boolean isCountryBlacklisted(String country) {
        return settings.strings("flags.blacklist").stream().anyMatch(value -> value.equalsIgnoreCase(country));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        preferences.remove(id);
        tabListDecorated.remove(id);
        String name = player.getName();
        getServer().getGlobalRegionScheduler().execute(this, () -> removeNametag(name, id));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) { refreshPlayer(event.getPlayer()); }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!PROXY_CHANNEL.equals(channel) || !settings.bool("proxy-mode.enabled", false)) return;
        try {
            ProxyChatMessage packet = ProxyChatMessage.decode(data, 'S', settings.string("proxy-mode.secret", ""));
            long now = System.currentTimeMillis();
            if (seenProxyMessages.putIfAbsent(packet.id(), now) != null) return;
            if (seenProxyMessages.size() > 8_192) {
                seenProxyMessages.entrySet().removeIf(entry -> now - entry.getValue() > 60_000);
                if (seenProxyMessages.size() > 8_192) seenProxyMessages.clear();
            }
            getServer().getGlobalRegionScheduler().execute(this, () -> {
                if (activeChatListener != null) activeChatListener.onProxyMessage(packet);
            });
        } catch (RuntimeException exception) {
            getLogger().fine("Dropped an invalid or unsigned chat message received from the proxy.");
        }
    }

    void relayChat(Player sender, PlayerPreferences preferences, String message) {
        String origin = settings.string("proxy-mode.server-id", getServer().getName());
        ProxyChatMessage packet = ProxyChatMessage.create(sender.getUniqueId(), sender.getName(), preferences, message, origin);
        byte[] payload = packet.encode('C', settings.string("proxy-mode.secret", ""));
        sender.getScheduler().run(this, task -> {
            if (sender.isOnline()) sender.sendPluginMessage(this, PROXY_CHANNEL, payload);
        }, null);
    }

    YamlConfig settings() { return settings; }
    PreferenceCache preferences() { return preferences; }
    TranslationBatchQueue translations() { return translations; }
    boolean isTranslationEnabled() { return settings.bool("chat.translation-enabled", false); }
    boolean hasTranslationProviders() { return translationProvidersConfigured; }

    void setAutomaticCountry(Player player, boolean enabled) {
        PlayerPreferences updated = preferences.get(player.getUniqueId()).withCountry(
                preferences.get(player.getUniqueId()).country(), enabled);
        updatePreferences(updated);
        if (enabled && settings.bool("flags.auto-detect-ip", false) && player.getAddress() != null) {
            String address = player.getAddress().getAddress().getHostAddress();
            detectCountry(address, updated.uniqueId());
        }
    }

    void updatePreferences(PlayerPreferences updated) {
        preferences.update(updated);
        GroupAssignmentService groups = groupAssignments;
        if (groups != null) groups.apply(updated);
    }

    boolean shouldUseBuiltInFormat() {
        return settings.bool("chat.built-in-format", true)
                && !(settings.bool("chat.auto-disable-format-with-known-plugin", true) && knownFormatter != null);
    }

    void refreshPlayer(Player player) {
        player.getScheduler().run(this, task -> {
            PlayerPreferences selected = preferences.get(player.getUniqueId());
            boolean visible = settings.bool("flags.enabled", true);
            if (visible && settings.bool("flags.tab-list.enabled", false)) {
                net.kyori.adventure.text.Component name = player.displayName();
                net.kyori.adventure.text.Component flagComponent = ChatListener.flagComponent(selected.country(), settings);
                net.kyori.adventure.text.Component decorated = settings.string("flags.tab-list.position", "before-name").equalsIgnoreCase("after-name")
                        ? name.append(net.kyori.adventure.text.Component.space()).append(flagComponent)
                        : flagComponent.append(net.kyori.adventure.text.Component.space()).append(name);
                player.playerListName(decorated);
                tabListDecorated.add(player.getUniqueId());
            } else if (tabListDecorated.remove(player.getUniqueId())) {
                player.playerListName(player.displayName());
            }
            String world = player.getWorld().getName();
            String playerName = player.getName();
            UUID id = player.getUniqueId();
            getServer().getGlobalRegionScheduler().execute(this,
                    () -> applyNametag(playerName, id, selected.country(), world));
        }, null);
    }

    private void removeNametag(String playerName, UUID playerId) {
        String teamName = "bc" + playerId.toString().replace("-", "").substring(0, 14);
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
        if (team == null) return;
        if (team.hasEntry(playerName)) team.removeEntry(playerName);
        if (team.getEntries().isEmpty()) team.unregister();
    }

    private void applyNametag(String playerName, UUID playerId, String country, String world) {
        String teamName = "bc" + playerId.toString().replace("-", "").substring(0, 14);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = scoreboard.getEntryTeam(playerName);
        Team own = scoreboard.getTeam(teamName);
        boolean enabled = settings.bool("flags.nametag.enabled", false)
                && settings.bool("flags.enabled", true)
                && worldAllowed(world);
        if (!enabled) {
            if (own != null && own.hasEntry(playerName)) own.removeEntry(playerName);
            if (own != null && own.getEntries().isEmpty()) own.unregister();
            return;
        }
        if (current != null && !current.getName().equals(teamName)) {
            if (!nametagConflictLogged) {
                nametagConflictLogged = true;
                getLogger().warning("Nametag flags cannot be applied to players already assigned to another scoreboard team. Use a nametag plugin placeholder instead.");
            }
            return;
        }
        if (own == null) own = scoreboard.registerNewTeam(teamName);
        net.kyori.adventure.text.Component flag = ChatListener.flagComponent(country, settings);
        if (settings.string("flags.nametag.position", "before-name").equalsIgnoreCase("after-name")) {
            own.prefix(net.kyori.adventure.text.Component.empty());
            own.suffix(net.kyori.adventure.text.Component.space().append(flag));
        } else {
            own.prefix(flag.append(net.kyori.adventure.text.Component.space()));
            own.suffix(net.kyori.adventure.text.Component.empty());
        }
        own.addEntry(playerName);
    }

    private volatile boolean nametagConflictLogged;

    private boolean worldAllowed(String world) {
        List<String> blacklist = settings.strings("flags.nametag.blacklist-worlds");
        if (blacklist.stream().anyMatch(name -> name.equalsIgnoreCase(world))) return false;
        List<String> whitelist = settings.strings("flags.nametag.worlds");
        return whitelist.isEmpty() || whitelist.stream().anyMatch(name -> name.equalsIgnoreCase(world));
    }

    private void sendResourcePack(Player player) {
        if (!settings.bool("flags.resource-pack.enabled", false)) return;
        String url = settings.string("flags.resource-pack.url", "").trim();
        if (url.isEmpty()) return;
        try {
            String sha1 = settings.string("flags.resource-pack.sha1", "").trim();
            byte[] hash = sha1.isEmpty() ? new byte[0] : java.util.HexFormat.of().parseHex(sha1);
            net.kyori.adventure.text.Component prompt = net.kyori.adventure.text.Component.text(
                    settings.string("flags.resource-pack.prompt", "Install BetterChat Flags to see country flags."));
            player.setResourcePack(url, hash, prompt, settings.bool("flags.resource-pack.required", false));
        } catch (RuntimeException exception) {
            getLogger().warning("Could not send the configured flag resource pack: " + exception.getMessage());
        }
    }

    void reloadSettings() throws Exception {
        YamlConfig nextSettings = YamlConfig.load(getDataFolder().toPath().resolve("config.yml"), null);
        PreferenceStore nextStore = openStore(nextSettings);
        PreferenceStore oldStore = store;
        TranslationBatchQueue oldTranslations = translations;
        store = nextStore;
        settings = nextSettings;
        createServices();
        oldTranslations.close();
        oldStore.close();
        getServer().getGlobalRegionScheduler().execute(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                preferences.load(player, loaded -> refreshPlayer(player));
            }
        });
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, PROXY_CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, PROXY_CHANNEL);
        if (translations != null) translations.close();
        if (store != null) store.close();
    }
}
