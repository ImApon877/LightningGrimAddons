package ac.grim.grimac.manager.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerFileImplTest {

    @Test
    void addsProxyBanCommandToGroups() {
        String input = """
                Punishments:
                  Reach:
                    remove-violations-after: 450
                    checks:
                      - "Reach"
                      - "Hitboxes"
                    commands:
                      - "1:1 [log]"
                """;

        String output = ConfigManagerFileImpl.ensureLightningCombatPunishmentGroups(input);

        assertTrue(output.contains("  Reach:\n"));
        assertTrue(output.contains("      - \"Reach\"\n"));
        assertTrue(output.contains("20:40 [proxy-ban]\n"));
    }

    @Test
    void doesNotDuplicateProxyBanWhenAlreadyConfigured() {
        String input = """
                Punishments:
                  WallHit:
                    remove-violations-after: 1
                    checks:
                      - "WallHit"
                    commands:
                      - "1:1 [alert]"
                      - "20:40 [proxy-ban]"
                  EntityPierce:
                    remove-violations-after: 1
                    checks:
                      - "EntityPierce"
                    commands:
                      - "1:1 [alert]"
                """;

        String output = ConfigManagerFileImpl.ensureLightningCombatPunishmentGroups(input);

        assertEquals(input, output);
        assertEquals(1, output.split("\\[proxy-ban\\]", -1).length - 1);
    }

    @Test
    void ignoresNonPunishmentYaml() {
        String input = """
                alerts:
                  print-to-console: true
                """;

        assertEquals(input, ConfigManagerFileImpl.ensureLightningCombatPunishmentGroups(input));
    }

    @Test
    void detectsProxyBanCommentAsConfigured() {
        // An operator who disabled the bridge by commenting the line out
        // must not get it re-added on every startup.
        String input = """
                Punishments:
                  Reach:
                    remove-violations-after: 300
                    checks:
                      - "Reach"
                    commands:
                      - "1:1 [log]"
                      # - "20:40 [proxy-ban]"
                """;

        String output = ConfigManagerFileImpl.ensureLightningCombatPunishmentGroups(input);

        assertEquals(input, output);
    }
}
