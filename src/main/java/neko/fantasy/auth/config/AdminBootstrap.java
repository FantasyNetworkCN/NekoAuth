package neko.fantasy.auth.config;

import neko.fantasy.auth.domain.Role;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.service.PasswordPolicy;
import neko.fantasy.auth.service.UserPasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthProperties properties;
    private final UserRepository userRepository;
    private final UserPasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public AdminBootstrap(AuthProperties properties, UserRepository userRepository,
                          UserPasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        String username = trim(admin.username());
        String email = trim(admin.email());
        String password = admin.password() == null ? "" : admin.password();

        if (username.isEmpty() && email.isEmpty() && password.isEmpty()) {
            return;
        }
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException(
                    "初始化管理员时，auth.bootstrap-admin.username、auth.bootstrap-admin.email、auth.bootstrap-admin.password 必须同时设置");
        }

        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (!normalizedUsername.matches("^[a-z0-9_]{3,32}$")) {
            throw new IllegalStateException("auth.bootstrap-admin.username 只能包含 3-32 位字母、数字或下划线");
        }
        passwordPolicy.validate(password);

        if (userRepository.existsByUsername(normalizedUsername) || userRepository.existsByEmail(normalizedEmail)) {
            log.info("管理员初始化跳过：用户名或邮箱已存在");
            return;
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setDisplayName(trim(admin.displayName()).isEmpty() ? username : admin.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("已创建初始管理员账户 {}", normalizedUsername);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
