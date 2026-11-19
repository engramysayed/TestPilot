package delivery.portal.api;

import delivery.agent.harness.cursor.CursorStreamBus;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE stream of raw Cursor sidecar NDJSON lines for a job (each event is one line, name = "cursor").
 * The frontend opens this with EventSource on the status page and renders events as they arrive.
 * Auth: inherits the default {@code anyRequest().authenticated()} rule — session cookie required.
 */
@RestController
@RequestMapping("/api/jobs")
public class CursorStreamController {

    private final PortalStore store;
    private final CurrentUserService currentUser;

    public CursorStreamController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    @GetMapping(value = "/{jobId}/cursor-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable("jobId") String jobId) {
        // Ownership check — stream carries detailed agent output, don't leak to unrelated accounts.
        if (store.getOwnedJob(jobId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SseEmitter emitter = new SseEmitter((long) CursorStreamBus.DEFAULT_SUBSCRIBER_TIMEOUT_MS);
        CursorStreamBus.subscribe(jobId, emitter);
        return ResponseEntity.ok(emitter);
    }
}
