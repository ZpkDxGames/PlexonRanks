package com.zpkdxgames.plexonranks.menu;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase3PlayerUxArchitectureTest {
    @Test void progressionPathHasNoHiddenRightClickRankup() throws Exception {
        String source = read("src/main/java/com/zpkdxgames/plexonranks/menu/RankListMenu.java");
        assertFalse(source.contains("event.isRightClick()")); assertTrue(source.contains("<green><bold>RANK UP</bold></green>"));
    }
    @Test void progressionPathHasNoRepeatingViewerRefreshTask() throws Exception {
        String source = read("src/main/java/com/zpkdxgames/plexonranks/menu/RankListMenu.java");
        assertFalse(source.contains("runTaskTimer")); assertFalse(source.contains("BukkitTask")); assertTrue(source.contains("REFRESH"));
    }
    @Test void historyCompletionIsBoundToExactInventoryAndRequest() throws Exception {
        String source = read("src/main/java/com/zpkdxgames/plexonranks/menu/RankDashboardMenu.java");
        assertTrue(source.contains("current != inv")); assertTrue(source.contains("active.requestId().equals(requestId)")); assertTrue(source.contains("RankDashboardHolder.View.HISTORY"));
    }
    @Test void menusDoNotOwnLowerLayerMutationAuthorities() throws Exception {
        String combined = read("src/main/java/com/zpkdxgames/plexonranks/menu/RankDashboardMenu.java") + read("src/main/java/com/zpkdxgames/plexonranks/menu/RankListMenu.java");
        assertFalse(combined.contains("DatabaseManager")); assertFalse(combined.contains("VaultHook")); assertFalse(combined.contains("LuckPermsHook")); assertFalse(combined.contains("withdrawPlayer")); assertFalse(combined.contains("depositPlayer")); assertFalse(combined.contains("compareAndSetRank")); assertTrue(combined.contains("rankup.attempt(player)"));
    }
    @Test void playerMenusDoNotUseSqliteOrCasLanguage() throws Exception {
        String combined = read("src/main/java/com/zpkdxgames/plexonranks/menu/RankDashboardMenu.java") + read("src/main/java/com/zpkdxgames/plexonranks/menu/RankListMenu.java");
        assertFalse(combined.contains("SQLite")); assertFalse(combined.contains("CAS_FAILURE"));
    }
    @Test void defaultPathDoesNotExposeInternalRankIdOrRightClickInstruction() throws Exception {
        String menus = read("src/main/resources/menus.yml"); assertFalse(menus.contains("%rank_id%")); assertFalse(menus.contains("RIGHT-CLICK"));
        assertTrue(menus.contains("CURRENT RANK")); assertTrue(menus.contains("NEXT RANK")); assertTrue(menus.contains("MAXIMUM RANK • MASTERED"));
    }
    @Test void legacyRightClickConfigRemainsParseCompatibleAndDocumented() throws Exception {
        String config = read("src/main/resources/config.yml"); String runtime = read("src/main/java/com/zpkdxgames/plexonranks/config/RuntimeSettings.java");
        assertTrue(config.contains("right-click-next-rank: true")); assertTrue(config.contains("Legacy compatibility option")); assertTrue(runtime.contains("rankup.right-click-next-rank"));
    }
    @Test void rankupAndRewardAuthoritiesRemainSeparateClasses() throws Exception {
        String dashboard = read("src/main/java/com/zpkdxgames/plexonranks/menu/RankDashboardMenu.java"); String path = read("src/main/java/com/zpkdxgames/plexonranks/menu/RankListMenu.java");
        assertTrue(dashboard.contains("RankupService")); assertTrue(path.contains("RankupService")); assertFalse(dashboard.contains("RewardEngine")); assertFalse(path.contains("RewardEngine"));
    }
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }
}
