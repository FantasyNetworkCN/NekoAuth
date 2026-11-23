package neko.fantasy.auth.repository;

import jakarta.persistence.LockModeType;
import neko.fantasy.auth.domain.LoginChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from LoginChallenge challenge join fetch challenge.user where challenge.id = :id")
    Optional<LoginChallenge> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query("delete from LoginChallenge challenge where challenge.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
