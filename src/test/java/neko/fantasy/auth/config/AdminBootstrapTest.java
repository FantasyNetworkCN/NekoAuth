package neko.fantasy.auth.config;

import neko.fantasy.auth.domain.Role;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.service.PasswordPolicy;
import neko.fantasy.auth.service.UserPasswordEncoder;
import neko.fantasy.auth.setup.SetupState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    @TempDir
    private Path stateDirectory;

    @Test
    void installationMarkerSynchronizesExistingAdminCredentialsOnce() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        User existing = new User();
        existing.setUsername("admin");
        existing.setEmail("old@example.com");
        existing.setPasswordHash("old-hash");
        existing.setRole(Role.USER);
        existing.setEnabled(false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("support@nekocore.cn")).thenReturn(Optional.empty());

        Path pendingMarker = stateDirectory.resolve(SetupState.PENDING_ADMIN_MARKER);
        Files.writeString(pendingMarker, "username=admin\n");
        UserPasswordEncoder passwordEncoder = new UserPasswordEncoder();
        AdminBootstrap bootstrap = new AdminBootstrap(
                properties("admin", "support@nekocore.cn", "NewPass123"),
                userRepository,
                passwordEncoder,
                new PasswordPolicy()
        );

        bootstrap.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("admin");
        assertThat(saved.getValue().getEmail()).isEqualTo("support@nekocore.cn");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("admin");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("NewPass123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(pendingMarker).doesNotExist();
    }

    @Test
    void existingAdminIsNotOverwrittenWithoutInstallationMarker() {
        UserRepository userRepository = mock(UserRepository.class);
        User existing = new User();
        existing.setUsername("admin");
        existing.setEmail("old@example.com");
        existing.setPasswordHash("old-hash");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("support@nekocore.cn")).thenReturn(Optional.empty());

        AdminBootstrap bootstrap = new AdminBootstrap(
                properties("admin", "support@nekocore.cn", "NewPass123"),
                userRepository,
                new UserPasswordEncoder(),
                new PasswordPolicy()
        );

        bootstrap.run(null);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        assertThat(existing.getEmail()).isEqualTo("old@example.com");
        assertThat(existing.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    void installationMarkerCreatesAdminWhenNoAccountExists() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("support@nekocore.cn")).thenReturn(Optional.empty());

        Path pendingMarker = stateDirectory.resolve(SetupState.PENDING_ADMIN_MARKER);
        Files.writeString(pendingMarker, "username=admin\n");
        UserPasswordEncoder passwordEncoder = new UserPasswordEncoder();
        AdminBootstrap bootstrap = new AdminBootstrap(
                properties("admin", "support@nekocore.cn", "NewPass123"),
                userRepository,
                passwordEncoder,
                new PasswordPolicy()
        );

        bootstrap.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("NewPass123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(pendingMarker).doesNotExist();
    }

    private AuthProperties properties(String username, String email, String password) {
        return new AuthProperties(
                null,
                null,
                null,
                new AuthProperties.BootstrapAdmin(username, email, password),
                null,
                null,
                new AuthProperties.Setup(stateDirectory.toString(), "test-token", true)
        );
    }
}
