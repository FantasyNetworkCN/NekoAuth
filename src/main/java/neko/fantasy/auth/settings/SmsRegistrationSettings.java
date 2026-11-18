package neko.fantasy.auth.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class SmsRegistrationSettings {

    // Nullable columns allow existing settings rows to start with SMS disabled and empty fields.
    @Column(name = "sms_enabled")
    Boolean enabled;

    @Column(name = "sms_access_key_id", length = 128)
    String accessKeyId;

    @Column(name = "sms_access_key_secret_ciphertext", length = 2048)
    String accessKeySecretCiphertext;

    @Column(name = "sms_sign_name", length = 128)
    String signName;

    @Column(name = "sms_scheme_name", length = 20)
    String schemeName;

    @Column(name = "sms_code_parameter", length = 64)
    String codeParameter;

    @Column(name = "sms_minutes_parameter", length = 64)
    String minutesParameter;

    protected SmsRegistrationSettings() {
    }
}
