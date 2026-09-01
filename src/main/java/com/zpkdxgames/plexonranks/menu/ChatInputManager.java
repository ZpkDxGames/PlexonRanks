package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.service.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatInputManager implements Listener {
    private static final long INPUT_TIMEOUT_TICKS = 20L * 60L;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputManager(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void request(Player player, Consumer<String> callback, Runnable cancelled) {
        PendingInput input = new PendingInput(callback, cancelled);
        pending.put(player.getUniqueId(), input);
        player.closeInventory();
        messages.send(player, "admin.editor-chat-prompt");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(player.getUniqueId(), input) && player.isOnline()) {
                messages.send(player, "admin.editor-chat-expired");
            }
        }, INPUT_TIMEOUT_TICKS);
    }

    public boolean awaiting(UUID uuid) {
        return pending.containsKey(uuid);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        PendingInput input = pending.remove(event.getPlayer().getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String value = PLAIN.serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (value.equalsIgnoreCase("cancel")) {
                input.cancelled().run();
            } else {
                input.callback().accept(value);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private record PendingInput(Consumer<String> callback, Runnable cancelled) {
    }
}
