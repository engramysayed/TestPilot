package delivery.portal.persistence.roles;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByRoleId(String roleId);
    Optional<RoleEntity> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<RoleEntity> findAllByOrderBySystemDescNameAsc();
}
