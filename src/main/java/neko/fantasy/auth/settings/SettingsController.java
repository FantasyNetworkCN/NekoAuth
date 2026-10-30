package neko.fantasy.auth.settings;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
public class SettingsController {

    private final SettingsService settingsService;
    private final MailService mailService;

    public SettingsController(SettingsService settingsService, MailService mailService) {
        this.settingsService = settingsService;
        this.mailService = mailService;
    }

    @GetMapping("/api/auth/registration-policy")
    public SettingsResponses.RegistrationPolicy registrationPolicy() {
        return settingsService.getRegistrationPolicy();
    }

    @GetMapping("/api/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponses.Settings settings() {
        return settingsService.getSettings();
    }

    @PutMapping("/api/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponses.Settings update(@Valid @RequestBody SettingsRequests.Update request) {
        return settingsService.update(request);
    }

    @PostMapping("/api/admin/settings/mail/test")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponses.MailTestResult testMail(
            @Valid @RequestBody SettingsRequests.MailTest request
    ) {
        mailService.sendTest(request.recipient().trim().toLowerCase(Locale.ROOT));
        return new SettingsResponses.MailTestResult("测试邮件已发送到 " + request.recipient().trim());
    }
}
