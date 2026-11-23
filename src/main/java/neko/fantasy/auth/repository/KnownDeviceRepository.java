package neko.fantasy.auth.repository;

import jakarta.persistence.LockModeType;
import neko.fantasy.auth.domain.KnownDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface KnownDeviceRepository extends JpaRepository<KnownDevice, Long> {

    Optional<KnownDevice> findByUserIdAndTokenHash(Long userId, String tokenHash);

    Optional<KnownDevice> findFirstByUserIdOrderByLastSeenAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select device from KnownDevice device
            where device.user.id = :userId and device.tokenHash = :tokenHash
            """)
    Optional<KnownDevice> findByUserIdAndTokenHashForUpdate(
            @Param("userId") Long userId, @Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update KnownDevice device set device.trustedUntil = null where device.user.id = :userId")
    int revokeTrustForUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from KnownDevice device where device.lastSeenAt < :cutoff")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
