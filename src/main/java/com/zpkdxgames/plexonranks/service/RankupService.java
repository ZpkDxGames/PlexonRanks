package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.config.RuntimeSettings;
import com.zpkdxgames.plexonranks.database.DatabaseManager;
import com.zpkdxgames.plexonranks.event.PlexonRankChangeEvent;
import com.zpkdxgames.plexonranks.event.PlexonRankPreRankupEvent;
import com.zpkdxgames.plexonranks.event.PlexonRankupEvent;
import com.zpkdxgames.plexonranks.event.RankChangeCause;
import com.zpkdxgames.plexonranks.integration.DiscordSrvHook;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.requirement.Consumption;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import com.zpkdxgames.plexonranks.requirement.RequirementPlan;
import com.zpkdxgames.plexonranks.reward.RewardEngine;
import com.zpkdxgames.plexonranks.util.NumberFormats;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class RankupService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final DatabaseManager database;
    private final RankService ranks;
    private final RequirementEngine requirements;
    private final RewardEngine rewards;
    private final RenderService render;
    private final MessageService messages;
    private final DiscordSrvHook discord;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastAttemptNanos = new ConcurrentHashMap<>();

    public RankupService(JavaPlugin plugin, ConfigManager configs, DatabaseManager database, RankService ranks,
                         RequirementEngine requirements, RewardEngine rewards, RenderService render,
                         MessageService messages, DiscordSrvHook discord) {
        this.plugin = plugin;
        this.configs = configs;
        this.database = database;
        this.ranks = ranks;
        this.requirements = requirements;
        this.rewards = rewards;
        this.render = render;
        this.messages = messages;
        this.discord = discord;
    }

    public void attempt(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> attempt(player));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!ranks.loaded(uuid)) {
            ranks.load(uuid);
            messages.send(player, "generic.data-loading");
            return;
        }

        RuntimeSettings settings = configs.current().settings();
        long now = System.nanoTime();
        long cooldownNanos = TimeUnit.MILLISECONDS.toNanos(settings.rankupCooldownMillis());
        long elapsed = now - lastAttemptNanos.getOrDefault(uuid, now - cooldownNanos);
        long remainingNanos = cooldownNanos - elapsed;
        if (remainingNanos > 0) {
            messages.send(player, "rankup.cooldown", Map.of("seconds",
                    NumberFormats.number(remainingNanos / 1_000_000_000.0)));
            return;
        }
        if (!processing.add(uuid)) {
            messages.send(player, "rankup.already-processing");
            return;
        }
        lastAttemptNanos.put(uuid, now);

        try {
            begin(player, settings);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Rank-up preflight failed for " + player.getName() + ": " + rootMessage(exception));
            messages.send(player, "rankup.reward-error");
            processing.remove(uuid);
        }
    }

    private void begin(Player player, RuntimeSettings settings) {
        UUID uuid = player.getUniqueId();
        Rank current = ranks.current(uuid).orElse(configs.current().registry().defaultRank());
        Optional<Rank> next = configs.current().registry().nextAccessible(current, player::hasPermission);
        if (next.isEmpty()) {
            messages.send(player, "rankup.max-rank");
            processing.remove(uuid);
            return;
        }
        Rank target = next.get();
        List<RequirementProgress> precheck = requirements.evaluate(player, target.requirements());
        if (precheck.stream().anyMatch(value -> !value.complete())) {
            messages.send(player, "rankup.requirements-not-met",
                    render.placeholders(player, current, target, precheck, RankState.NEXT));
            playConfiguredSound(player, settings.deniedSound());
            processing.remove(uuid);
            return;
        }

        PlexonRankPreRankupEvent preEvent = new PlexonRankPreRankupEvent(player, current, target);
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            messages.send(player, "rankup.cancelled");
            processing.remove(uuid);
            return;
        }

        RequirementPlan plan = requirements.plan(player, target.requirements());
        if (!plan.complete()) {
            messages.send(player, "rankup.requirements-not-met",
                    render.placeholders(player, current, target, plan.progress(), RankState.NEXT));
            playConfiguredSound(player, settings.deniedSound());
            processing.remove(uuid);
            return;
        }

        Map<String, String> placeholders = render.placeholders(player, current, target, plan.progress(), RankState.NEXT);
        rewards.preflight(player, target, placeholders);

        List<Consumption> consumed;
        try {
            consumed = requirements.consume(player, plan);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Rank-up consumption failed for " + player.getName() + ": " + rootMessage(exception));
            List<RequirementProgress> refreshed = requirements.evaluate(player, target.requirements());
            messages.send(player, "rankup.requirements-not-met",
                    render.placeholders(player, current, target, refreshed, RankState.NEXT));
            processing.remove(uuid);
            return;
        }

        String transactionId = UUID.randomUUID().toString();
        database.commitRankup(uuid, current.id(), target.id(), transactionId).whenComplete((committed, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || !Boolean.TRUE.equals(committed)) {
                        rollbackRequirements(consumed, transactionId);
                        plugin.getLogger().severe("Rank transaction " + transactionId + " failed before commit for "
                                + player.getName() + ": " + (error == null ? "stale rank state" : rootMessage(error)));
                        messages.send(player, "generic.database-error");
                        processing.remove(uuid);
                        return;
                    }
                    ranks.acceptCommitted(uuid, target);
                    reconcileTarget(player, current, target, transactionId, consumed, plan.progress(), placeholders);
                }));
    }

    private void reconcileTarget(Player player, Rank current, Rank target, String transactionId,
                                 List<Consumption> consumed, List<RequirementProgress> progress,
                                 Map<String, String> placeholders) {
        rewards.reconcile(player.getUniqueId(), target, configs.current().registry(),
                configs.current().settings().cumulativePermissions()).whenComplete((ignored, projectionError) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (projectionError != null) {
                        compensate(player, current, target, transactionId, consumed, null,
                                "ROLLED_BACK_PROJECTION_FAILURE", projectionError);
                        return;
                    }
                    RewardEngine.ReversibleRewardBatch batch;
                    try {
                        batch = rewards.executeReversible(player, target, placeholders);
                    } catch (RuntimeException rewardError) {
                        compensate(player, current, target, transactionId, consumed, null,
                                "ROLLED_BACK_REWARD_FAILURE", rewardError);
                        return;
                    }
                    finalizeExternalBoundary(player, current, target, transactionId, consumed, batch, progress, placeholders);
                }));
    }

    private void finalizeExternalBoundary(Player player, Rank current, Rank target, String transactionId,
                                          List<Consumption> consumed, RewardEngine.ReversibleRewardBatch batch,
                                          List<RequirementProgress> progress, Map<String, String> placeholders) {
        Throwable commandError = null;
        try {
            rewards.executeCommands(player, target, placeholders);
        } catch (RuntimeException exception) {
            commandError = exception;
        }

        if (commandError != null) {
            Throwable finalCommandError = commandError;
            database.markTransactionFailed(transactionId, "EXTERNAL_REWARD_FAILED", rootMessage(commandError))
                    .whenComplete((ignored, statusError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        Throwable reported = statusError == null ? finalCommandError : statusError;
                        if (statusError != null) {
                            plugin.getLogger().severe("CRITICAL: could not persist external reward failure for transaction "
                                    + transactionId + ": " + rootMessage(statusError));
                        }
                        finishCommitted(player, current, target, transactionId, placeholders, reported, false);
                    }));
            return;
        }

        database.completeTransaction(transactionId).whenComplete((ignored, statusError) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (statusError != null) {
                        plugin.getLogger().severe("CRITICAL: rank " + target.id() + " and rewards committed but transaction "
                                + transactionId + " final status could not be persisted: " + rootMessage(statusError));
                        finishCommitted(player, current, target, transactionId, placeholders, statusError, false);
                        return;
                    }
                    finishCommitted(player, current, target, transactionId, placeholders, null, true);
                }));
    }

    private void compensate(Player player, Rank current, Rank target, String transactionId,
                            List<Consumption> consumed, RewardEngine.ReversibleRewardBatch batch,
                            String status, Throwable cause) {
        UUID uuid = player.getUniqueId();
        database.rollbackRankup(uuid, target.id(), current.id(), transactionId, status, rootMessage(cause))
                .whenComplete((rolledBack, databaseError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (databaseError != null || !Boolean.TRUE.equals(rolledBack)) {
                        plugin.getLogger().severe("CRITICAL: failed to compensate authoritative rank transaction "
                                + transactionId + "; target rank remains authoritative: "
                                + (databaseError == null ? "compare-and-set rejected" : rootMessage(databaseError)));
                        messages.send(player, "generic.database-error");
                        processing.remove(uuid);
                        return;
                    }
                    ranks.acceptCommitted(uuid, current);
                    List<Throwable> rollbackFailures = new ArrayList<>();
                    if (batch != null) {
                        try {
                            batch.rollback();
                        } catch (RuntimeException failure) {
                            rollbackFailures.add(failure);
                        }
                    }
                    try {
                        rollbackRequirementsChecked(consumed);
                    } catch (RuntimeException failure) {
                        rollbackFailures.add(failure);
                    }
                    rewards.reconcile(uuid, current, configs.current().registry(),
                                    configs.current().settings().cumulativePermissions())
                            .whenComplete((ignored, projectionRollbackError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (projectionRollbackError != null) rollbackFailures.add(projectionRollbackError);
                                if (!rollbackFailures.isEmpty()) {
                                    plugin.getLogger().severe("CRITICAL: rank transaction " + transactionId
                                            + " restored authoritative rank but one or more custody/projection rollbacks failed: "
                                            + rootMessage(rollbackFailures.getFirst()));
                                    messages.send(player, "generic.database-error");
                                } else {
                                    plugin.getLogger().warning("Rank transaction " + transactionId + " was safely rolled back: "
                                            + rootMessage(cause));
                                    messages.send(player, "rankup.reward-error");
                                }
                                processing.remove(uuid);
                            }));
                }));
    }

    private void finishCommitted(Player player, Rank from, Rank to, String transactionId,
                                 Map<String, String> placeholders, Throwable completionError, boolean fullSuccess) {
        try {
            Bukkit.getPluginManager().callEvent(new PlexonRankChangeEvent(player, from, to, RankChangeCause.RANKUP));
            if (fullSuccess) {
                feedback(player, to, placeholders);
            } else {
                plugin.getLogger().severe("Rank transaction " + transactionId
                        + " crossed the irreversible/committed boundary with a reported failure: "
                        + rootMessage(completionError));
                messages.send(player, "rankup.reward-error");
            }
            Bukkit.getPluginManager().callEvent(new PlexonRankupEvent(player, from, to, transactionId));
        } finally {
            processing.remove(player.getUniqueId());
        }
    }

    public boolean processing(UUID uuid) {
        return processing.contains(uuid);
    }

    public void clearPlayerState(UUID uuid) {
        processing.remove(uuid);
        lastAttemptNanos.remove(uuid);
    }

    private void rollbackRequirements(List<Consumption> consumed, String transactionId) {
        try {
            rollbackRequirementsChecked(consumed);
        } catch (RuntimeException rollbackFailure) {
            plugin.getLogger().severe("CRITICAL: requirement refund failed for uncommitted transaction " + transactionId
                    + ": " + rootMessage(rollbackFailure));
        }
    }

    private static void rollbackRequirementsChecked(List<Consumption> consumed) {
        List<Consumption> reversed = new ArrayList<>(consumed);
        Collections.reverse(reversed);
        RuntimeException combined = null;
        for (Consumption consumption : reversed) {
            try {
                consumption.rollback().run();
            } catch (RuntimeException failure) {
                if (combined == null) combined = new IllegalStateException("One or more requirement refunds failed");
                combined.addSuppressed(failure);
            }
        }
        if (combined != null) throw combined;
    }

    private void feedback(Player player, Rank rank, Map<String, String> placeholders) {
        RuntimeSettings settings = configs.current().settings();
        RuntimeSettings.Feedback feedback = settings.feedback();
        if (player.isOnline() && feedback.chat()) {
            messages.send(player, "rankup.success", placeholders);
        }
        if (player.isOnline() && feedback.title()) {
            player.showTitle(Title.title(
                    messages.component("rankup.title", placeholders),
                    messages.component("rankup.subtitle", placeholders),
                    Title.Times.times(Duration.ofMillis(350), Duration.ofSeconds(3), Duration.ofMillis(600))
            ));
        }
        if (player.isOnline() && feedback.sound()) {
            playConfiguredSound(player, settings.rankupSound());
        }
        if (rank.announce() && settings.broadcastEnabled() && feedback.broadcast()) {
            Bukkit.getServer().sendMessage(messages.component("rankup.broadcast", placeholders));
        }
        if (discord.connected()) {
            String message = TextFormatter.replaceRaw(settings.discordMessage(), placeholders);
            discord.send(settings.discordChannel(), configs.formatter().plain(configs.formatter().component(message)));
        }
    }

    private void playConfiguredSound(Player player, RuntimeSettings.SoundDescriptor descriptor) {
        if (descriptor.sound().isBlank()) return;
        String sound = descriptor.sound();
        try {
            player.playSound(player.getLocation(), sound.toLowerCase().contains(":") ? sound.toLowerCase()
                    : "minecraft:" + sound.toLowerCase(), descriptor.volume(), descriptor.pitch());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid configured sound " + sound);
        }
    }

    private static String rootMessage(Throwable throwable) {
        if (throwable == null) return "unknown failure";
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
