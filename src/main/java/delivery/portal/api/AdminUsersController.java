package delivery.portal.api;

import delivery.portal.persistence.InviteRepository;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.ProjectRepository;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.AccessRequestService;
import delivery.portal.service.AdminUserService;
import delivery.portal.service.InviteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminUsersController {
    private final PortalUserRepository users;
    private final InviteRepository invites;
    private final ProjectRepository projects;
    private final AdminUserService adminUsers;
    private final InviteService inviteService;
    private final AccessRequestService accessRequests;
    private final CurrentUserService currentUser;

    public AdminUsersController(PortalUserRepository users, InviteRepository invites,
                                ProjectRepository projects, AdminUserService adminUsers,
                                InviteService inviteService, AccessRequestService accessRequests,
                                CurrentUserService currentUser) {
        this.users = users;
        this.invites = invites;
        this.projects = projects;
        this.adminUsers = adminUsers;
        this.inviteService = inviteService;
        this.accessRequests = accessRequests;
        this.currentUser = currentUser;
    }

    @GetMapping("/users")
    public Map<String, Object> users() {
        Long me = currentUser.requireUserId();
        List<Map<String, Object>> userRows = users.findAllByOrderByIdAsc().stream()
                .map(u -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", u.getId());
                    row.put("email", u.getEmail());
                    row.put("role", u.getRole().name());
                    row.put("enabled", u.isEnabled());
                    row.put("projectCount", projects.findByOwnerUserIdOrderByIdDesc(u.getId()).size());
                    row.put("self", u.getId().equals(me));
                    return row;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> pendingInvites = invites.findByUsedFalseOrderByIdDesc().stream()
                .map(i -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", i.getId());
                    row.put("email", i.getEmail());
                    row.put("token", i.getToken());
                    row.put("expiresAt", i.getExpiresAt().toString());
                    row.put("acceptPath", "/invite/" + i.getToken());
                    return row;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> pendingAccess = accessRequests.listPending().stream()
                .map(r -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", r.getId());
                    row.put("email", r.getEmail());
                    row.put("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
                    row.put("status", r.getStatus().name());
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> out = new HashMap<>();
        out.put("users", userRows);
        out.put("pendingInvites", pendingInvites);
        out.put("pendingAccessRequests", pendingAccess);
        out.put("userCount", userRows.size());
        out.put("pendingInviteCount", pendingInvites.size());
        out.put("pendingAccessRequestCount", pendingAccess.size());
        out.put("mailConfigured", inviteService.mailConfigured());
        return out;
    }

    public record CreateInviteRequest(String email, Boolean sendEmail) {
    }

    public record ApproveAccessRequest(Boolean sendEmail) {
    }

    @PostMapping("/invites")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateInviteRequest body) {
        try {
            boolean send = body == null || body.sendEmail() == null || body.sendEmail();
            var result = inviteService.createInvite(
                    body == null ? null : body.email(),
                    currentUser.requireUserId(),
                    send
            );
            Map<String, Object> response = new HashMap<>();
            response.put("token", result.invite().getToken());
            response.put("email", result.invite().getEmail());
            response.put("expiresAt", result.invite().getExpiresAt().toString());
            response.put("acceptPath", "/invite/" + result.invite().getToken());
            response.put("acceptUrl", result.acceptUrl());
            response.put("emailSent", result.emailSent());
            response.put("mailConfigured", inviteService.mailConfigured());
            if (!result.emailSent()) {
                response.put("message", "Invite created. Email was not sent (mail disabled or sendEmail=false). Share the accept link manually.");
            } else {
                response.put("message", "Invite email sent to " + result.invite().getEmail());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/access-requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveAccess(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ApproveAccessRequest body) {
        try {
            boolean send = body == null || body.sendEmail() == null || body.sendEmail();
            var result = accessRequests.approve(id, currentUser.requireUserId(), send);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ok");
            response.put("email", result.invite().getEmail());
            response.put("acceptUrl", result.acceptUrl());
            response.put("emailSent", result.emailSent());
            response.put("message", result.emailSent()
                    ? "Approved. Invite email sent."
                    : "Approved. Share this link: " + result.acceptUrl());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/access-requests/{id}/deny")
    public ResponseEntity<Map<String, String>> denyAccess(@PathVariable("id") Long id) {
        try {
            accessRequests.deny(id, currentUser.requireUserId());
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Request denied"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable("userId") Long userId) {
        adminUsers.deleteUser(userId, currentUser.requireUserId());
        return ResponseEntity.ok(Map.of("status", "ok", "message", "User deleted"));
    }

    @DeleteMapping("/invites/{inviteId}")
    public ResponseEntity<Map<String, String>> deleteInvite(@PathVariable("inviteId") Long inviteId) {
        adminUsers.deleteInvite(inviteId);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Invite deleted"));
    }
}
