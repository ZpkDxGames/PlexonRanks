package com.zpkdxgames.plexonranks.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        return applyPersistentGrants(uuid, permissions, List.of());
    }

    public CompletableFuture<Void> addGroup(UUID uuid, String groupName, String mode) {
        if (groupName.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return applyPersistentGrants(uuid, List.of(), List.of(new GroupGrant(groupName, mode)));
    }

    public CompletableFuture<Void> applyPersistentGrants(UUID uuid, Collection<String> permissions,
                                                          Collection<GroupGrant> groups) {
        if (luckPerms == null || (permissions.isEmpty() && groups.isEmpty())) {
            return CompletableFuture.completedFuture(null);
        }

        List<ResolvedGroupGrant> resolvedGroups = new ArrayList<>();
        for (GroupGrant grant : groups) {
            if (grant.group().isBlank()) {
                continue;
            }
            Group group = luckPerms.getGroupManager().getGroup(grant.group());
            if (group == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("LuckPerms group does not exist: " + grant.group()));
            }
            resolvedGroups.add(new ResolvedGroupGrant(group, grant.mode()));
        }

        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            for (String permission : permissions) {
                if (!permission.isBlank()) {
                    user.data().add(PermissionNode.builder(permission).value(true).build());
                }
            }
            for (ResolvedGroupGrant grant : resolvedGroups) {
                Node node = InheritanceNode.builder(grant.group().getName()).build();
                user.data().add(node);
                if ("SET_PRIMARY".equalsIgnoreCase(grant.mode())) {
                    user.setPrimaryGroup(grant.group().getName());
                }
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

    public record GroupGrant(String group, String mode) {
        public GroupGrant {
            group = group == null ? "" : group.trim();
            mode = mode == null || mode.isBlank() ? "ADD" : mode.trim();
        }
    }

    private record ResolvedGroupGrant(Group group, String mode) {
    }
}
