package neko.fantasy.auth.service;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserPasswordEncoder {

    private static final int ARGON2_ITERATIONS = 10;
    private static final int ARGON2_MEMORY_KIB = 65536;
    private static final int ARGON2_PARALLELISM = 1;

    private final Argon2 argon2 = Argon2Factory.create();
    private final BCryptPasswordEncoder legacyBcrypt = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        return argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KIB, ARGON2_PARALLELISM,
                rawPassword.toCharArray());
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            if (storedHash.startsWith("$argon2")) {
                return argon2.verify(storedHash, rawPassword.toCharArray());
            }
            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$")
                    || storedHash.startsWith("$2y$")) {
                return legacyBcrypt.matches(rawPassword, storedHash);
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    public boolean needsUpgrade(String storedHash) {
        if (storedHash == null || !storedHash.startsWith("$argon2i$v=19$m=65536,t=10,p=1$")) {
            return true;
        }
        try {
            return argon2.needsRehash(storedHash, ARGON2_ITERATIONS, ARGON2_MEMORY_KIB,
                    ARGON2_PARALLELISM);
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}
