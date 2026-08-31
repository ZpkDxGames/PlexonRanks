package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public final class MessageService {
    private final ConfigManager configs;

    public MessageService(ConfigManager configs) {
        this.configs = configs;
    }

    public String string(String path) {
        return configs.current().messages().getString(path, "<red>Missing message: " + path + "</red>");
    }

    public List<String> lines(String path) {
        return configs.current().messages().getStringList(path);
    }

    public Component component(String path, Map<String, String> placeholders) {
        return configs.formatter().component(string(path), placeholders);
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        Component prefix = configs.formatter().component(configs.current().messages().getString("prefix", ""));
        sender.sendMessage(prefix.append(component(path, placeholders)));
    }

    public void sendLines(CommandSender sender, String path, Map<String, String> placeholders) {
        for (String line : lines(path)) {
            sender.sendMessage(configs.formatter().component(line, placeholders));
        }
    }
}

