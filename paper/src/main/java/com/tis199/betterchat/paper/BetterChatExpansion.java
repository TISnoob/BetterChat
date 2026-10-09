package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.model.CountryCatalog;
import com.tis199.betterchat.common.model.PlayerPreferences;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class BetterChatExpansion extends PlaceholderExpansion {
    private final BetterChatPaperPlugin plugin;
    BetterChatExpansion(BetterChatPaperPlugin plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "betterchat"; }
    @Override public String getAuthor() { return "TIS199"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String parameter) {
        if (player == null) return "";
        PlayerPreferences selected = plugin.preferences().get(player.getUniqueId());
        return switch (parameter.toLowerCase()) {
            case "flag", "player_flag" -> CountryCatalog.flag(selected.country());
            case "flag_glyph", "player_flag_glyph" -> CountryCatalog.glyph(selected.country());
            case "global_flag", "earth_flag" -> CountryCatalog.flag("EARTH");
            case "global_flag_glyph", "earth_flag_glyph" -> CountryCatalog.glyph("EARTH");
            case "nation", "nationality", "player_nation", "player_nationality", "country" ->
                    CountryCatalog.all().getOrDefault(selected.country(), "Unknown / hidden");
            case "country_code", "nation_code", "player_country_code" -> selected.country();
            case "language", "player_language", "language_code" -> selected.language();
            case "language_name" -> com.tis199.betterchat.common.model.LanguageCatalog.displayName(selected.language());
            case "automatic_country" -> Boolean.toString(selected.automaticCountry());
            default -> null;
        };
    }
}
