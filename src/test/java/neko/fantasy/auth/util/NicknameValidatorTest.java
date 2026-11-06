package neko.fantasy.auth.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NicknameValidatorTest {

    @Test
    void acceptsNormalNickname() {
        assertThat(NicknameValidator.validate("小梦")).isNull();
    }

    @Test
    void rejectsBannedWord() {
        assertThat(NicknameValidator.validate("001工程")).isEqualTo("昵称包含违禁词");
    }

    @Test
    void detectsBannedWordsSplitByWhitespaceOrZeroWidthCharacters() {
        assertThat(NicknameValidator.validate("0 0 1 工 程")).isEqualTo("昵称包含违禁词");
        assertThat(NicknameValidator.validate("001\u200B工程")).isEqualTo("昵称包含违禁词");
    }

    @Test
    void honorsAllowList() {
        assertThat(NicknameValidator.validate("幸运")).isNull();
    }

    @Test
    void rejectsInvalidLengthAndControlCharacters() {
        assertThat(NicknameValidator.validate("")).isEqualTo("昵称长度需为 1-32 个字符");
        assertThat(NicknameValidator.validate("a".repeat(33))).isEqualTo("昵称长度需为 1-32 个字符");
        assertThat(NicknameValidator.validate("猫\u0000娘")).isEqualTo("昵称不能包含控制字符");
    }
}
