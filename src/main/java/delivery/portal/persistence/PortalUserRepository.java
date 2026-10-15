package delivery.portal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortalUserRepository extends JpaRepository<PortalUser, Long> {
    Optional<PortalUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<PortalUser> findAllByOrderByIdAsc();

    /** Explicit {@code portal_users.role_id} membership. Null role ids are not counted. */
    long countByRoleId(String roleId);
}
