package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.model.CountryCatalog;
import com.tis199.betterchat.common.model.LanguageCatalog;
import com.tis199.betterchat.common.model.PlayerPreferences;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class BetterChatMenu implements Listener {
    private static final int PAGE_SIZE = 45;
    private final BetterChatPaperPlugin plugin;

    BetterChatMenu(BetterChatPaperPlugin plugin) { this.plugin = plugin; }

    void openMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.MAIN, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("BetterChat settings"));
        holder.inventory = inventory;
        inventory.setItem(11, item(Material.WRITABLE_BOOK, "<gold>Choose language", "<gray>Chat is translated into this language. Use /bc language <name> for custom languages."));
        inventory.setItem(15, item(Material.WHITE_BANNER, "<gold>Choose flag", "<gray>Set your displayed country or enable automatic lookup."));
        player.openInventory(inventory);
    }

    private void openLanguages(Player player, int page) {
        List<Map.Entry<String, String>> entries = LanguageCatalog.all().entrySet().stream()
                .filter(entry -> !isBlacklisted("language.blacklist", entry.getKey()))
                .toList();
        openPaged(player, MenuType.LANGUAGES, page, entries, false);
    }

    private void openCountries(Player player, int page) {
        List<Map.Entry<String, String>> entries = CountryCatalog.all().entrySet().stream()
                .filter(entry -> !isBlacklisted("flags.blacklist", entry.getKey()))
                .toList();
        openPaged(player, MenuType.COUNTRIES, page, entries, true);
    }

    private void openPaged(Player player, MenuType type, int page, List<Map.Entry<String, String>> entries, boolean country) {
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder(type, currentPage);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text((country ? "Choose country flag" : "Choose language") + " — " + (currentPage + 1) + "/" + pages));
        holder.inventory = inventory;
        int start = currentPage * PAGE_SIZE;
        PlayerPreferences selected = plugin.preferences().get(player.getUniqueId());
        for (int index = 0; index < PAGE_SIZE && start + index < entries.size(); index++) {
            Map.Entry<String, String> option = entries.get(start + index);
            String code = option.getKey();
            boolean current = country ? selected.country().equals(code) : selected.language().equals(code);
            String label = country
                    ? (plugin.settings().bool("flags.resource-pack.use-glyphs", false)
                        ? "<font:betterchat:flags>" + CountryCatalog.glyph(code) + "</font> "
                        : CountryCatalog.flag(code) + " ") + option.getValue()
                    : option.getValue();
            inventory.setItem(index, item(country ? Material.WHITE_BANNER : Material.PAPER,
                    (current ? "<green>✓ " : "<white>") + label, "<gray>Code: " + code));
        }
        if (currentPage > 0) inventory.setItem(45, item(Material.ARROW, "<yellow>Previous page", "<gray>Go to page " + currentPage));
        inventory.setItem(49, item(Material.BARRIER, "<red>Back", "<gray>Return to BetterChat settings."));
        if (currentPage + 1 < pages) inventory.setItem(53, item(Material.ARROW, "<yellow>Next page", "<gray>Go to page " + (currentPage + 2)));
        if (country) {
            inventory.setItem(47, item(selected.automaticCountry() ? Material.LIME_DYE : Material.GRAY_DYE,
                    selected.automaticCountry() ? "<green>Automatic country: on" : "<gray>Automatic country: off",
                    "<gray>Click to toggle IP based country lookup."));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (holder.type == MenuType.MAIN) {
            if (slot == 11) openLanguages(player, 0);
            else if (slot == 15) openCountries(player, 0);
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot == 45) {
            openPagedByType(player, holder.type, holder.page - 1);
            return;
        }
        if (slot == 53) {
            openPagedByType(player, holder.type, holder.page + 1);
            return;
        }
        PlayerPreferences current = plugin.preferences().get(player.getUniqueId());
        if (holder.type == MenuType.COUNTRIES && slot == 47) {
            boolean auto = !current.automaticCountry();
            plugin.setAutomaticCountry(player, auto);
            player.sendMessage(Component.text("Automatic country lookup " + (auto ? "enabled" : "disabled") + "."));
            openCountries(player, holder.page);
            return;
        }
        if (slot >= PAGE_SIZE) return;
        List<String> options = holder.type == MenuType.LANGUAGES
                ? LanguageCatalog.all().keySet().stream().filter(code -> !isBlacklisted("language.blacklist", code)).toList()
                : CountryCatalog.all().keySet().stream().filter(code -> !isBlacklisted("flags.blacklist", code)).toList();
        int optionIndex = holder.page * PAGE_SIZE + slot;
        if (optionIndex >= options.size()) return;
        String code = options.get(optionIndex);
        if (holder.type == MenuType.LANGUAGES) {
            plugin.updatePreferences(current.withLanguage(code));
            plugin.refreshPlayer(player);
            player.sendMessage(Component.text("Your chat language is now " + LanguageCatalog.all().get(code) + "."));
        } else {
            plugin.updatePreferences(current.withCountry(code, false));
            plugin.refreshPlayer(player);
            player.sendMessage(Component.text("Your country flag is now " + CountryCatalog.flag(code) + " " + CountryCatalog.all().get(code) + "."));
        }
        player.closeInventory();
    }

    private void openPagedByType(Player player, MenuType type, int page) {
        if (type == MenuType.LANGUAGES) openLanguages(player, page);
        else openCountries(player, page);
    }

    private boolean isBlacklisted(String path, String code) {
        Set<String> denied = plugin.settings().strings(path).stream()
                .map(value -> path.equals("language.blacklist") ? LanguageCatalog.resolve(value) : value)
                .filter(java.util.Objects::nonNull).map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return denied.contains(code.toLowerCase(Locale.ROOT));
    }

    private static ItemStack item(Material material, String title, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(title));
        meta.lore(List.of(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(lore)));
        item.setItemMeta(meta);
        return item;
    }

    private enum MenuType { MAIN, LANGUAGES, COUNTRIES }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final int page;
        private Inventory inventory;
        private MenuHolder(MenuType type, int page) { this.type = type; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
