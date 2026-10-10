package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.config.YamlConfig;
import com.tis199.betterchat.common.model.CountryCatalog;
import com.tis199.betterchat.common.model.PlayerPreferences;
import com.tis199.betterchat.common.model.ProxyChatMessage;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ChatListener implements Listener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final BetterChatPaperPlugin plugin;

    ChatListener(BetterChatPaperPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        YamlConfig config = plugin.settings();
        if (!config.bool("chat.enabled", true)) return;

        Player sender = event.getPlayer();
        PlayerPreferences senderPreferences = plugin.preferences().get(sender.getUniqueId());
        String original = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!plugin.shouldUseBuiltInFormat()) {
            if (plugin.settings().bool("proxy-mode.enabled", false)
                    || !plugin.isTranslationEnabled() || !plugin.hasTranslationProviders()) return;
            event.setCancelled(true);
            processWithRenderer(sender, event.renderer(), sender.displayName(), event.message(), original,
                    playerViewers(event.viewers()));
            return;
        }
        if (config.bool("proxy-mode.enabled", false)) {
            event.setCancelled(true);
            plugin.relayChat(sender, senderPreferences, original);
            return;
        }

        if (plugin.isTranslationEnabled() && plugin.hasTranslationProviders()) {
            event.setCancelled(true);
            process(sender.getUniqueId(), sender.getName(), senderPreferences, original, playerViewers(event.viewers()));
        } else {
            event.renderer((chatSender, displayName, message, viewer) -> render(chatSender.getName(),
                    plugin.preferences().get(chatSender.getUniqueId()), message, config));
        }
    }

    private void processWithRenderer(Player sender, ChatRenderer renderer, Component sourceDisplayName,
                                    Component originalComponent, String original, List<Player> viewers) {
        PlayerPreferences senderPreferences = plugin.preferences().get(sender.getUniqueId());
        YamlConfig config = plugin.settings();
        Map<UUID, PlayerPreferences> listenerPreferences = new LinkedHashMap<>();
        Set<String> targetLanguages = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            PlayerPreferences selected = plugin.preferences().get(viewer.getUniqueId());
            listenerPreferences.put(viewer.getUniqueId(), selected);
            if (selected.translationEnabled() && !selected.language().equals(senderPreferences.language()))
                targetLanguages.add(selected.language());
        }
        String consoleLanguage = config.string("defaults.console-language", "en_us");
        if (config.bool("chat.translate-console", true) && !consoleLanguage.equals(senderPreferences.language()))
            targetLanguages.add(consoleLanguage);
        if (targetLanguages.isEmpty()) {
            deliverWithRenderer(sender, renderer, sourceDisplayName, originalComponent, viewers, listenerPreferences,
                    senderPreferences, Map.of());
            logConsole(sender.getName(), original, Map.of());
            return;
        }
        boolean accepted = plugin.translations().submit(original, targetLanguages, senderPreferences.language(), result -> {
            deliverWithRenderer(sender, renderer, sourceDisplayName, originalComponent, viewers, listenerPreferences,
                    senderPreferences, result.byLanguage());
            logConsole(sender.getName(), original, result.byLanguage());
        });
        if (!accepted) {
            deliverWithRenderer(sender, renderer, sourceDisplayName, originalComponent, viewers, listenerPreferences,
                    senderPreferences, Map.of());
            logConsole(sender.getName(), original, Map.of());
        }
    }

    private void deliverWithRenderer(Player sender, ChatRenderer renderer, Component sourceDisplayName,
                                     Component originalComponent, List<Player> viewers,
                                     Map<UUID, PlayerPreferences> listenerPreferences,
                                     PlayerPreferences senderPreferences, Map<String, String> translationsByLanguage) {
        YamlConfig config = plugin.settings();
        for (Player viewer : viewers) {
            PlayerPreferences target = listenerPreferences.get(viewer.getUniqueId());
            boolean wantsTranslation = target.translationEnabled()
                    && !target.language().equals(senderPreferences.language());
            String translated = !wantsTranslation ? null
                    : translationsByLanguage.get(target.language());
            if (translated == null && wantsTranslation
                    && !config.bool("chat.show-original-on-translation-failure", true)) continue;
            Component message = translated == null ? originalComponent : Component.text(translated);
            Component rendered = renderer.render(sender, sourceDisplayName, message, viewer);
            viewer.getScheduler().run(plugin, task -> viewer.sendMessage(rendered), null);
        }
    }

    void onProxyMessage(ProxyChatMessage packet) {
        if (!plugin.settings().bool("chat.enabled", true) || !plugin.shouldUseBuiltInFormat()) return;
        PlayerPreferences sender = new PlayerPreferences(UUID.fromString(packet.senderId()),
                packet.language(), packet.country(), false);
        List<Player> viewers = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        process(sender.uniqueId(), packet.senderName(), sender, packet.message(), viewers);
    }

    private void process(UUID senderId, String senderName, PlayerPreferences sender, String original, List<Player> viewers) {
        YamlConfig config = plugin.settings();
        Map<UUID, PlayerPreferences> listenerPreferences = new LinkedHashMap<>();
        Set<String> targetLanguages = new LinkedHashSet<>();
        boolean translate = plugin.isTranslationEnabled() && plugin.hasTranslationProviders();
        for (Player viewer : viewers) {
            UUID id = viewer.getUniqueId();
            PlayerPreferences selected = plugin.preferences().get(id);
            listenerPreferences.put(id, selected);
            if (translate && selected.translationEnabled() && !selected.language().equals(sender.language()))
                targetLanguages.add(selected.language());
        }
        String consoleLanguage = config.string("defaults.console-language", "en_us");
        if (translate && config.bool("chat.translate-console", true) && !consoleLanguage.equals(sender.language()))
            targetLanguages.add(consoleLanguage);

        if (targetLanguages.isEmpty()) {
            deliver(senderName, sender, original, viewers, listenerPreferences, Map.of());
            logConsole(senderName, original, Map.of());
            return;
        }

        boolean accepted = plugin.translations().submit(original, targetLanguages, sender.language(), result -> {
            deliver(senderName, sender, original, viewers, listenerPreferences, result.byLanguage());
            logConsole(senderName, original, result.byLanguage());
        });
        if (!accepted) {
            deliver(senderName, sender, original, viewers, listenerPreferences, Map.of());
            logConsole(senderName, original, Map.of());
        }
    }

    private void deliver(String senderName, PlayerPreferences sender, String original, List<Player> viewers,
                         Map<UUID, PlayerPreferences> listenerPreferences, Map<String, String> translationsByLanguage) {
        YamlConfig config = plugin.settings();
        for (Player viewer : viewers) {
            PlayerPreferences target = listenerPreferences.get(viewer.getUniqueId());
            if (target == null) target = plugin.preferences().get(viewer.getUniqueId());
            boolean wantsTranslation = target.translationEnabled() && !target.language().equals(sender.language());
            String text = wantsTranslation ? translationsByLanguage.get(target.language()) : original;
            if (text == null) {
                if (wantsTranslation && !config.bool("chat.show-original-on-translation-failure", true)) continue;
                text = original;
            }
            Component message = format(senderName, sender, text, config);
            viewer.getScheduler().run(plugin, task -> viewer.sendMessage(message), null);
        }
    }

    private void logConsole(String senderName, String original, Map<String, String> translated) {
        YamlConfig config = plugin.settings();
        String consoleLanguage = config.string("defaults.console-language", "en_us");
        String template = config.string("chat.console-format", "[<language>] <player>: <message>");
        plugin.getLogger().info("[Chat/original] " + consoleLine(template, "original", senderName, original));
        plugin.getLogger().info("[Chat/" + consoleLanguage + "] " + consoleLine(template, consoleLanguage,
                senderName, translated.getOrDefault(consoleLanguage, original)));
    }

    private static String consoleLine(String template, String language, String player, String message) {
        return template.replace("<language>", language).replace("<player>", player).replace("<message>", message);
    }

    private static Component render(String senderName, PlayerPreferences sender, Component message, YamlConfig config) {
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        return format(senderName, sender, plain, config);
    }

    static Component format(String senderName, PlayerPreferences sender, String message, YamlConfig config) {
        String flag = config.bool("flags.enabled", true) ? CountryCatalog.flag(sender.country()) : "";
        Component flagComponent = flagComponent(sender.country(), config);
        String template = config.string("chat.format", "");
        if (template.isBlank()) {
            String position = config.string("chat.flag-position", "before-name");
            template = position.equalsIgnoreCase("after-name")
                    ? "<gold><player></gold> <white><flag></white><dark_gray>: <white><message>"
                    : "<dark_gray>[<white><flag></white>] <gold><player></gold><dark_gray>: <white><message>";
        }
        TagResolver placeholders = TagResolver.builder()
                .resolver(Placeholder.unparsed("player", senderName))
                .resolver(Placeholder.component("flag", flagComponent))
                .resolver(Placeholder.component("global_flag", flagComponent("EARTH", config)))
                .resolver(Placeholder.component("earth_flag", flagComponent("EARTH", config)))
                .resolver(Placeholder.component("message", Component.text(message)))
                .resolver(Placeholder.unparsed("language", sender.language()))
                .resolver(Placeholder.unparsed("country", sender.country()))
                .build();
        return MINI.deserialize(template, placeholders);
    }

    static Component flagComponent(String country, YamlConfig config) {
        if (config.bool("flags.resource-pack.use-glyphs", false)) {
            return Component.text(CountryCatalog.glyph(country)).font(Key.key("betterchat", "flags"));
        }
        return Component.text(CountryCatalog.flag(country));
    }

    private static List<Player> playerViewers(Set<Audience> audiences) {
        List<Player> result = new ArrayList<>();
        for (Audience audience : audiences) if (audience instanceof Player player) result.add(player);
        return result;
    }
}
