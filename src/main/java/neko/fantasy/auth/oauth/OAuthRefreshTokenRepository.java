package neko.fantasy.auth.oauth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthRefreshTokenRepository extends JpaRepository<OAuthRefreshToken, Long> {

    Optional<OAuthRefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OAuthRefreshToken r join fetch r.client join fetch r.user where r.tokenHash = :hash")
    Optional<OAuthRefreshToken> findByHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("""
            update OAuthRefreshToken r set r.revokedAt = :now
            where r.user.id = :userId and r.client.id = :clientId and r.revokedAt is null
            """)
    int revokeActiveForFamily(@Param("userId") Long userId, @Param("clientId") Long clientId,
                              @Param("now") Instant now);

    @Modifying
    @Query("""
            update OAuthRefreshToken r set r.revokedAt = :now
            where r.user.id = :userId and r.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from OAuthRefreshToken r where r.expiresAt < :cutoff or r.revokedAt < :cutoff")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
