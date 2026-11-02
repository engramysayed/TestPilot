package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.HealJavaLocatorPatcher;
import delivery.portal.service.HealProposalDiskStore;
import delivery.portal.service.HealProposalService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Heal proposals: AE create (pending), Accept patches existing generated Java only, Reject discards.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/heal-proposals")
public class HealProposalController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final HealProposalService proposals;

    public HealProposalController(
            PortalStore store,
            CurrentUserService currentUser,
            HealProposalService proposals
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.proposals = proposals;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "status", required = false) String status
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(proposals.list(projectId, status));
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{proposalId}")
    public ResponseEntity<?> get(
            @PathVariable("projectId") String projectId,
            @PathVariable("proposalId") String proposalId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return proposals.get(projectId, proposalId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Unknown heal proposal").asMap()));
        } catch (HealProposalDiskStore.BadPathException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            Map<String, Object> created = proposals.create(projectId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (HealProposalDiskStore.BadRequestException | HealProposalDiskStore.BadPathException e) {
            return badRequest(e.getMessage());
        } catch (HealProposalDiskStore.AlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("ALREADY_EXISTS", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping("/{proposalId}/accept")
    public ResponseEntity<?> accept(
            @PathVariable("projectId") String projectId,
            @PathVariable("proposalId") String proposalId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(proposals.accept(projectId, proposalId, currentUser.requireUserId()));
        } catch (HealProposalDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (HealProposalDiskStore.ConflictException | HealJavaLocatorPatcher.MissingPageException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("CONFLICT", e.getMessage()).asMap());
        } catch (HealJavaLocatorPatcher.BadPatchException
                 | HealProposalDiskStore.BadRequestException
                 | HealProposalDiskStore.BadPathException e) {
            return badRequest(e.getMessage());
        } catch (delivery.portal.service.credits.InsufficientCreditsException e) {
            throw e;
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping("/{proposalId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable("projectId") String projectId,
            @PathVariable("proposalId") String proposalId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(proposals.reject(projectId, proposalId, currentUser.requireUserId()));
        } catch (HealProposalDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (HealProposalDiskStore.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("CONFLICT", e.getMessage()).asMap());
        } catch (HealProposalDiskStore.BadPathException | HealProposalDiskStore.BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (delivery.portal.service.credits.InsufficientCreditsException e) {
            throw e;
        } catch (Exception e) {
            return internalError(e);
        }
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", message).asMap());
    }

    private static ResponseEntity<?> internalError(Exception e) {
        String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", msg).asMap());
    }
}
