package neko.fantasy.auth.repository;

import jakarta.persistence.LockModeType;
import neko.fantasy.auth.domain.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailVerificationCode c where c.email = :email")
    Optional<EmailVerificationCode> findByEmailForUpdate(@Param("email") String email);

    @Modifying
    @Query("delete from EmailVerificationCode c where c.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
