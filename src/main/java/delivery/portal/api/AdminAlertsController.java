package delivery.portal.api;

import delivery.portal.alerts.Alert;
import delivery.portal.alerts.AlertChannel;
import delivery.portal.alerts.EmailAlertChannel;
import delivery.portal.alerts.TelegramAlertChannel;
import delivery.portal.roles.RolesService;
import delivery.portal.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin-only alert channel inspection and test.
 * <ul>
 *   <li>{@code GET /api/admin/alerts/channels} — reports id, enabled, minSeverity and readiness for each channel.</li>
 *   <li>{@code POST /api/admin/alerts/channels/{id}/test} — fires a synthetic warning so the operator can confirm
 *       the webhook / SMTP / bot is actually wired.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/alerts")
public class AdminAlertsController {
    private static final Logger log = LoggerFactory.getLogger(AdminAlertsController.class);

    private final CurrentUserService currentUser;
    private final RolesService roles;
    private final List<AlertChannel> channels;

    public AdminAlertsController(CurrentUserService currentUser, RolesService roles, List<AlertChannel> channels) {
        this.currentUser = currentUser;
        this.roles = roles;
        this.channels = channels == null ? List.of() : List.copyOf(channels);
    }

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> channels() {
        roles.requireHeadAdmin(currentUser.requireUser());
        List<Map<String, Object>> list = new ArrayList<>();
        for (AlertChannel c : channels) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id());
            row.put("enabled", c.enabled());
            row.put("minSeverity", c.minSeverity().name());
            row.put("readiness", readiness(c));
            list.add(row);
        }
        return ResponseEntity.ok(Map.of("channels", list));
    }

    @PostMapping("/channels/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable("id") String id) {
        roles.requireHeadAdmin(currentUser.requireUser());
        AlertChannel channel = find(id);
        if (channel == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "not_found",
                    "message", "No channel with id=" + id));
        }
        if (!channel.enabled()) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "disabled",
                    "message", "Channel is disabled; set keel.alerts.channels." + id + ".enabled=true"));
        }
        Alert alert = new Alert(
                "ADMIN_TEST",
                "channel:" + id,
                Alert.Severity.WARNING,
                Alert.Kind.RAISED,
                "This is a test alert from Keel. If you see this, the "
                        + id + " channel is wired correctly.",
                Instant.now());
        try {
            channel.send(alert);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "channel", id,
                    "at", alert.at().toString()));
        } catch (Exception e) {
            log.warn("Admin test alert failed for {}: {}", id, e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                    "status", "failed",
                    "channel", id,
                    "message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private AlertChannel find(String id) {
        if (id == null) return null;
        String want = id.trim().toLowerCase(Locale.ROOT);
        for (AlertChannel c : channels) {
            if (c != null && c.id() != null && want.equals(c.id().trim().toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }

    private static List<String> readiness(AlertChannel c) {
        return c.readinessIssues();
    }
}
