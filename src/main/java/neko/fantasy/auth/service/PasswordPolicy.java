package neko.fantasy.auth.service;

import neko.fantasy.auth.exception.ApiException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PasswordPolicy {

    public void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw ApiException.badRequest("WEAK_PASSWORD", "密码长度需为 8-64 个字符");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("WEAK_PASSWORD", "密码的 UTF-8 长度不能超过 72 字节");
        }
        if (password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw ApiException.badRequest("WEAK_PASSWORD", "密码必须同时包含字母和数字");
        }
    }
}
