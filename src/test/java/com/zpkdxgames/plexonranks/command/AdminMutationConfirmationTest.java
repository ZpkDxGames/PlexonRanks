package com.zpkdxgames.plexonranks.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminMutationConfirmationTest {
    private final UUID player = UUID.randomUUID();
    private final Object generation = new Object();

    @Test
    void exactStagedMutationConfirmsOnce() {
        AdminMutationConfirmation guard = new AdminMutationConfirmation(30_000);
        guard.stage("admin", player, "setrank:newbie-2", "newbie-1", generation, 1_000);
        assertEquals(AdminMutationConfirmation.Verification.CONFIRMED,
                guard.verify("admin", player, "setrank:newbie-2", "newbie-1", generation, 2_000));
        assertEquals(AdminMutationConfirmation.Verification.MISSING,
                guard.verify("admin", player, "setrank:newbie-2", "newbie-1", generation, 2_001));
    }

    @Test
    void expiredConfirmationIsRejectedAndRemoved() {
        AdminMutationConfirmation guard = new AdminMutationConfirmation(30_000);
        guard.stage("admin", player, "reset:unranked", "newbie-4", generation, 1_000);
        assertEquals(AdminMutationConfirmation.Verification.EXPIRED,
                guard.verify("admin", player, "reset:unranked", "newbie-4", generation, 31_001));
        assertEquals(0, guard.pendingCount());
    }

    @Test
    void authoritativeRankChangeMakesConfirmationStale() {
        AdminMutationConfirmation guard = new AdminMutationConfirmation(30_000);
        guard.stage("admin", player, "promote:1:newbie-2", "newbie-1", generation, 1_000);
        assertEquals(AdminMutationConfirmation.Verification.STALE,
                guard.verify("admin", player, "promote:1:newbie-2", "newbie-3", generation, 2_000));
    }

    @Test
    void reloadGenerationMakesConfirmationStale() {
        AdminMutationConfirmation guard = new AdminMutationConfirmation(30_000);
        guard.stage("admin", player, "demote:1:newbie-1", "newbie-2", generation, 1_000);
        assertEquals(AdminMutationConfirmation.Verification.STALE,
                guard.verify("admin", player, "demote:1:newbie-1", "newbie-2", new Object(), 2_000));
    }

    @Test
    void targetActionAndValueAreBound() {
        AdminMutationConfirmation guard = new AdminMutationConfirmation(30_000);
        guard.stage("admin", player, "promote:2:newbie-3", "newbie-1", generation, 1_000);
        assertEquals(AdminMutationConfirmation.Verification.MISMATCH,
                guard.verify("admin", player, "promote:1:newbie-2", "newbie-1", generation, 2_000));
        assertEquals(AdminMutationConfirmation.Verification.MISMATCH,
                guard.verify("admin", UUID.randomUUID(), "promote:2:newbie-3", "newbie-1", generation, 2_000));
        assertEquals(1, guard.pendingCount());
    }

    @Test
    void stagingAnotherActionReplacesPreviousActionForSameAdmin() {
        AdminMutationConfirmation guard = new AdminMutationConfirmation(30_000);
        guard.stage("admin", player, "setrank:newbie-2", "newbie-1", generation, 1_000);
        guard.stage("admin", player, "reset:unranked", "newbie-1", generation, 2_000);
        assertEquals(AdminMutationConfirmation.Verification.MISMATCH,
                guard.verify("admin", player, "setrank:newbie-2", "newbie-1", generation, 3_000));
        assertEquals(AdminMutationConfirmation.Verification.CONFIRMED,
                guard.verify("admin", player, "reset:unranked", "newbie-1", generation, 3_000));
    }

    @Test
    void differentAdminsHaveIndependentTokensButCasMustStillGuardCommit() {
        AdminMutationConfirmation guard = new AdminMutationConfirmation(30_000);
        guard.stage("admin-a", player, "setrank:newbie-2", "newbie-1", generation, 1_000);
        guard.stage("admin-b", player, "setrank:newbie-3", "newbie-1", generation, 1_000);
        assertEquals(AdminMutationConfirmation.Verification.CONFIRMED,
                guard.verify("admin-a", player, "setrank:newbie-2", "newbie-1", generation, 2_000));
        assertEquals(AdminMutationConfirmation.Verification.STALE,
                guard.verify("admin-b", player, "setrank:newbie-3", "newbie-2", generation, 2_001));
    }
}
