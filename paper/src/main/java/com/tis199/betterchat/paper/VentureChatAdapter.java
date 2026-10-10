package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.model.PlayerPreferences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Optional reflection adapter: BetterChat does not bundle VentureChat's GPL-3.0 classes. */
final class VentureChatAdapter implements Listener {
    private static final String EVENT_NAME = "mineverse.Aust1n46.chat.api.events.VentureChatEvent";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final BetterChatPaperPlugin plugin;

    VentureChatAdapter(BetterChatPaperPlugin plugin) {
        this.plugin = plugin;
        Plugin ventureChat = Bukkit.getPluginManager().getPlugin("VentureChat");
        if (ventureChat == null || !ventureChat.isEnabled()) return;
        try {
            Class<?> raw = Class.forName(EVENT_NAME, false, ventureChat.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(raw)) throw new IllegalStateException("Unexpected VentureChat event type");
            @SuppressWarnings("unchecked") Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            EventExecutor executor = (ignored, event) -> handle(event);
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.HIGHEST, executor, plugin, false);
            plugin.getLogger().info("VentureChat detected; BetterChat will translate its local channel recipients.");
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("VentureChat was found, but its optional chat-event adapter could not be enabled: " + exception.getMessage());
        }
    }

    private void handle(Event event) throws EventException {
        if (!plugin.isTranslationEnabled() || !plugin.hasTranslationProviders()) return;
        try {
            Object wrapped = invoke(event, "getMineverseChatPlayer");
            if (wrapped == null) return; // VentureChat also fires a separate, non-cancellable proxy-receive event.
            UUID senderId = (UUID) invoke(wrapped, "getUUID");
            String senderName = String.valueOf(invoke(event, "getUsername"));
            String formattedPrefix = String.valueOf(invoke(event, "getFormat"));
            String rawChat = String.valueOf(invoke(event, "getChat"));
            @SuppressWarnings("unchecked") Set<Player> eventRecipients = (Set<Player>) invoke(event, "getRecipients");
            List<Player> recipients = new ArrayList<>(new LinkedHashSet<>(eventRecipients));
            PlayerPreferences sender = plugin.preferences().get(senderId);
            String plainMessage = ChatColor.stripColor(rawChat);
            if (plainMessage == null) plainMessage = rawChat;
            String text = plainMessage;
            Set<String> targets = new LinkedHashSet<>();
            Map<UUID, PlayerPreferences> listenerPreferences = new java.util.LinkedHashMap<>();
            for (Player recipient : recipients) {
                PlayerPreferences target = plugin.preferences().get(recipient.getUniqueId());
                listenerPreferences.put(recipient.getUniqueId(), target);
                if (target.translationEnabled() && !target.language().equals(sender.language()))
                    targets.add(target.language());
            }
            String consoleLanguage = plugin.settings().string("defaults.console-language", "en_us");
            if (plugin.settings().bool("chat.translate-console", true) && !consoleLanguage.equals(sender.language()))
                targets.add(consoleLanguage);
            // VentureChat fires this event immediately before it sends the final channel message.
            // Clearing its mutable local recipient set lets BetterChat send a translated copy once.
            eventRecipients.clear();
            if (targets.isEmpty()) {
                send(formattedPrefix, rawChat, sender, recipients, listenerPreferences, Map.of());
                return;
            }
            boolean accepted = plugin.translations().submit(text, targets, sender.language(), result -> {
                send(formattedPrefix, rawChat, sender, recipients, listenerPreferences, result.byLanguage());
                String translated = result.byLanguage().getOrDefault(consoleLanguage, text);
                plugin.getLogger().info("[VentureChat/original] " + ChatColor.stripColor(formattedPrefix + rawChat));
                plugin.getLogger().info("[VentureChat/" + consoleLanguage + "] " + ChatColor.stripColor(formattedPrefix + translated));
            });
            if (!accepted) send(formattedPrefix, rawChat, sender, recipients, listenerPreferences, Map.of());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("VentureChat message was left unchanged because its event shape could not be read: " + exception.getMessage());
        }
    }

    private void send(String prefix, String original, PlayerPreferences sender,
                      List<Player> recipients, Map<UUID, PlayerPreferences> preferences,
                      Map<String, String> translations) {
        for (Player recipient : recipients) {
            PlayerPreferences target = preferences.get(recipient.getUniqueId());
            String translated = !target.translationEnabled() || target.language().equals(sender.language()) ? original
                    : translations.getOrDefault(target.language(), original);
            String withStyle = retainLeadingLegacyStyle(original, translated);
            Component output = LEGACY.deserialize(prefix + withStyle);
            recipient.getScheduler().run(plugin, task -> recipient.sendMessage(output), null);
        }
    }

    private static String retainLeadingLegacyStyle(String original, String translated) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(\\s*(?:§[0-9A-FK-ORa-fk-or])*)").matcher(original);
        return matcher.find() ? matcher.group(1) + translated : translated;
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
}
