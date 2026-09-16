package delivery.identity;

import java.nio.file.Files;
import java.nio.file.Path;

/** Test helper process that holds a publication lock, then exits. */
public final class PublicationLockHold {
    private PublicationLockHold() {
    }

    public static void main(String[] args) throws Exception {
        Path lock = Path.of(args[0]);
        Path flag = Path.of(args[1]);
        long holdMs = Long.parseLong(args[2]);
        PublicationLock.call(lock, () -> {
            Files.writeString(flag, "held");
            Thread.sleep(holdMs);
            return null;
        });
    }
}
