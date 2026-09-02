package delivery.portal.service;

import delivery.portal.persistence.AccessRequest;
import delivery.portal.persistence.AccessRequestRepository;
import delivery.portal.persistence.PortalUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AccessRequestService {
    private final AccessRequestRepository requests;
    private final PortalUserRepository users;
    private final InviteService invites;

    public AccessRequestService(AccessRequestRepository requests, PortalUserRepository users,
                                InviteService invites) {
        this.requests = requests;
        this.users = users;
        this.invites = invites;
    }

    @Transactional
    public AccessRequest submit(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@") || normalized.length() < 5) {
            throw new IllegalArgumentException("Enter a valid email");
        }
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new IllegalArgumentException("An account already exists for this email. Sign in instead.");
        }
        if (requests.existsByEmailIgnoreCaseAndStatus(normalized, AccessRequest.Status.PENDING)) {
            throw new IllegalArgumentException("A request for this email is already pending review.");
        }
        AccessRequest req = new AccessRequest();
        req.setEmail(normalized);
        req.setStatus(AccessRequest.Status.PENDING);
        req.setCreatedAt(Instant.now());
        return requests.save(req);
    }

    public List<AccessRequest> listPending() {
        return requests.findByStatusOrderByCreatedAtDesc(AccessRequest.Status.PENDING);
    }

    @Transactional
    public InviteService.InviteCreateResult approve(Long requestId, Long adminUserId, boolean sendEmail) {
        AccessRequest req = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (req.getStatus() != AccessRequest.Status.PENDING) {
            throw new IllegalArgumentException("Request is no longer pending");
        }
        InviteService.InviteCreateResult invite = invites.createInvite(req.getEmail(), adminUserId, sendEmail);
        req.setStatus(AccessRequest.Status.APPROVED);
        req.setResolvedAt(Instant.now());
        req.setResolvedByUserId(adminUserId);
        requests.save(req);
        return invite;
    }

    @Transactional
    public void deny(Long requestId, Long adminUserId) {
        AccessRequest req = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (req.getStatus() != AccessRequest.Status.PENDING) {
            throw new IllegalArgumentException("Request is no longer pending");
        }
        req.setStatus(AccessRequest.Status.DENIED);
        req.setResolvedAt(Instant.now());
        req.setResolvedByUserId(adminUserId);
        requests.save(req);
    }
}
