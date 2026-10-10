package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.model.CountryCatalog;
import com.tis199.betterchat.common.model.LanguageCatalog;
import com.tis199.betterchat.common.model.PlayerPreferences;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class BetterChatMenu implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final Map<String, String> REPRESENTATIVE_COUNTRIES = Map.ofEntries(
            Map.entry("af", "ZA"), Map.entry("am", "ET"), Map.entry("ar", "SA"),
            Map.entry("bn", "BD"), Map.entry("de", "DE"), Map.entry("el", "GR"),
            Map.entry("en", "GB"), Map.entry("es", "ES"), Map.entry("fa", "IR"),
            Map.entry("fr", "FR"), Map.entry("he", "IL"), Map.entry("hi", "IN"),
            Map.entry("id", "ID"), Map.entry("it", "IT"), Map.entry("ja", "JP"),
            Map.entry("ko", "KR"), Map.entry("ms", "MY"), Map.entry("nl", "NL"),
            Map.entry("pl", "PL"), Map.entry("pt", "PT"), Map.entry("ru", "RU"),
            Map.entry("sv", "SE"), Map.entry("sw", "TZ"), Map.entry("th", "TH"),
            Map.entry("tr", "TR"), Map.entry("uk", "UA"), Map.entry("ur", "PK"),
            Map.entry("vi", "VN"), Map.entry("zh", "CN")
    );
    private final BetterChatPaperPlugin plugin;
    private final Map<UUID, SearchRequest> pendingSearch = new ConcurrentHashMap<>();

    BetterChatMenu(BetterChatPaperPlugin plugin) { this.plugin = plugin; }

    void openMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.MAIN, 0, "");
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("BetterChat settings"));
        holder.inventory = inventory;
        inventory.setItem(11, item(Material.WRITABLE_BOOK, "<gold>Choose language", "<gray>Chat is translated into this language. Use /bc language <name> for custom languages."));
        inventory.setItem(15, item(Material.WHITE_BANNER, "<gold>Choose flag", "<gray>Set your displayed country or enable automatic lookup."));
        player.openInventory(inventory);
    }

    private void openLanguages(Player player, int page) {
        openLanguages(player, page, "");
    }

    private void openLanguages(Player player, int page, String query) {
        openPaged(player, MenuType.LANGUAGES, page, query);
    }

    private void openCountries(Player player, int page) {
        openCountries(player, page, "");
    }

    private void openCountries(Player player, int page, String query) {
        openPaged(player, MenuType.COUNTRIES, page, query);
    }

    private void openPaged(Player player, MenuType type, int page, String query) {
        List<Map.Entry<String, String>> entries = options(type, query);
        boolean country = type == MenuType.COUNTRIES;
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder(type, currentPage, query);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text((country ? "Choose country flag" : "Choose language") + " — " + (currentPage + 1) + "/" + pages));
        holder.inventory = inventory;
        int start = currentPage * PAGE_SIZE;
        PlayerPreferences selected = plugin.preferences().get(player.getUniqueId());
        for (int index = 0; index < PAGE_SIZE && start + index < entries.size(); index++) {
            Map.Entry<String, String> option = entries.get(start + index);
            String code = option.getKey();
            boolean current = country ? selected.country().equals(code) : selected.language().equals(code);
            String label = (country ? flagMarkup(code) : languageFlagMarkup(code)) + " " + option.getValue();
            inventory.setItem(index, item(country ? Material.WHITE_BANNER : Material.PAPER,
                    (current ? "<green>✓ " : "<white>") + label, "<gray>Code: " + code));
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, "<red>No matches", "<gray>Try a shorter name or code."));
        }
        if (currentPage > 0) inventory.setItem(45, item(Material.ARROW, "<yellow>Previous page", "<gray>Go to page " + currentPage));
        inventory.setItem(46, item(Material.COMPASS, "<yellow>Search", "<gray>Click, then type a name or code in chat."));
        inventory.setItem(49, item(Material.BARRIER, "<red>Back", "<gray>Return to BetterChat settings."));
        if (!query.isBlank()) inventory.setItem(51, item(Material.MILK_BUCKET, "<yellow>Clear search", "<gray>Show all options again."));
        if (currentPage + 1 < pages) inventory.setItem(53, item(Material.ARROW, "<yellow>Next page", "<gray>Go to page " + (currentPage + 2)));
        if (country) {
            inventory.setItem(47, item(selected.automaticCountry() ? Material.LIME_DYE : Material.GRAY_DYE,
                    selected.automaticCountry() ? "<green>Automatic country: on" : "<gray>Automatic country: off",
                    "<gray>Click to toggle IP based country lookup."));
        }
        player.openInventory(inventory);
    }

    private List<Map.Entry<String, String>> options(MenuType type, String query) {
        String path = type == MenuType.LANGUAGES ? "language.blacklist" : "flags.blacklist";
        Stream<Map.Entry<String, String>> entries = (type == MenuType.LANGUAGES
                ? LanguageCatalog.all() : CountryCatalog.all()).entrySet().stream()
                .filter(entry -> !isBlacklisted(path, entry.getKey()));
        if (type == MenuType.COUNTRIES) {
            entries = entries.sorted(Comparator
                    .comparing((Map.Entry<String, String> entry) -> !entry.getKey().equals("EARTH"))
                    .thenComparing(Map.Entry::getKey));
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return entries.toList();
        String resolvedLanguage = type == MenuType.LANGUAGES ? LanguageCatalog.resolve(needle) : null;
        return entries.filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.getValue().toLowerCase(Locale.ROOT).contains(needle)
                        || entry.getKey().equalsIgnoreCase(resolvedLanguage)
                        || (type == MenuType.COUNTRIES && entry.getKey().equals("EARTH")
                            && Set.of("earth", "global", "world").contains(needle)))
                .toList();
    }

    private String languageFlagMarkup(String language) {
        String normalized = PlayerPreferences.normalizeLanguage(language);
        String[] parts = normalized.split("_", 2);
        String country = parts.length == 2 && parts[1].matches("[a-z]{2}")
                ? parts[1].toUpperCase(Locale.ROOT)
                : REPRESENTATIVE_COUNTRIES.get(normalized.split("_", 2)[0]);
        return country == null || !CountryCatalog.contains(country) ? "🌐" : flagMarkup(country);
    }

    private String flagMarkup(String country) {
        return plugin.settings().bool("flags.resource-pack.use-glyphs", false)
                ? "<font:betterchat:flags>" + CountryCatalog.glyph(country) + "</font>"
                : CountryCatalog.flag(country);
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
            openPagedByType(player, holder.type, holder.page - 1, holder.query);
            return;
        }
        if (slot == 46) {
            pendingSearch.put(player.getUniqueId(), new SearchRequest(holder.type, holder.query));
            player.closeInventory();
            player.sendMessage(Component.text("Type a name or code to search, or type 'cancel'."));
            return;
        }
        if (slot == 51 && !holder.query.isBlank()) {
            openPagedByType(player, holder.type, 0, "");
            return;
        }
        if (slot == 53) {
            openPagedByType(player, holder.type, holder.page + 1, holder.query);
            return;
        }
        PlayerPreferences current = plugin.preferences().get(player.getUniqueId());
        if (holder.type == MenuType.COUNTRIES && slot == 47) {
            boolean auto = !current.automaticCountry();
            plugin.setAutomaticCountry(player, auto);
            player.sendMessage(Component.text("Automatic country lookup " + (auto ? "enabled" : "disabled") + "."));
            openCountries(player, holder.page, holder.query);
            return;
        }
        if (slot >= PAGE_SIZE) return;
        List<String> options = options(holder.type, holder.query).stream().map(Map.Entry::getKey).toList();
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

    private void openPagedByType(Player player, MenuType type, int page, String query) {
        openPaged(player, type, page, query);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSearchChat(AsyncChatEvent event) {
        SearchRequest request = pendingSearch.remove(event.getPlayer().getUniqueId());
        if (request == null) return;
        event.setCancelled(true);
        String query = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) return;
            if (query.equalsIgnoreCase("cancel") || query.isEmpty()) {
                player.sendMessage(Component.text("Search cancelled."));
                openPagedByType(player, request.type(), 0, request.previousQuery());
                return;
            }
            openPagedByType(player, request.type(), 0, query);
        }, null);
    }

    @EventHandler
    public void onSearchPlayerQuit(PlayerQuitEvent event) {
        pendingSearch.remove(event.getPlayer().getUniqueId());
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

    private record SearchRequest(MenuType type, String previousQuery) { }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final int page;
        private final String query;
        private Inventory inventory;
        private MenuHolder(MenuType type, int page, String query) {
            this.type = type;
            this.page = page;
            this.query = query;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
