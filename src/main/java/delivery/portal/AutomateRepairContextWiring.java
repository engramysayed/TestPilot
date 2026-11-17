package delivery.portal;

import delivery.agent.validation.automate.AutomateFrameworkAcceptanceGate;
import delivery.agent.validation.automate.AutomateFrameworkRepairBridge;
import delivery.portal.service.PortalStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Wires the project's saved locator ranking + preferred hooks into the Automate framework repair
 * context so Cursor repairs match the workspace's locator policy.
 */
@Component
public final class AutomateRepairContextWiring {
    private final PortalStore store;

    public AutomateRepairContextWiring(PortalStore store) {
        this.store = store;
    }

    @PostConstruct
    public void wire() {
        AutomateFrameworkAcceptanceGate.setLocatorPreferenceLookup(projectId -> {
            if (projectId == null || projectId.isBlank()) {
                return null;
            }
            try {
                String mode = store.locatorModeWire(projectId);
                String hooks = store.preferredHooksJoined(projectId);
                return new AutomateFrameworkRepairBridge.LocatorPreference(mode, hooks);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }
}
