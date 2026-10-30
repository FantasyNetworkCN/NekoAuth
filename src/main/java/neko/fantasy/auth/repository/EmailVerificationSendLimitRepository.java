package neko.fantasy.auth.repository;

import jakarta.persistence.LockModeType;
import neko.fantasy.auth.domain.EmailVerificationSendLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationSendLimitRepository extends JpaRepository<EmailVerificationSendLimit, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from EmailVerificationSendLimit l where l.email = :email")
    Optional<EmailVerificationSendLimit> findByEmailForUpdate(@Param("email") String email);

    @Modifying
    @Query("delete from EmailVerificationSendLimit l where l.lastSentAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
