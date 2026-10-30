package neko.fantasy.auth.repository;

import jakarta.persistence.LockModeType;
import neko.fantasy.auth.domain.RegistrationCaptcha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RegistrationCaptchaRepository extends JpaRepository<RegistrationCaptcha, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RegistrationCaptcha c where c.id = :id")
    Optional<RegistrationCaptcha> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query("delete from RegistrationCaptcha c where c.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
