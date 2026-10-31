package neko.fantasy.auth.oauth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface OAuthRevokedAccessTokenRepository extends JpaRepository<OAuthRevokedAccessToken, String> {

    @Modifying
    @Query("delete from OAuthRevokedAccessToken token where token.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
