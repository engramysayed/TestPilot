package delivery.mobile.appium;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Offline mock NewSession / DeleteSession for AE-L6 tests (no network).
 */
public final class ScriptedAppiumSessionTransporter implements AppiumSessionTransporter {
    private final AtomicReference<String> nextSessionId = new AtomicReference<>("real-appium-sid-123");
    private volatile AppiumLifecycleException createFailure;
    private final List<CreateCall> creates = new ArrayList<>();
    private final List<DeleteCall> deletes = new ArrayList<>();

    public void setNextSessionId(String sessionId) {
        nextSessionId.set(sessionId);
    }

    public void failCreate(AppiumLifecycleException e) {
        createFailure = e;
    }

    public List<CreateCall> creates() {
        return List.copyOf(creates);
    }

    public List<DeleteCall> deletes() {
        return List.copyOf(deletes);
    }

    @Override
    public String createSession(String baseUrl, String deviceSerial) throws AppiumLifecycleException {
        creates.add(new CreateCall(baseUrl, deviceSerial, AppiumW3cCapabilities.newSessionBody(deviceSerial)));
        if (createFailure != null) {
            throw createFailure;
        }
        // Still enforce loopback for realism when testing factory with this mock's optional check:
        // Factory/transporter contract: blank serial rejected by caps builder before we get here
        // if callers use requireSerial — Scripted still builds body which rejects blank.
        String id = nextSessionId.get();
        if (id == null || id.isBlank()) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE, "scripted blank sessionId");
        }
        return id;
    }

    @Override
    public void deleteSession(String baseUrl, String sessionId) {
        deletes.add(new DeleteCall(baseUrl, sessionId));
    }

    public record CreateCall(String baseUrl, String deviceSerial, String body) {}
    public record DeleteCall(String baseUrl, String sessionId) {}
}
