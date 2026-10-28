package ac.grim.grimac.events.packets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerYamlTest {

    @Test
    void detectsBungeeCordModeInSpigotYml(@TempDir Path dir) throws Exception {
        Path spigot = Files.writeString(dir.resolve("spigot.yml"), """
                settings:
                  debug: false
                  bungeecord: true
                """);

        assertTrue(ServerYaml.getBoolean(spigot.toString(), "settings.bungeecord"));
    }

    @Test
    void falseWhenDisabledMissingOrUnreadable(@TempDir Path dir) throws Exception {
        Path spigot = Files.writeString(dir.resolve("spigot.yml"), """
                settings:
                  bungeecord: false
                """);
        Path broken = Files.writeString(dir.resolve("broken.yml"), "settings: [unclosed");

        assertFalse(ServerYaml.getBoolean(spigot.toString(), "settings.bungeecord"));
        assertFalse(ServerYaml.getBoolean(spigot.toString(), "settings.missing.key"));
        assertFalse(ServerYaml.getBoolean(dir.resolve("absent.yml").toString(), "settings.bungeecord"));
        assertFalse(ServerYaml.getBoolean(broken.toString(), "settings.bungeecord"));
    }
}
