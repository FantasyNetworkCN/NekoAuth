package neko.fantasy.auth.repository;

import jakarta.persistence.LockModeType;
import neko.fantasy.auth.domain.SmsRegistrationChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface SmsRegistrationChallengeRepository extends JpaRepository<SmsRegistrationChallenge, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from SmsRegistrationChallenge challenge where challenge.phone = :phone")
    Optional<SmsRegistrationChallenge> findForUpdate(String phone);

    @Modifying
    @Query("delete from SmsRegistrationChallenge challenge where challenge.lastSentAt < :cutoff")
    void deleteOlderThan(Instant cutoff);
}
