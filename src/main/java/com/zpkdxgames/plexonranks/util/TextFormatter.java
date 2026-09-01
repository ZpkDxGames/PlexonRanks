package com.zpkdxgames.plexonranks.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormatter {
    private static final Pattern LEGACY = Pattern.compile("(?i)&(?:[0-9A-FK-OR]|x&)");
    private static final Pattern PERSISTENT_NEGATED_DECORATION = Pattern.compile(
            "(?i)(?<!\\\\)<(/)?(!(?:bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|obf))>");
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final MiniMessage strictMiniMessage = MiniMessage.builder().strict(true).build();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
    private final boolean miniMessageSupport;
    private final boolean legacySupport;

    public TextFormatter(boolean legacySupport) {
        this(true, legacySupport);
    }

    public TextFormatter(boolean miniMessageSupport, boolean legacySupport) {
        this.miniMessageSupport = miniMessageSupport;
        this.legacySupport = legacySupport;
    }

    public Component component(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        Component parsed = miniMessageSupport ? miniMessage.deserialize(input) : Component.text(input);
        if (legacySupport) {
            parsed = deserializeLegacyText(parsed);
        }
        return parsed;
    }

    public Component component(String template, Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return component(template);
        }
        String tokenized = template == null ? "" : template;
        Map<String, String> tokens = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = entry.getKey().startsWith("%") ? entry.getKey() : "%" + entry.getKey() + "%";
            String token = "\uE000" + index++ + "\uE001";
            tokenized = tokenized.replace(placeholder, token);
            tokens.put(token, entry.getValue() == null ? "" : entry.getValue());
        }
        Component result = component(tokenized);
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(entry.getKey())
                    .replacement(component(entry.getValue()))
                    .build());
        }
        return result;
    }

    public List<Component> components(List<String> lines, Map<String, String> placeholders) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(component(line, placeholders));
        }
        return components;
    }

    public String plain(Component component) {
        return plain.serialize(component);
    }

    public String miniMessage(Component component) {
        return miniMessage.serialize(component);
    }

    public String legacy(Component component) {
        return legacy.serialize(component);
    }

    public Component withoutItalics(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public boolean valid(String input) {
        try {
            if (miniMessageSupport && input != null && !input.isEmpty()) {
                strictMiniMessage.deserialize(closePersistentNegatedDecorations(input));
            } else if (legacySupport && input != null) {
                legacy.deserialize(input);
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String closePersistentNegatedDecorations(String input) {
        Matcher matcher = PERSISTENT_NEGATED_DECORATION.matcher(input);
        Deque<String> openTags = new ArrayDeque<>();
        while (matcher.find()) {
            String tag = matcher.group(2);
            if (matcher.group(1) == null) {
                openTags.addLast(tag);
            } else if (!openTags.isEmpty() && openTags.peekLast().equalsIgnoreCase(tag)) {
                openTags.removeLast();
            }
        }
        if (openTags.isEmpty()) {
            return input;
        }
        StringBuilder normalized = new StringBuilder(input);
        while (!openTags.isEmpty()) {
            normalized.append("</").append(openTags.removeLast()).append('>');
        }
        return normalized.toString();
    }

    public static String replaceRaw(String input, Map<String, String> placeholders) {
        String rendered = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey().startsWith("%") ? entry.getKey() : "%" + entry.getKey() + "%";
            rendered = rendered.replace(key, entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private Component deserializeLegacyText(Component component) {
        List<Component> children = component.children().stream().map(this::deserializeLegacyText).toList();
        Component withoutChildren = component.children(List.of());
        if (withoutChildren instanceof TextComponent text && LEGACY.matcher(text.content()).find()) {
            List<Component> combined = new ArrayList<>(children.size() + 1);
            combined.add(legacy.deserialize(text.content()));
            combined.addAll(children);
            return Component.empty().style(text.style()).children(combined);
        }
        return withoutChildren.children(children);
    }
}
