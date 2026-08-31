package com.zpkdxgames.plexonranks.config;

import com.zpkdxgames.plexonranks.model.RequirementType;
import com.zpkdxgames.plexonranks.model.RewardType;
import com.zpkdxgames.plexonranks.model.ValidationIssue;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

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
                    display:
                      name: '<gray>Unranked</gray>'
                      short-name: Unranked
                      tag: '[Unranked]'
                    requirements: []
                    rewards: []
                  newbie-1:
                    order: 1
                    display:
                      name: '<white>Newbie I</white>'
                      short-name: Newbie I
                      tag: '[Newbie I]'
                    requirements:
                      - type: MONEY
                        amount: 2000
                        consume: true
                      - type: PLAYTIME
                        amount: 180
                        unit: MINUTES
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
}

