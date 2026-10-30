package neko.fantasy.auth.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSettingsRepository extends JpaRepository<ApplicationSettings, Long> {
}
