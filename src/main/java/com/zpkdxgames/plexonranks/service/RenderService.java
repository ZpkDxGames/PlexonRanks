package com.zpkdxgames.plexonranks.service;

import com.zpkdxgames.plexonranks.config.ConfigManager;
import com.zpkdxgames.plexonranks.model.Rank;
import com.zpkdxgames.plexonranks.model.RankState;
import com.zpkdxgames.plexonranks.model.RequirementDefinition;
import com.zpkdxgames.plexonranks.model.RequirementProgress;
import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.requirement.RequirementEngine;
import com.zpkdxgames.plexonranks.reward.RewardEngine;
import com.zpkdxgames.plexonranks.util.NumberFormats;
import com.zpkdxgames.plexonranks.util.ListPlaceholderExpander;
import com.zpkdxgames.plexonranks.util.TextFormatter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RenderService {
    private final ConfigManager configs;
    private final RequirementEngine requirements;

    public RenderService(ConfigManager configs, RequirementEngine requirements) {
        this.configs = configs;
        this.requirements = requirements;
    }

    public List<RequirementProgress> progress(Player player, Rank rank) {
        return requirements.evaluate(player, rank.requirements());
    }

    public List<String> requirementLines(List<RequirementProgress> progresses) {
        List<String> lines = new ArrayList<>(progresses.size());
        for (RequirementProgress progress : progresses) {
            String base = "requirement-display." + progress.definition().type().name() + "."
                    + (progress.complete() ? "completed" : "incomplete");
            String template = configs.current().menus().getString(base,
                    "<dark_gray>•</dark_gray> %completed% <gray>" + progress.definition().type() + ":</gray> %current%/%required%");
            lines.add(TextFormatter.replaceRaw(template, progress.placeholders()));
        }
        return lines.isEmpty()
                ? configs.current().menus().getStringList("rank-list.empty.requirements")
                : List.copyOf(lines);
    }

    public List<String> rewardLines(Rank rank) {
        List<String> lines = RewardEngine.display(rank);
        return lines.isEmpty()
                ? configs.current().menus().getStringList("rank-list.empty.rewards")
                : lines;
    }

    public List<String> expand(List<String> template, Rank rank, RankState state,
                               List<String> requirementLines, List<String> rewardLines) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("%description%", rank.display().description());
        lists.put("%requirements%", requirementLines);
        lists.put("%rewards%", rewardLines);
        lists.put("%state_details%", stateDetails(state));
        return ListPlaceholderExpander.expand(template, lists);
    }

    public Map<String, String> placeholders(Player player, Rank current, Rank target,
                                             List<RequirementProgress> progress, RankState state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("player_rank", current.display().name());
        values.put("player_rank_id", current.id());
        values.put("player_rank_order", String.valueOf(current.order()));
        values.put("rank_id", target.id());
        values.put("rank_order", String.valueOf(target.order()));
        values.put("rank_name", target.display().name());
        values.put("rank_display", target.display().name());
        values.put("rank_short_name", target.display().shortName());
        values.put("rank_tag", target.display().tag());
        values.put("status", status(state));
        values.put("status_icon", statusIcon(state));
        values.put("next_rank", target.display().name());
        values.put("next_rank_id", target.id());
        double normalized = RequirementEngine.overallProgress(progress);
        values.put("progress_percent", NumberFormats.number(normalized * 100.0));
        values.put("progress_bar", progressBar(normalized));
        long complete = progress.stream().filter(RequirementProgress::complete).count();
        values.put("requirements_complete", String.valueOf(complete));
        values.put("requirements_total", String.valueOf(progress.size()));
        values.put("requirements_remaining", String.valueOf(progress.size() - complete));
        values.put("commands_count", String.valueOf(target.rewards().stream().mapToInt(reward -> reward.commands().size()).sum()));
        values.put("permissions_count", String.valueOf(target.rewards().stream().mapToInt(reward -> reward.permissions().size()).sum()));
        setRequirementSummary(values, target);
        return values;
    }

    public Map<String, String> placeholders(Player player, Rank current, Optional<Rank> next) {
        if (next.isEmpty()) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("player", player.getName());
            values.put("uuid", player.getUniqueId().toString());
            values.put("player_rank", current.display().name());
            values.put("player_rank_id", current.id());
            values.put("player_rank_order", String.valueOf(current.order()));
            values.put("next_rank", "<gray>None</gray>");
            values.put("next_rank_id", "");
            values.put("progress_percent", "100");
            values.put("progress_bar", progressBar(1.0));
            values.put("requirements_complete", "0");
            values.put("requirements_total", "0");
            values.put("requirements_remaining", "0");
            return values;
        }
        List<RequirementProgress> progress = progress(player, next.get());
        return placeholders(player, current, next.get(), progress, RankState.NEXT);
    }

    public String status(RankState state) {
        return configs.current().menus().getString("rank-list.states." + state.name().toLowerCase() + ".status", state.name());
    }

    public String statusIcon(RankState state) {
        return configs.current().menus().getString("rank-list.states." + state.name().toLowerCase() + ".icon", "");
    }

    public List<String> stateDetails(RankState state) {
        return configs.current().menus().getStringList("rank-list.states." + state.name().toLowerCase() + ".lore");
    }

    public String progressBar(double normalized) {
        int width = Math.max(5, Math.min(50, configs.current().messages().getInt("rank.progress-bar.width", 18)));
        int filled = Math.max(0, Math.min(width, (int) Math.round(normalized * width)));
        String character = configs.current().messages().getString("rank.progress-bar.character", "■");
        String filledTemplate = configs.current().messages().getString("rank.progress-bar.filled", "<green>%bar%</green>");
        String emptyTemplate = configs.current().messages().getString("rank.progress-bar.empty", "<dark_gray>%bar%</dark_gray>");
        String complete = TextFormatter.replaceRaw(filledTemplate, Map.of("bar", character.repeat(filled)));
        String remaining = TextFormatter.replaceRaw(emptyTemplate, Map.of("bar", character.repeat(width - filled)));
        return complete + remaining;
    }

    private void setRequirementSummary(Map<String, String> values, Rank rank) {
        values.put("money", required(rank, RequirementType.MONEY));
        values.put("xp", required(rank, RequirementType.XP_LEVELS));
        values.put("playtime", required(rank, RequirementType.PLAYTIME));
    }

    private String required(Rank rank, RequirementType type) {
        return rank.requirements().stream()
                .filter(requirement -> requirement.type() == type)
                .findFirst()
                .map(RequirementDefinition::amount)
                .map(NumberFormats::number)
                .orElse("0");
    }
}
