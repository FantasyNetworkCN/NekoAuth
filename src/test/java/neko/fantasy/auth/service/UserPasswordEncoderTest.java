package neko.fantasy.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class UserPasswordEncoderTest {

    private static final String NEKOMUSIC_ARGON2I_HASH =
            "$argon2i$v=19$m=65536,t=10,p=1$9tfF2ueOXAkD5F27w/24JA$"
                    + "Aevc2b63K74xgAIS2Bo1Y4346nTzSvfHAIuO7tKKF+o";

    private final UserPasswordEncoder encoder = new UserPasswordEncoder();

    @Test
    void verifiesNekoMusicArgon2iHashWithoutRehashing() {
        assertThat(encoder.matches("Password123", NEKOMUSIC_ARGON2I_HASH)).isTrue();
        assertThat(encoder.matches("wrong-password", NEKOMUSIC_ARGON2I_HASH)).isFalse();
        assertThat(encoder.needsUpgrade(NEKOMUSIC_ARGON2I_HASH)).isFalse();
    }

    @Test
    void generatesSameArgon2iProfileAsNekoMusic() {
        String hash = encoder.encode("Password123");

        assertThat(hash).startsWith("$argon2i$v=19$m=65536,t=10,p=1$");
        assertThat(hash).hasSizeLessThanOrEqualTo(255);
        assertThat(encoder.matches("Password123", hash)).isTrue();
        assertThat(encoder.needsUpgrade(hash)).isFalse();
    }

    @Test
    void verifiesAndUpgradesLegacyBcryptHash() {
        String legacyHash = new BCryptPasswordEncoder(12).encode("Password123");

        assertThat(encoder.matches("Password123", legacyHash)).isTrue();
        assertThat(encoder.matches("wrong-password", legacyHash)).isFalse();
        assertThat(encoder.needsUpgrade(legacyHash)).isTrue();
    }

    @Test
    void rejectsMalformedHashes() {
        assertThat(encoder.matches("Password123", "not-a-hash")).isFalse();
        assertThat(encoder.matches("Password123", "$argon2i$broken")).isFalse();
        assertThat(encoder.needsUpgrade("not-a-hash")).isTrue();
    }
}
