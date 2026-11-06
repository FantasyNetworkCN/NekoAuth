package neko.fantasy.auth.util;

/**
 * 昵称校验：注册与修改昵称共用同一套规则。
 */
public final class NicknameValidator {

    public static final int MAX_LENGTH = 32;

    private NicknameValidator() {
    }

    /**
     * 校验昵称是否合法。
     *
     * @param nickname 已 trim 的昵称
     * @return 合法时返回 {@code null}，否则返回错误提示
     */
    public static String validate(String nickname) {
        if (nickname == null || nickname.isEmpty() || nickname.length() > MAX_LENGTH) {
            return "昵称长度需为 1-32 个字符";
        }
        if (nickname.chars().anyMatch(Character::isISOControl)) {
            return "昵称不能包含控制字符";
        }
        if (SensitiveWordUtil.contains(nickname)) {
            return "昵称包含违禁词";
        }
        return null;
    }
}
