package neko.fantasy.auth.oauth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OAuthRefreshTokenReuseService {

    private final OAuthRefreshTokenRepository refreshTokenRepository;

    public OAuthRefreshTokenReuseService(OAuthRefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void revokeFamily(Long userId, Long clientId) {
        refreshTokenRepository.revokeActiveForFamily(userId, clientId, Instant.now());
    }
}
