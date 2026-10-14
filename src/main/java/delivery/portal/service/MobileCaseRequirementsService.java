package delivery.portal.service;

import delivery.mobile.auth.AuthContractException;
import delivery.mobile.auth.AuthMode;
import delivery.mobile.auth.MobileCaseRequirementsStore;
import delivery.mobile.auth.MobileCaseRequirementsView;
import delivery.portal.model.ProjectRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Portal mobile case-requirements (M3-06 Final). */
@Service
public class MobileCaseRequirementsService {
    private final PortalStore store;
    private final MobileCaseRequirementsStore requirements;

    public MobileCaseRequirementsService(PortalStore store, MobileCaseRequirementsStore requirements) {
        this.store = store;
        this.requirements = requirements;
    }

    public ProjectRecord requireProject(String projectId, Long userId) {
        return store.getOwnedProject(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown project"));
    }

    public List<MobileCaseRequirementsView> list(String projectId, Long userId) {
        ProjectRecord project = requireProject(projectId, userId);
        try {
            return requirements.list(project.getTenantId(), projectId);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "case requirements store error");
        }
    }

    public MobileCaseRequirementsView get(String projectId, String tcId, Long userId) {
        ProjectRecord project = requireProject(projectId, userId);
        try {
            return requirements.get(project.getTenantId(), projectId, tcId)
                    .orElse(MobileCaseRequirementsView.empty(tcId == null ? "" : tcId.trim()));
        } catch (AuthContractException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "case requirements store error");
        }
    }

    public MobileCaseRequirementsView put(String projectId, String tcId, Long userId, Map<String, Object> body) {
        ProjectRecord project = requireProject(projectId, userId);
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        String id = tcId == null ? "" : tcId.trim();
        if (id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tcId required");
        }
        // path tcId wins; body.tcId if present must match
        Object bodyTc = body.get("tcId");
        if (bodyTc != null && !String.valueOf(bodyTc).trim().isBlank()
                && !id.equals(String.valueOf(bodyTc).trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tcId mismatch");
        }
        AuthMode mode = null;
        Object rawMode = body.get("authMode");
        if (rawMode != null && !String.valueOf(rawMode).isBlank()) {
            try {
                mode = AuthMode.parse(String.valueOf(rawMode));
            } catch (AuthContractException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
        List<String> callBefore = stringList(body.get("callBefore"));
        String authProfileRevisionId = str(body.get("authProfileRevisionId"));
        String startStateTcId = str(body.get("startStateTcId"));
        boolean negativeLogin = body.get("negativeLogin") instanceof Boolean b && b;
        MobileCaseRequirementsView view = new MobileCaseRequirementsView(
                id, mode, callBefore, authProfileRevisionId, startStateTcId, negativeLogin);
        try {
            return requirements.put(project.getTenantId(), projectId, view);
        } catch (AuthContractException e) {
            if (MobileCaseRequirementsStore.NEGATIVE_LOGIN_SETUP.equals(e.code())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "case requirements store error");
        }
    }

    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "callBefore must be array");
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o).trim());
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
