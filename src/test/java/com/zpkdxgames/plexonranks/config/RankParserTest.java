package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.model.RewardType;
import com.zpkdxgames.plexonranks.model.ValidationIssue;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankParserTest {
    @Test
    void parsesModularRequirementsAndRewards() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                ranks:
                  unranked:
                    order: 0
                    default: true
                    display: {name: '<gray>Unranked</gray>', short-name: Unranked, tag: '[Unranked]'}
                    requirements: []
                    rewards: []
                  newbie-1:
                    order: 1
                    display: {name: '<white>Newbie I</white>', short-name: Newbie I, tag: '[Newbie I]'}
                    requirements:
                      - {type: MONEY, amount: 2000, consume: true}
                      - {type: PLAYTIME, amount: 180, unit: MINUTES}
                    rewards:
                      - type: COMMAND
                        commands: ['keysadmin give %player% basic 1']
                        display: ['1x Basic Key']
                      - type: PERMISSION
                        permissions: ['rank.1']
                        persistent: true
                """);
        RankParser.ParseResult result = new RankParser().parse(yaml);
        assertFalse(result.issues().stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR));
        assertEquals(2, result.ranks().size());
        assertEquals(RequirementType.MONEY, result.ranks().get(1).requirements().getFirst().type());
        assertTrue(result.ranks().get(1).requirements().getFirst().consume());
        assertEquals(RewardType.PERMISSION, result.ranks().get(1).rewards().get(1).type());
        assertTrue(result.ranks().get(1).rewards().get(1).persistent());
    }

    @Test
    void parsesEveryAuditedRequirementAndRewardTypeWithoutInventedCounters() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                ranks:
                  root:
                    order: 0
                    default: true
                    requirements: []
                    rewards: []
                  target:
                    order: 1
                    requirements:
                      - {type: MONEY, amount: 10, consume: true}
                      - {type: XP_LEVELS, amount: 2, consume: true}
                      - {type: PLAYTIME, amount: 30, unit: MINUTES}
                      - {type: PERMISSION, permission: example.access}
                      - {type: PLACEHOLDER, placeholder: '%example_value%', operator: '>=', value: '5'}
                      - {type: ITEM, material: DIAMOND, amount: 3, consume: true}
                    rewards:
                      - {type: COMMAND, commands: ['say %player%'], display: ['command']}
                      - {type: PERMISSION, permissions: ['example.reward'], persistent: true}
                      - {type: LUCKPERMS_GROUP, group: example, persistent: true}
                      - {type: MONEY, amount: 50}
                      - {type: XP_LEVELS, amount: 4}
                      - {type: ITEM, material: EMERALD, amount: 2}
                """);
        RankParser.ParseResult result = new RankParser().parse(yaml);
        assertFalse(result.issues().stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR));
        assertEquals(List.of(RequirementType.MONEY, RequirementType.XP_LEVELS, RequirementType.PLAYTIME,
                        RequirementType.PERMISSION, RequirementType.PLACEHOLDER, RequirementType.ITEM),
                result.ranks().get(1).requirements().stream().map(value -> value.type()).toList());
        assertEquals(List.of(RewardType.COMMAND, RewardType.PERMISSION, RewardType.LUCKPERMS_GROUP,
                        RewardType.MONEY, RewardType.XP_LEVELS, RewardType.ITEM),
                result.ranks().get(1).rewards().stream().map(value -> value.type()).toList());
    }

    @Test
    void rejectsDuplicateOrderAndUnknownType() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                ranks:
                  one:
                    order: 1
                    default: true
                    requirements: [{type: UNKNOWN, amount: 2}]
                  two:
                    order: 1
                    requirements: []
                """);
        RankParser.ParseResult result = new RankParser().parse(yaml);
        assertTrue(result.issues().stream().filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR).count() >= 2);
    }

    @Test
    void rejectsUnsafeAmountsOperatorsUnitsAndRewardPayloads() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                ranks:
                  start:
                    order: 0
                    default: true
                    requirements: []
                    rewards: []
                  broken:
                    order: 1
                    requirements:
                      - {type: MONEY, amount: NaN}
                      - {type: PLAYTIME, amount: 1, unit: WEEKS}
                      - {type: PLACEHOLDER, placeholder: '%quests%', operator: APPROX, amount: 1}
                    rewards:
                      - type: COMMAND
                        commands: ['']
                      - {type: ITEM, material: '', amount: 0}
                """);
        RankParser.ParseResult result = new RankParser().parse(yaml);
        List<String> messages = result.issues().stream().map(ValidationIssue::message).toList();
        assertTrue(messages.stream().anyMatch(message -> message.contains("finite")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Playtime unit")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("placeholder operator")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("blank or multiline")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("missing material")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("greater than zero")));
    }
}
