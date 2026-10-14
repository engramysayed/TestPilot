package delivery.portal.persistence.credits;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserCreditsRepository extends JpaRepository<UserCredits, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserCredits c where c.userId = :userId")
    Optional<UserCredits> findByIdForUpdate(@Param("userId") Long userId);
}
