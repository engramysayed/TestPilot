package utils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ScenarioReader {
    private static final String SCENARIO = loadScenario();

    private ScenarioReader() {
    }

    public static String getScenario() {
        return SCENARIO;
    }

    private static String loadScenario() {
        try (InputStream in = ScenarioReader.class.getClassLoader().getResourceAsStream("scenario.txt")) {
            if (in == null) {
                throw new IllegalStateException("scenario.txt not found in src/main/resources");
            }
            String scenario = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (scenario.isEmpty()) {
                throw new IllegalStateException("scenario.txt is empty");
            }
            return scenario;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load scenario from scenario.txt", e);
        }
    }
}
