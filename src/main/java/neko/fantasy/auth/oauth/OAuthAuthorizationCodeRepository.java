package neko.fantasy.auth.oauth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthAuthorizationCodeRepository extends JpaRepository<OAuthAuthorizationCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OAuthAuthorizationCode c join fetch c.client join fetch c.user where c.codeHash = :hash")
    Optional<OAuthAuthorizationCode> findByHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("delete from OAuthAuthorizationCode c where c.expiresAt < :cutoff")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
