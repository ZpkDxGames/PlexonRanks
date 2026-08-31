package com.zpkdxgames.plexonranks.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsHook {
    private final LuckPerms luckPerms;

    public LuckPermsHook(JavaPlugin plugin) {
        RegisteredServiceProvider<LuckPerms> registration = plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        this.luckPerms = registration == null ? null : registration.getProvider();
    }

    public boolean connected() {
        return luckPerms != null;
    }

    public CompletableFuture<Void> addPermissions(UUID uuid, Collection<String> permissions) {
        if (luckPerms == null || permissions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            for (String permission : permissions) {
                if (!permission.isBlank()) {
                    user.data().add(PermissionNode.builder(permission).value(true).build());
                }
            }
        }).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> addGroup(UUID uuid, String groupName, String mode) {
        if (luckPerms == null || groupName.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("LuckPerms group does not exist: " + groupName));
        }
        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            Node node = InheritanceNode.builder(group.getName()).build();
            user.data().add(node);
            if ("SET_PRIMARY".equalsIgnoreCase(mode)) {
                user.setPrimaryGroup(group.getName());
            }
        }).thenApply(ignored -> null);
    }

    public String primaryGroup(UUID uuid) {
        if (luckPerms == null) {
            return "";
        }
        var user = luckPerms.getUserManager().getUser(uuid);
        return user == null ? "" : user.getPrimaryGroup();
    }
}
