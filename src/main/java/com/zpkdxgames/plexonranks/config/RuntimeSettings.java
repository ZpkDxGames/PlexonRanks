package com.zpkdxgames.plexonranks.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

public record RuntimeSettings(
        boolean miniMessage,
        boolean legacyAmpersandSupport,
        long rankupCooldownMillis,
        boolean rightClickRankup,
        boolean cumulativePermissions,
        boolean reconcileOnJoin,
        String missingRankFallback,
        String databaseFile,
        int databaseQueueCapacity,
        boolean placeholderApiEnabled,
        int placeholderCacheTicks,
        boolean discordSrvEnabled,
        boolean broadcastEnabled,
        Feedback feedback,
        SoundDescriptor rankupSound,
        SoundDescriptor deniedSound,
        String discordChannel,
        String discordMessage,
        int backupRetention,
        RankMenu rankMenu
) {
    public RuntimeSettings {
        rankupCooldownMillis = Math.max(0L, rankupCooldownMillis);
        databaseFile = databaseFile == null || databaseFile.isBlank() ? "database.db" : databaseFile.trim();
        databaseQueueCapacity = clamp(databaseQueueCapacity, 8, 65_536);
        placeholderCacheTicks = clamp(placeholderCacheTicks, 1, 20);
        missingRankFallback = missingRankFallback == null || missingRankFallback.isBlank()
                ? "FIRST" : missingRankFallback.trim().toUpperCase(java.util.Locale.ROOT);
        discordChannel = discordChannel == null || discordChannel.isBlank() ? "global" : discordChannel.trim();
        discordMessage = discordMessage == null ? "" : discordMessage;
        backupRetention = clamp(backupRetention, 1, 1000);
    }

    public static RuntimeSettings parse(YamlConfiguration config, YamlConfiguration menus) {
        return new RuntimeSettings(
                config.getBoolean("formatting.minimessage", true),
                config.getBoolean("formatting.legacy-ampersand-support", true),
                config.getLong("rankup.cooldown-ms", 750L),
                config.getBoolean("rankup.right-click-next-rank", true),
                config.getBoolean("permissions.cumulative", true),
                config.getBoolean("permissions.reconcile-on-join", true),
                config.getString("join.missing-rank-fallback", "FIRST"),
                config.getString("storage.sqlite.file", "database.db"),
                config.getInt("performance.database.queue-capacity", 1024),
                config.getBoolean("integrations.placeholderapi", true),
                config.getInt("performance.placeholder-cache-ticks", 4),
                config.getBoolean("integrations.discordsrv", false),
                config.getBoolean("broadcast.enabled", true),
                new Feedback(
                        config.getBoolean("feedback.chat", true),
                        config.getBoolean("feedback.title", true),
                        config.getBoolean("feedback.sound", true),
                        config.getBoolean("feedback.broadcast", true)
                ),
                sound(config, "sounds.rankup", "UI_TOAST_CHALLENGE_COMPLETE", 1.0, 1.0),
                sound(config, "sounds.denied", "BLOCK_NOTE_BLOCK_BASS", 0.8, 0.8),
                config.getString("discord.game-channel", "global"),
                config.getString("discord.message", "**%player%** advanced to **%rank_short_name%**!"),
                config.getInt("backup.keep", 10),
                RankMenu.parse(menus)
        );
    }

    private static SoundDescriptor sound(YamlConfiguration config, String path, String fallback,
                                         double fallbackVolume, double fallbackPitch) {
        return new SoundDescriptor(
                config.getString(path + ".sound", fallback),
                (float) config.getDouble(path + ".volume", fallbackVolume),
                (float) config.getDouble(path + ".pitch", fallbackPitch)
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Feedback(boolean chat, boolean title, boolean sound, boolean broadcast) {
    }

    public record SoundDescriptor(String sound, float volume, float pitch) {
        public SoundDescriptor {
            sound = sound == null ? "" : sound.trim();
            volume = Math.max(0.0f, volume);
            pitch = Math.max(0.0f, pitch);
        }
    }

    public record RankMenu(
            String title,
            int size,
            boolean refreshEnabled,
            long refreshIntervalTicks,
            List<Integer> rankSlots,
            int previousSlot,
            int infoSlot,
            int closeSlot,
            int nextSlot,
            boolean fillerEnabled,
            String fillerMaterial,
            String fillerName
    ) {
        public RankMenu {
            title = title == null ? "Plexon Ranks" : title;
            size = normalizeInventorySize(size);
            refreshIntervalTicks = Math.max(20L, refreshIntervalTicks);
            rankSlots = List.copyOf(rankSlots == null || rankSlots.isEmpty()
                    ? List.of(10, 11, 12, 13, 14, 15, 16) : rankSlots);
            fillerMaterial = fillerMaterial == null || fillerMaterial.isBlank()
                    ? "GRAY_STAINED_GLASS_PANE" : fillerMaterial;
            fillerName = fillerName == null ? " " : fillerName;
        }

        private static RankMenu parse(YamlConfiguration menus) {
            return new RankMenu(
                    menus.getString("rank-list.title", "Plexon Ranks"),
                    menus.getInt("rank-list.size", 54),
                    menus.getBoolean("rank-list.refresh.enabled", true),
                    menus.getLong("rank-list.refresh.interval-ticks", 40L),
                    menus.getIntegerList("rank-list.rank-slots"),
                    menus.getInt("rank-list.navigation.previous.slot", 45),
                    menus.getInt("rank-list.navigation.info.slot", 48),
                    menus.getInt("rank-list.navigation.close.slot", 49),
                    menus.getInt("rank-list.navigation.next.slot", 53),
                    menus.getBoolean("rank-list.filler.enabled", true),
                    menus.getString("rank-list.filler.material", "GRAY_STAINED_GLASS_PANE"),
                    menus.getString("rank-list.filler.name", " ")
            );
        }

        private static int normalizeInventorySize(int requested) {
            int clamped = Math.max(9, Math.min(54, requested));
            return ((clamped + 8) / 9) * 9;
        }
    }
}
