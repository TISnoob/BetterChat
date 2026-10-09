package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.model.LanguageCatalog;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One-off translation command using the same queue, provider policy, and batching as chat. */
final class TranslateCommand implements CommandExecutor, TabCompleter {
    private final BetterChatPaperPlugin plugin;

    TranslateCommand(BetterChatPaperPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("betterchat.translate")) {
            reply(sender, Component.text("You do not have permission."));
            return true;
        }
        if (args.length < 2) {
            reply(sender, Component.text("Usage: /translate <target-language> <text> (language codes such as bn or en_us work best)"));
            return true;
        }
        if (!plugin.hasTranslationProviders()) {
            reply(sender, Component.text("No translation provider is configured."));
            return true;
        }

        String language = LanguageCatalog.resolve(args[0]);
        if (language == null || language.equals("auto")) {
            reply(sender, Component.text("That target language is invalid. Use a language code or a one-word name."));
            return true;
        }
        String sourceText = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (sourceText.isEmpty()) {
            reply(sender, Component.text("Usage: /translate <target-language> <text>"));
            return true;
        }
        if (sourceText.length() > 2_000) {
            reply(sender, Component.text("Text is too long; the limit is 2,000 characters."));
            return true;
        }

        boolean queued = plugin.translations().submit(sourceText, Set.of(language), "auto", result -> {
            String translated = result.byLanguage().get(language);
            if (translated == null) {
                reply(sender, Component.text("Translation failed. Check the provider configuration and server log."));
                return;
            }
            reply(sender, Component.text("[" + LanguageCatalog.displayName(language) + "] " + translated));
        });
        if (!queued) reply(sender, Component.text("The translation queue is full or shutting down. Try again shortly."));
        return true;
    }

    private void reply(CommandSender sender, Component message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline()) player.sendMessage(message);
            }, null);
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(message));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return LanguageCatalog.all().keySet().stream().filter(code -> code.startsWith(prefix)).limit(80).toList();
    }
}
