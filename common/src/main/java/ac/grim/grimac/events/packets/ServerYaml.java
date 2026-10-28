package ac.grim.grimac.events.packets;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Reads values from the server's own config files (spigot.yml, paper-global.yml, ...). */
final class ServerYaml {
    private ServerYaml() {
    }

    // Reads the server's own YAML directly. The previous DynamicConfig-based version returned false even with
    // bungeecord: true in spigot.yml, so the proxy was never detected and [proxy] alerts and [proxy-ban]
    // requests were silently dropped.
    static boolean getBoolean(String pathToFile, String pathToValue) {
        File file = new File(pathToFile);
        if (!file.exists()) return false;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Object node = new Yaml().load(reader);
            for (String key : pathToValue.split("\\.")) {
                if (!(node instanceof Map<?, ?> map)) return false;
                node = map.get(key);
            }
            return Boolean.TRUE.equals(node);
        } catch (Exception e) {
            return false;
        }
    }
}
