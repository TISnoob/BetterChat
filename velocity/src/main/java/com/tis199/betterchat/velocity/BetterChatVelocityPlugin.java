package com.tis199.betterchat.velocity;

import com.google.inject.Inject;
import com.tis199.betterchat.common.config.YamlConfig;
import com.tis199.betterchat.common.storage.JdbcPreferenceStore;
import com.tis199.betterchat.common.storage.PreferenceStore;
import com.tis199.betterchat.common.model.ProxyChatMessage;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "betterchat", name = "BetterChat", version = "0.1.0-pre.1",
        description = "Shared preference and chat support for BetterChat proxy mode.", authors = {"TIS199"})
public final class BetterChatVelocityPlugin {
    private static final int BSTATS_PLUGIN_ID = 34610;
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("betterchat", "chat");
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;
    private PreferenceStore store;
    private String sharedSecret;

    @Inject
    public BetterChatVelocityPlugin(ProxyServer server, Logger logger,
                                    @com.velocitypowered.api.plugin.annotation.DataDirectory Path dataDirectory,
                                    Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            YamlConfig config = YamlConfig.load(dataDirectory.resolve("config.yml"),
                    getClass().getClassLoader().getResourceAsStream("config.yml"));
            if (!config.bool("proxy-mode.enabled", true)) {
                throw new IllegalStateException("proxy-mode.enabled must be true in the Velocity config");
            }
            if (!config.string("storage.type", "mariadb").equalsIgnoreCase("mariadb")) {
                throw new IllegalStateException("Proxy mode requires storage.type: mariadb");
            }
            sharedSecret = config.string("proxy-mode.secret", "");
            if (sharedSecret.length() < 32) throw new IllegalStateException("Set proxy-mode.secret to the same random 32+ character value on the proxy and every backend");
            String jdbc = "jdbc:mariadb://" + config.string("storage.mariadb.host", "127.0.0.1") + ":"
                    + config.integer("storage.mariadb.port", 3306) + "/"
                    + config.string("storage.mariadb.database", "betterchat")
                    + "?useUnicode=true&characterEncoding=utf8";
            store = new JdbcPreferenceStore(jdbc, config.string("storage.mariadb.username", "betterchat"),
                    config.string("storage.mariadb.password", ""), config.integer("storage.mariadb.pool-size", 8));
            server.getChannelRegistrar().register(CHANNEL);
            server.getEventManager().register(this, this);
            if (config.bool("bstats.enabled", true)) metricsFactory.make(this, BSTATS_PLUGIN_ID);
            logger.info("BetterChat proxy mode is ready; player preferences are shared through MariaDB.");
        } catch (Exception exception) {
            logger.error("Could not start BetterChat Velocity proxy mode: {}", exception.getMessage());
            if (store != null) store.close();
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier()) || !(event.getSource() instanceof ServerConnection)) return;
        byte[] incoming = event.getData();
        if (incoming.length < 1 || incoming[0] != 'C' || incoming.length > 32_767) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        final ProxyChatMessage packet;
        try { packet = ProxyChatMessage.decode(incoming, 'C', sharedSecret); }
        catch (RuntimeException exception) {
            logger.debug("Dropped an invalid or unsigned BetterChat proxy packet.");
            return;
        }
        byte[] outgoing = packet.encode('S', sharedSecret);
        for (var backend : server.getAllServers()) {
            try { backend.sendPluginMessage(CHANNEL, outgoing); }
            catch (RuntimeException exception) {
                logger.debug("Could not relay BetterChat message to {}", backend.getServerInfo().getName(), exception);
            }
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        server.getEventManager().unregisterListeners(this);
        server.getChannelRegistrar().unregister(CHANNEL);
        if (store != null) store.close();
    }
}
