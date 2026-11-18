package neko.fantasy.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.security.UserPrincipal;
import neko.fantasy.auth.service.ContactBindingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/me")
public class ContactBindingController {

    private final ContactBindingService binding;

    public ContactBindingController(ContactBindingService binding) {
        this.binding = binding;
    }

    @PostMapping("/email/code")
    public ApiResponses.VerificationCodeSent sendEmailCode(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Valid @RequestBody AuthRequests.RegistrationCode request) {
        return binding.sendEmailCode(principal.id(), request.email(), request.captchaId(), request.captchaAnswer());
    }

    @PostMapping("/phone/code")
    public ApiResponses.VerificationCodeSent sendPhoneCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AuthRequests.PhoneRegistrationCode request,
            HttpServletRequest servletRequest) {
        return binding.sendPhoneCode(principal.id(), request.phone(), request.captchaId(), request.captchaAnswer(),
                servletRequest.getRemoteAddr());
    }

    @PostMapping("/email")
    public ApiResponses.User bindEmail(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody AuthRequests.BindEmail request) {
        return binding.bindEmail(principal.id(), request.email(), request.verificationCode(), request.currentPassword());
    }

    @PostMapping("/phone")
    public ApiResponses.User bindPhone(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody AuthRequests.BindPhone request) {
        return binding.bindPhone(principal.id(), request.phone(), request.verificationCode(), request.currentPassword());
    }
}
