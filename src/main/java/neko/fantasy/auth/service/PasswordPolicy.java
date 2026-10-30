package neko.fantasy.auth.service;

import neko.fantasy.auth.exception.ApiException;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public void validate(String password) {
        if (password == null || password.length() < 6 || password.length() > 30) {
            throw ApiException.badRequest("WEAK_PASSWORD", "密码长度需为 6-30 个字符");
        }
    }
}
