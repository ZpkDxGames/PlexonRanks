package com.zpkdxgames.plexonranks.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry.IntegrationState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlexonCoreBridgeLifecycleTest {
    @Test
    void compatibleCorePublishesLifecycleAndUnregistersCleanly() {
        Plugin plugin = plugin("PlexonRanks", "2.1.0");
        CoreVersion version = CoreVersion.of(1, 0, "1.0.0");
        ModuleRegistry modules = new ModuleRegistry(version);
        IntegrationRegistry integrations = integrations();
        integrations.publish("VAULT", "Vault", "1.7", IntegrationState.READY, Set.of("economy"), "Ready");
        PlexonCoreAPI api = api(version, modules, integrations);

        PlexonCoreBridge bridge = new PlexonCoreBridge(plugin, api);
        bridge.registerStarting();

        assertTrue(bridge.compatible());
        assertEquals("CORE", bridge.mode());
        assertEquals(ModuleState.STARTING, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());
        assertEquals(CoreBridge.ProviderHint.PRESENT, bridge.providerHint("VAULT"));

        bridge.markReady("ready");
        assertEquals(ModuleState.READY, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());
        assertEquals(IntegrationState.READY, integrations.get("PLEXON_RANKS").orElseThrow().state());

        bridge.markDegraded("optional provider unavailable");
        assertEquals(ModuleState.DEGRADED, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.markFailed("storage failed");
        assertEquals(ModuleState.FAILED, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.unregister();
        assertTrue(modules.find(CoreBridge.MODULE_ID).isEmpty());
        assertEquals("UNREGISTERED", bridge.registrationState());
    }

    @Test
    void incompatibleCoreNeverTransitionsToReady() {
        Plugin plugin = plugin("PlexonRanks", "2.1.0");
        CoreVersion version = CoreVersion.of(2, 0, "2.0.0");
        ModuleRegistry modules = new ModuleRegistry(version);
        IntegrationRegistry integrations = integrations();
        PlexonCoreBridge bridge = new PlexonCoreBridge(plugin, api(version, modules, integrations));

        bridge.registerStarting();

        assertFalse(bridge.compatible());
        assertEquals("STANDALONE", bridge.mode());
        assertEquals(ModuleState.INCOMPATIBLE, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.markReady("must not override incompatibility");
        assertEquals(ModuleState.INCOMPATIBLE, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());
        assertTrue(integrations.get("PLEXON_RANKS").isEmpty());

        bridge.unregister();
        assertTrue(modules.find(CoreBridge.MODULE_ID).isEmpty());
    }

    @Test
    void duplicateRegistrationOwnedByAnotherPluginIsNotReplacedOrRemoved() {
        Plugin plugin = plugin("PlexonRanks", "2.1.0");
        Plugin other = plugin("OtherRanks", "9.9.9");
        CoreVersion version = CoreVersion.of(1, 0, "1.0.0");
        ModuleRegistry modules = new ModuleRegistry(version);
        IntegrationRegistry integrations = integrations();
        ModuleDescriptor existing = new ModuleDescriptor(
                CoreBridge.MODULE_ID,
                "Other Ranks",
                "OtherRanks",
                "9.9.9",
                other,
                ModuleVersionRange.parse(CoreBridge.SUPPORTED_API_RANGE),
                Set.of("other"),
                ModuleState.READY,
                "Already registered",
                Instant.now());
        assertTrue(modules.register(existing).success());

        PlexonCoreBridge bridge = new PlexonCoreBridge(plugin, api(version, modules, integrations));
        bridge.registerStarting();

        assertEquals("STANDALONE", bridge.mode());
        assertEquals(other, modules.find(CoreBridge.MODULE_ID).orElseThrow().plugin());

        bridge.unregister();
        assertEquals(other, modules.find(CoreBridge.MODULE_ID).orElseThrow().plugin());
    }

    private static PlexonCoreAPI api(CoreVersion version, ModuleRegistry modules, IntegrationRegistry integrations) {
        return (PlexonCoreAPI) Proxy.newProxyInstance(
                PlexonCoreAPI.class.getClassLoader(),
                new Class<?>[]{PlexonCoreAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "version" -> version;
                    case "modules" -> modules;
                    case "integrations" -> integrations;
                    case "toString" -> "TestPlexonCoreAPI";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static IntegrationRegistry integrations() {
        PluginManager pluginManager = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new IntegrationRegistry(pluginManager);
    }

    private static Plugin plugin(String name, String version) {
        PluginMeta meta = (PluginMeta) Proxy.newProxyInstance(
                PluginMeta.class.getClassLoader(),
                new Class<?>[]{PluginMeta.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getVersion" -> version;
                    case "toString" -> name + " " + version;
                    default -> defaultValue(method.getReturnType());
                });
        Logger logger = Logger.getLogger("PlexonRanks-Test-" + name);
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getPluginMeta" -> meta;
                    case "getLogger" -> logger;
                    case "isEnabled" -> true;
                    case "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
