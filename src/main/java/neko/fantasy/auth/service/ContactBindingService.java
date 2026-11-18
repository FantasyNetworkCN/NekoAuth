package neko.fantasy.auth.service;

import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactBindingService {

    private final UserRepository users;
    private final EmailVerificationService emailVerification;
    private final SmsRegistrationService smsVerification;
    private final RegistrationCaptchaService captchas;
    private final UserPasswordEncoder passwords;
    private final LoginRateLimiter passwordAttempts;

    public ContactBindingService(UserRepository users, EmailVerificationService emailVerification,
                                 SmsRegistrationService smsVerification, RegistrationCaptchaService captchas,
                                 UserPasswordEncoder passwords, LoginRateLimiter passwordAttempts) {
        this.users = users;
        this.emailVerification = emailVerification;
        this.smsVerification = smsVerification;
        this.captchas = captchas;
        this.passwords = passwords;
        this.passwordAttempts = passwordAttempts;
    }

    public ApiResponses.VerificationCodeSent sendEmailCode(Long userId, String rawEmail,
                                                           String captchaId, String captchaAnswer) {
        String email = EmailVerificationService.normalizeEmail(rawEmail);
        captchas.verifyAndConsume(captchaId, captchaAnswer);
        requireEmailAvailable(requireEnabled(users.findById(userId).orElse(null)), email);
        var delivery = emailVerification.sendBindingCode(email, userId);
        return new ApiResponses.VerificationCodeSent(
                delivery.message(), delivery.expiresInSeconds(), delivery.resendAfterSeconds());
    }

    public ApiResponses.VerificationCodeSent sendPhoneCode(Long userId, String rawPhone,
                                                           String captchaId, String captchaAnswer, String clientIp) {
        String phone = SmsRegistrationService.normalizePhone(rawPhone);
        captchas.verifyAndConsume(captchaId, captchaAnswer);
        requirePhoneAvailable(requireEnabled(users.findById(userId).orElse(null)), phone);
        return smsVerification.sendBindingCode(phone, clientIp, userId);
    }

    @Transactional
    public ApiResponses.User bindEmail(Long userId, String rawEmail, String code, String currentPassword) {
        String email = EmailVerificationService.normalizeEmail(rawEmail);
        User user = requireEnabled(users.findByIdForUpdate(userId).orElse(null));
        requireEmailAvailable(user, email);
        verifyPassword(user, currentPassword);
        emailVerification.verifyBindingCode(email, code, userId);
        user.setEmail(email);
        return ApiResponses.User.from(users.saveAndFlush(user));
    }

    @Transactional
    public ApiResponses.User bindPhone(Long userId, String rawPhone, String code, String currentPassword) {
        String phone = SmsRegistrationService.normalizePhone(rawPhone);
        User user = requireEnabled(users.findByIdForUpdate(userId).orElse(null));
        requirePhoneAvailable(user, phone);
        verifyPassword(user, currentPassword);
        smsVerification.verifyBindingCode(phone, code, userId);
        user.setPhone(phone);
        return ApiResponses.User.from(users.saveAndFlush(user));
    }

    private void requireEmailAvailable(User user, String email) {
        if (user.getEmail() != null) {
            throw ApiException.conflict("EMAIL_ALREADY_BOUND", "当前账户已绑定邮箱");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "该邮箱已关联其他账户");
        }
    }

    private void requirePhoneAvailable(User user, String phone) {
        if (user.getPhone() != null) {
            throw ApiException.conflict("PHONE_ALREADY_BOUND", "当前账户已绑定手机号");
        }
        if (users.existsByPhone(phone)) {
            throw ApiException.conflict("PHONE_TAKEN", "该手机号已关联其他账户");
        }
    }

    private void verifyPassword(User user, String password) {
        String key = "contact-binding:" + user.getId();
        passwordAttempts.check(key);
        if (!passwords.matches(password, user.getPasswordHash())) {
            passwordAttempts.recordFailure(key);
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "当前密码不正确");
        }
        passwordAttempts.recordSuccess(key);
    }

    private static User requireEnabled(User user) {
        if (user == null || !user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_UNAVAILABLE", "当前账户不可用");
        }
        return user;
    }
}
