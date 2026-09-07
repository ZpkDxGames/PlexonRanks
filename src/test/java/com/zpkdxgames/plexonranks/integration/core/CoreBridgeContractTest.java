package com.zpkdxgames.plexonranks.integration.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CoreBridgeContractTest {
    @Test
    void exposesStableModuleIdentityAndSupportedRange() {
        assertEquals("ranks", CoreBridge.MODULE_ID);
        assertEquals(">=1.0 <2.0", CoreBridge.SUPPORTED_API_RANGE);
    }

    @Test
    void standaloneBridgeDoesNotPretendCoreIsAvailable() {
        CoreBridge bridge = new StandaloneCoreBridge(false, "-", "-", "PlexonCore is not installed");

        assertFalse(bridge.installed());
        assertFalse(bridge.available());
        assertFalse(bridge.compatible());
        assertEquals("STANDALONE", bridge.mode());
        assertEquals("NOT_INSTALLED", bridge.registrationState());
        assertEquals(CoreBridge.ProviderHint.UNKNOWN, bridge.providerHint("VAULT"));
    }

    @Test
    void installedButUnavailableCoreStillFallsBackSafely() {
        CoreBridge bridge = new StandaloneCoreBridge(true, "1.0.0", "-", "PlexonCore is disabled");

        assertFalse(bridge.available());
        assertEquals("STANDALONE", bridge.mode());
        assertEquals("UNAVAILABLE", bridge.registrationState());
        assertEquals("1.0.0", bridge.pluginVersion());
    }
}
