package neko.fantasy.auth.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthConsentRepository extends JpaRepository<OAuthConsent, Long> {

    Optional<OAuthConsent> findByUserIdAndClientId(Long userId, Long clientId);
}
