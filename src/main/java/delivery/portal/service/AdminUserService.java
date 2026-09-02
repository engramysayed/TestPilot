package delivery.portal.service;

import delivery.portal.persistence.InviteRepository;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final PortalUserRepository users;
    private final ProjectRepository projects;
    private final JobRepository jobs;
    private final InviteRepository invites;

    public AdminUserService(PortalUserRepository users, ProjectRepository projects,
                            JobRepository jobs, InviteRepository invites) {
        this.users = users;
        this.projects = projects;
        this.jobs = jobs;
        this.invites = invites;
    }

    @Transactional
    public void deleteUser(Long targetUserId, Long actingAdminId) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("user id is required");
        }
        if (targetUserId.equals(actingAdminId)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        PortalUser target = users.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (target.getRole() == PortalUser.Role.ADMIN) {
            long admins = users.findAllByOrderByIdAsc().stream()
                    .filter(u -> u.getRole() == PortalUser.Role.ADMIN && u.isEnabled())
                    .count();
            if (admins <= 1) {
                throw new IllegalArgumentException("Cannot delete the last admin account");
            }
        }
        jobs.deleteByOwnerUserId(targetUserId);
        projects.deleteByOwnerUserId(targetUserId);
        users.delete(target);
    }

    @Transactional
    public void deleteInvite(Long inviteId) {
        invites.findById(inviteId).ifPresent(invites::delete);
    }
}
