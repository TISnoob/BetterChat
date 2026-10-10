package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.model.CountryCatalog;
import com.tis199.betterchat.common.model.LanguageCatalog;
import com.tis199.betterchat.common.model.PlayerPreferences;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class BetterChatCommand implements CommandExecutor, TabCompleter {
    private final BetterChatPaperPlugin plugin;
    private final BetterChatMenu menu;

    BetterChatCommand(BetterChatPaperPlugin plugin, BetterChatMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) return fail(sender, "Only players can open the BetterChat menu.");
            menu.openMain(player);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("language") || subcommand.equals("lang")) {
            if (args.length == 1 && sender instanceof Player player) {
                menu.openMain(player);
                return true;
            }
            if (args.length < 2) return fail(sender, "Usage: /bc language <language-name-or-code>");
            String language = LanguageCatalog.resolve(String.join(" ", args).substring(args[0].length()).trim());
            if (!LanguageCatalog.contains(language) || isBlacklisted("language.blacklist", language))
                return fail(sender, "That language is invalid or disabled. Use an ISO code or language name.");
            Player target = requirePlayer(sender);
            if (target == null) return true;
            plugin.updatePreferences(plugin.preferences().get(target.getUniqueId()).withLanguage(language));
            plugin.refreshPlayer(target);
            target.sendMessage(Component.text("Your chat language is now " + LanguageCatalog.displayName(language) + "."));
            return true;
        }
        if (subcommand.equals("flag") || subcommand.equals("country")) {
            if (args.length == 1 && sender instanceof Player player) {
                menu.openMain(player);
                return true;
            }
            if (args.length < 2) return fail(sender, "Usage: /bc flag <country-code|name|earth|global>");
            String country = findCountry(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            if (country == null || isBlacklisted("flags.blacklist", country)) return fail(sender, "That country is unknown or disabled.");
            Player target = requirePlayer(sender);
            if (target == null) return true;
            plugin.updatePreferences(plugin.preferences().get(target.getUniqueId()).withCountry(country, false));
            plugin.refreshPlayer(target);
            target.sendMessage(Component.text("Your flag is now " + CountryCatalog.flag(country) + " " + CountryCatalog.all().get(country) + "."));
            return true;
        }
        if (subcommand.equals("auto")) {
            Player target = requirePlayer(sender);
            if (target == null) return true;
            if (args.length > 2) return fail(sender, "Usage: /bc auto [on|off]");
            PlayerPreferences current = plugin.preferences().get(target.getUniqueId());
            boolean enabled = args.length == 1 ? !current.automaticCountry() : args[1].equalsIgnoreCase("on");
            if (args.length == 2 && !args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))
                return fail(sender, "Usage: /bc auto [on|off]");
            plugin.setAutomaticCountry(target, enabled);
            target.sendMessage(Component.text("Automatic country lookup " + (enabled ? "enabled" : "disabled") + "."));
            return true;
        }
        if (subcommand.equals("set")) return setOtherPlayer(sender, args);
        if (subcommand.equals("reload")) {
            if (!sender.hasPermission("betterchat.admin")) return fail(sender, "You do not have permission.");
            try {
                plugin.reloadSettings();
                sender.sendMessage(Component.text("BetterChat configuration reloaded."));
            } catch (Exception exception) {
                sender.sendMessage(Component.text("Reload failed: " + exception.getMessage()));
                plugin.getLogger().severe("Configuration reload failed: " + exception.getMessage());
            }
            return true;
        }
        sender.sendMessage(Component.text("/bc menu | language <language-name-or-code> | flag <country-code> | auto [on|off]"));
        return true;
    }

    private boolean setOtherPlayer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("betterchat.admin")) return fail(sender, "You do not have permission.");
        if (args.length < 4) return fail(sender, "Usage: /bc set <player> <language|flag> <value>");
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) return fail(sender, "That player is not online.");
        PlayerPreferences current = plugin.preferences().get(target.getUniqueId());
        if (args[2].equalsIgnoreCase("language")) {
            String language = LanguageCatalog.resolve(String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
            if (!LanguageCatalog.contains(language) || isBlacklisted("language.blacklist", language))
                return fail(sender, "That language is invalid or disabled. Use an ISO code or language name.");
            plugin.updatePreferences(current.withLanguage(language));
            plugin.refreshPlayer(target);
            target.sendMessage(Component.text("An administrator changed your chat language to " + LanguageCatalog.displayName(language) + "."));
            sender.sendMessage(Component.text("Updated " + target.getName() + "'s language."));
            return true;
        }
        if (args[2].equalsIgnoreCase("flag") || args[2].equalsIgnoreCase("country")) {
            String country = findCountry(String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
            if (country == null || isBlacklisted("flags.blacklist", country)) return fail(sender, "That country is unknown or disabled.");
            plugin.updatePreferences(current.withCountry(country, false));
            plugin.refreshPlayer(target);
            target.sendMessage(Component.text("An administrator changed your country flag to " + CountryCatalog.all().get(country) + "."));
            sender.sendMessage(Component.text("Updated " + target.getName() + "'s flag."));
            return true;
        }
        return fail(sender, "Use language or flag.");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        fail(sender, "Console must specify a player: /bc set <player> <language|flag> <value>");
        return null;
    }

    private boolean isBlacklisted(String path, String code) {
        return plugin.settings().strings(path).stream().anyMatch(value -> path.equals("language.blacklist")
                ? code.equalsIgnoreCase(value) || code.equalsIgnoreCase(LanguageCatalog.resolve(value))
                : value.equalsIgnoreCase(code));
    }

    private String findCountry(String input) {
        if (input.equalsIgnoreCase("earth") || input.equalsIgnoreCase("global")
                || input.equalsIgnoreCase("world")) return "EARTH";
        String code = input.toUpperCase(Locale.ROOT);
        if (CountryCatalog.contains(code)) return code;
        return CountryCatalog.all().entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(input))
                .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
    }

    private static boolean fail(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("menu", "language", "flag", "auto", "set", "reload");
        if (args.length == 2 && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang")))
            return LanguageCatalog.all().keySet().stream().filter(code -> code.startsWith(args[1].toLowerCase(Locale.ROOT))).limit(60).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("flag") || args[0].equalsIgnoreCase("country")))
            return java.util.stream.Stream.concat(java.util.stream.Stream.of("earth", "global"),
                            CountryCatalog.all().keySet().stream())
                    .filter(code -> code.startsWith(args[1].toLowerCase(Locale.ROOT))
                            || code.startsWith(args[1].toUpperCase(Locale.ROOT)))
                    .distinct().limit(60).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return List.of("language", "flag");
        return List.of();
    }
}
