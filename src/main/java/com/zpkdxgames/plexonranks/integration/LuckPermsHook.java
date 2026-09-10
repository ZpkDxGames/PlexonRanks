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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    public boolean groupExists(String groupName) {
        return luckPerms != null && groupName != null && !groupName.isBlank()
                && luckPerms.getGroupManager().getGroup(groupName) != null;
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
        if (luckPerms == null) {
            if (permissions.isEmpty() && groups.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms is unavailable"));
        }
        List<ResolvedGroupGrant> resolvedGroups = resolveGroups(groups);
        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            for (String permission : permissions) {
                if (!permission.isBlank()) {
                    user.data().add(PermissionNode.builder(permission).value(true).build());
                }
            }
            for (ResolvedGroupGrant grant : resolvedGroups) {
                user.data().add(InheritanceNode.builder(grant.group().getName()).build());
                if ("SET_PRIMARY".equalsIgnoreCase(grant.mode())) {
                    user.setPrimaryGroup(grant.group().getName());
                }
            }
        }).thenApply(ignored -> null);
    }

    /** Reconciles only nodes managed by PlexonRanks; unrelated LuckPerms state is never removed. */
    public CompletableFuture<Void> reconcileManagedGrants(
            UUID uuid,
            Collection<String> desiredPermissions,
            Collection<GroupGrant> desiredGroups,
            Collection<String> managedPermissions,
            Collection<String> managedGroups
    ) {
        if (luckPerms == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms is unavailable"));
        }
        Set<String> desiredPermissionSet = Set.copyOf(desiredPermissions);
        Set<String> managedPermissionSet = Set.copyOf(managedPermissions);
        Set<String> managedGroupSet = new HashSet<>(managedGroups);
        List<ResolvedGroupGrant> resolvedGroups = resolveGroups(desiredGroups);
        Set<String> desiredGroupSet = new HashSet<>();
        for (ResolvedGroupGrant group : resolvedGroups) {
            desiredGroupSet.add(group.group().getName());
        }

        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            for (Node node : new ArrayList<>(user.data().toCollection())) {
                if (node instanceof PermissionNode permission) {
                    if (managedPermissionSet.contains(permission.getPermission())
                            && !desiredPermissionSet.contains(permission.getPermission())) {
                        user.data().remove(node);
                    }
                } else if (node instanceof InheritanceNode inheritance) {
                    if (managedGroupSet.contains(inheritance.getGroupName())
                            && !desiredGroupSet.contains(inheritance.getGroupName())) {
                        user.data().remove(node);
                    }
                }
            }
            for (String permission : desiredPermissionSet) {
                if (!permission.isBlank()) {
                    user.data().add(PermissionNode.builder(permission).value(true).build());
                }
            }
            for (ResolvedGroupGrant grant : resolvedGroups) {
                user.data().add(InheritanceNode.builder(grant.group().getName()).build());
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

    private List<ResolvedGroupGrant> resolveGroups(Collection<GroupGrant> groups) {
        List<ResolvedGroupGrant> resolved = new ArrayList<>();
        for (GroupGrant grant : groups) {
            if (grant.group().isBlank()) {
                continue;
            }
            Group group = luckPerms.getGroupManager().getGroup(grant.group());
            if (group == null) {
                throw new IllegalArgumentException("LuckPerms group does not exist: " + grant.group());
            }
            resolved.add(new ResolvedGroupGrant(group, grant.mode()));
        }
        return List.copyOf(resolved);
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
