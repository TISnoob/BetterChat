package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.model.PlayerPreferences;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class TranslationPreferenceCommand implements CommandExecutor, TabCompleter {
    private final BetterChatPaperPlugin plugin;

    TranslationPreferenceCommand(BetterChatPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can change their chat translation preference."));
            return true;
        }
        if (args.length != 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            sender.sendMessage(Component.text("Usage: /translation <on|off>"));
            return true;
        }

        boolean enabled = args[0].toLowerCase(Locale.ROOT).equals("on");
        PlayerPreferences updated = plugin.preferences().get(player.getUniqueId()).withTranslationEnabled(enabled);
        plugin.updatePreferences(updated);
        player.sendMessage(Component.text("Chat translation is now " + (enabled ? "on" : "off") + "."
                + (enabled ? " You will receive translations for your selected language."
                : " You will always see the original chat message.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String partial = args[0].toLowerCase(Locale.ROOT);
        return List.of("on", "off").stream().filter(option -> option.startsWith(partial)).toList();
    }
}
