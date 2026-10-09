package com.tis199.betterchat.paper;

import com.tis199.betterchat.common.config.YamlConfig;
import com.tis199.betterchat.common.model.PlayerPreferences;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Keeps BetterChat-owned LuckPerms parent groups in sync with a player's selected settings. */
final class GroupAssignmentService {
    private final BetterChatPaperPlugin plugin;

    GroupAssignmentService(BetterChatPaperPlugin plugin) {
        this.plugin = plugin;
    }

    void apply(PlayerPreferences preferences) {
        YamlConfig config = plugin.settings();
        if (!config.bool("groups.enabled", false)) return;
        String prefix = config.string("groups.group-prefix", "betterchat_").toLowerCase(Locale.ROOT);
        List<String> desired = new ArrayList<>(2);
        if (config.bool("groups.nationality-groups", false) && !preferences.country().equals("ZZ"))
            desired.add(prefix + "country_" + preferences.country().toLowerCase(Locale.ROOT));
        if (config.bool("groups.language-groups", false)) {
            String languageGroup = preferences.language().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (languageGroup.isBlank())
                languageGroup = "custom_" + Integer.toUnsignedString(preferences.language().hashCode(), 36);
            desired.add(prefix + "language_" + languageGroup);
        }
        if (desired.isEmpty()) return;
        String provider = config.string("groups.provider", "luckperms");
        if (provider.equalsIgnoreCase("vault")) {
            applyVault(preferences, prefix, desired);
            return;
        }
        if (!provider.equalsIgnoreCase("luckperms")) {
            plugin.getLogger().warning("groups.provider must be luckperms or vault.");
            return;
        }
        LuckPerms luckPerms = LuckPermsProvider.get();
        CompletableFuture<?>[] groupsReady = desired.stream().map(name -> luckPerms.getGroupManager().loadGroup(name)
                .thenCompose(group -> group.isPresent() ? CompletableFuture.completedFuture(group.get())
                        : luckPerms.getGroupManager().createAndLoadGroup(name))).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(groupsReady).thenCompose(ignored -> luckPerms.getUserManager().modifyUser(
                preferences.uniqueId(), user -> {
                    user.data().clear(NodeType.INHERITANCE.predicate(node -> node.getGroupName().startsWith(prefix)));
                    desired.forEach(name -> user.data().add(InheritanceNode.builder(name).build()));
                })).exceptionally(error -> {
            plugin.getLogger().warning("Could not update LuckPerms groups for " + preferences.uniqueId() + ": " + error.getMessage());
            return null;
        });
    }

    private void applyVault(PlayerPreferences preferences, String prefix, List<String> desired) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            org.bukkit.entity.Player online = Bukkit.getPlayer(preferences.uniqueId());
            if (online == null) applyVaultNow(preferences, prefix, desired);
            else online.getScheduler().run(plugin,
                    task -> applyVaultNow(preferences, prefix, desired), null);
        });
    }

    private void applyVaultNow(PlayerPreferences preferences, String prefix, List<String> desired) {
        try {
            Class<?> permissionType = plugin.getClass().getClassLoader().loadClass("net.milkbowl.vault.permission.Permission");
            Object registration = Bukkit.getServicesManager().getClass()
                    .getMethod("getRegistration", Class.class).invoke(Bukkit.getServicesManager(), permissionType);
            if (registration == null) throw new IllegalStateException("Vault has no active permission provider");
            Object permission = registration.getClass().getMethod("getProvider").invoke(registration);
            OfflinePlayer player = Bukkit.getOfflinePlayer(preferences.uniqueId());
            Method groupList = Arrays.stream(permissionType.getMethods())
                    .filter(method -> method.getName().equals("getPlayerGroups") && method.getParameterCount() == 2)
                    .findFirst().orElseThrow(() -> new NoSuchMethodException("Vault getPlayerGroups"));
            Object[] groupArgs = vaultArgs(groupList, player, null);
            Object groupsResult = groupList.invoke(permission, groupArgs);
            Collection<?> current = groupsResult instanceof Collection<?> collection ? collection
                    : groupsResult instanceof String[] array ? Arrays.asList(array) : List.of();
            for (Object group : current) {
                String name = String.valueOf(group);
                if (!name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) continue;
                invokeVaultGroup(permissionType, permission, "playerRemoveGroup", player, name);
            }
            for (String name : desired) invokeVaultGroup(permissionType, permission, "playerAddGroup", player, name);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Vault group assignment is enabled, but no Vault permission provider with group support is available: " + exception.getMessage());
        }
    }

    private static void invokeVaultGroup(Class<?> type, Object permission, String methodName,
                                         OfflinePlayer player, String group) throws ReflectiveOperationException {
        Method method = Arrays.stream(type.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName) && candidate.getParameterCount() == 3)
                .filter(candidate -> candidate.getParameterTypes()[0] == String.class)
                .filter(candidate -> candidate.getParameterTypes()[1] == String.class
                        || candidate.getParameterTypes()[1].isAssignableFrom(player.getClass())
                        || candidate.getParameterTypes()[1].isAssignableFrom(OfflinePlayer.class))
                .filter(candidate -> candidate.getParameterTypes()[2] == String.class)
                .findFirst().orElseThrow(() -> new NoSuchMethodException("Vault " + methodName));
        Object target = method.getParameterTypes()[1] == String.class ? player.getName() : player;
        method.invoke(permission, null, target, group);
    }

    private static Object[] vaultArgs(Method method, OfflinePlayer player, String group) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        args[0] = null;
        args[1] = types[1] == String.class ? player.getName() : player;
        return args;
    }
}
