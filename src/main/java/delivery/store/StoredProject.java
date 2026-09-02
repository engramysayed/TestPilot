package delivery.store;

import java.nio.file.Path;

public record StoredProject(String projectId, Path rootPath, int version) {
}
