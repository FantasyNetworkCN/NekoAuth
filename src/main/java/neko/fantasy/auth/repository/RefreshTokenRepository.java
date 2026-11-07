package neko.fantasy.auth.repository;

import jakarta.persistence.LockModeType;
import neko.fantasy.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r join fetch r.user where r.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    Optional<RefreshToken> findByIdAndUserId(Long id, Long userId);

    Optional<RefreshToken> findFirstByFamilyIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String familyId, Instant now);

    boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(String familyId, Instant now);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken r set r.revokedAt = :now
            where r.user.id = :userId and r.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken r set r.revokedAt = :now
            where r.familyId = :familyId and r.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :cutoff or r.revokedAt < :cutoff")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
