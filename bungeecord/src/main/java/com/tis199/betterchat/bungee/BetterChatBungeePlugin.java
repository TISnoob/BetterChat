package com.tis199.betterchat.bungee;

import com.tis199.betterchat.common.config.YamlConfig;
import com.tis199.betterchat.common.storage.JdbcPreferenceStore;
import com.tis199.betterchat.common.storage.PreferenceStore;
import com.tis199.betterchat.common.model.ProxyChatMessage;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.connection.Server;
import org.bstats.bungeecord.Metrics;

import java.io.IOException;
import java.nio.file.Path;

public final class BetterChatBungeePlugin extends Plugin implements Listener {
    private static final int BSTATS_PLUGIN_ID = 34611;
    private static final String CHANNEL = "betterchat:chat";
    private PreferenceStore store;
    private String sharedSecret;

    @Override
    public void onEnable() {
        try {
            Path configPath = getDataFolder().toPath().resolve("config.yml");
            YamlConfig config = YamlConfig.load(configPath, getResourceAsStream("config.yml"));
            if (!config.bool("proxy-mode.enabled", true)) {
                throw new IllegalStateException("proxy-mode.enabled must be true in the BungeeCord config");
            }
            if (!config.string("storage.type", "mariadb").equalsIgnoreCase("mariadb")) {
                throw new IllegalStateException("Proxy mode requires storage.type: mariadb");
            }
            sharedSecret = config.string("proxy-mode.secret", "");
            if (sharedSecret.length() < 32) throw new IllegalStateException("Set proxy-mode.secret to the same random 32+ character value on the proxy and every backend");
            String jdbc = jdbcUrl(config);
            store = new JdbcPreferenceStore(jdbc, config.string("storage.mariadb.username", "betterchat"),
                    config.string("storage.mariadb.password", ""), config.integer("storage.mariadb.pool-size", 8));
            getProxy().registerChannel(CHANNEL);
            getProxy().getPluginManager().registerListener(this, this);
            if (config.bool("bstats.enabled", true)) new Metrics(this, BSTATS_PLUGIN_ID);
            getLogger().info("Proxy mode is ready; player preferences are shared through MariaDB.");
        } catch (Exception exception) {
            getLogger().severe("Could not start BetterChat BungeeCord proxy mode: " + exception.getMessage());
            if (store != null) store.close();
            getProxy().getPluginManager().unregisterListeners(this);
            getProxy().unregisterChannel(CHANNEL);
        }
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getTag()) || !(event.getSender() instanceof Server)) return;
        byte[] incoming = event.getData();
        if (incoming.length < 1 || incoming[0] != 'C' || incoming.length > 32_767) return;
        event.setCancelled(true);
        final ProxyChatMessage packet;
        try { packet = ProxyChatMessage.decode(incoming, 'C', sharedSecret); }
        catch (RuntimeException exception) {
            getLogger().fine("Dropped an invalid or unsigned BetterChat proxy packet.");
            return;
        }
        byte[] outgoing = packet.encode('S', sharedSecret);
        getProxy().getServers().values().forEach(server -> {
            try { server.sendData(CHANNEL, outgoing, false); }
            catch (RuntimeException exception) {
                getLogger().fine("Could not relay BetterChat message to " + server.getName() + ": " + exception.getMessage());
            }
        });
    }

    private static String jdbcUrl(YamlConfig config) {
        return "jdbc:mariadb://" + config.string("storage.mariadb.host", "127.0.0.1") + ":"
                + config.integer("storage.mariadb.port", 3306) + "/"
                + config.string("storage.mariadb.database", "betterchat")
                + "?useUnicode=true&characterEncoding=utf8";
    }

    @Override
    public void onDisable() {
        getProxy().getPluginManager().unregisterListeners(this);
        getProxy().unregisterChannel(CHANNEL);
        if (store != null) store.close();
    }
}
