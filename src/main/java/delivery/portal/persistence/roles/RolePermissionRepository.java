package delivery.portal.persistence.roles;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {
    List<RolePermissionEntity> findByRoleId(String roleId);

    @Modifying(clearAutomatically = true)
    @Query("delete from RolePermissionEntity r where r.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") String roleId);

    long countByRoleId(String roleId);
}
