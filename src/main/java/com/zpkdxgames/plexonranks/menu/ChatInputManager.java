package com.zpkdxgames.plexonranks.menu;

import com.zpkdxgames.plexonranks.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public final class ChatInputManager implements Listener {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputManager(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void request(Player player, Consumer<String> callback, Runnable cancelled) {
        pending.put(player.getUniqueId(), new PendingInput(System.currentTimeMillis() + 60_000L, callback, cancelled));
        player.closeInventory();
        messages.send(player, "admin.editor-chat-prompt");
    }

    public boolean awaiting(UUID uuid) {
        return pending.containsKey(uuid);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput input = pending.remove(event.getPlayer().getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String value = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (System.currentTimeMillis() > input.expiresAt() || value.equalsIgnoreCase("cancel")) {
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

    private record PendingInput(long expiresAt, Consumer<String> callback, Runnable cancelled) {
    }
}

