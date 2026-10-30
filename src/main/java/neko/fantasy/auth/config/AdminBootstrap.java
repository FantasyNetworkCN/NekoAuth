package neko.fantasy.auth.config;

import neko.fantasy.auth.domain.Role;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.service.PasswordPolicy;
import neko.fantasy.auth.service.UserPasswordEncoder;
import neko.fantasy.auth.setup.SetupState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

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

        Path pendingAdminMarker = SetupState.pendingAdminMarker(properties);
        boolean applyInstallationCredentials = Files.isRegularFile(pendingAdminMarker);
        Optional<User> usernameUser = userRepository.findByUsername(normalizedUsername);
        Optional<User> emailUser = userRepository.findByEmail(normalizedEmail);

        if (usernameUser.isPresent() && emailUser.isPresent()
                && !usernameUser.get().getId().equals(emailUser.get().getId())) {
            throw new IllegalStateException(
                    "初始化管理员冲突：用户名和邮箱分别属于不同账户，请清理冲突账户后重新启动");
        }

        User existing = usernameUser.or(() -> emailUser).orElse(null);
        if (existing != null && !applyInstallationCredentials) {
            log.info("管理员初始化跳过：用户名或邮箱已存在，不会覆盖现有密码");
            return;
        }

        User user = existing == null ? new User() : existing;
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setDisplayName(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        userRepository.saveAndFlush(user);

        if (applyInstallationCredentials) {
            deleteAfterCommit(pendingAdminMarker);
            log.warn("已按安装配置同步管理员账户 {}，一次性初始化标记已消费", normalizedUsername);
        } else {
            log.info("已创建初始管理员账户 {}", normalizedUsername);
        }
    }

    private static void deleteAfterCommit(Path marker) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteMarker(marker);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteMarker(marker);
            }
        });
    }

    private static void deleteMarker(Path marker) {
        try {
            Files.deleteIfExists(marker);
        } catch (IOException exception) {
            log.warn("无法删除一次性管理员初始化标记 {}，请在下次启动前手动删除，避免再次覆盖管理员密码",
                    marker, exception);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
