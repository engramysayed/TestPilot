package delivery.store;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocatorMapStore {
    private final Path mapFile;

    public LocatorMapStore(Path mapFile) {
        this.mapFile = mapFile;
    }

    public JSONObject load() throws Exception {
        if (!Files.exists(mapFile)) {
            return new JSONObject();
        }
        return new JSONObject(Files.readString(mapFile, StandardCharsets.UTF_8));
    }

    public void save(JSONObject map) throws Exception {
        Files.createDirectories(mapFile.getParent());
        Files.writeString(mapFile, map.toString(2), StandardCharsets.UTF_8);
    }
}
