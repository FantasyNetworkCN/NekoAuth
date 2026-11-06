package neko.fantasy.auth.util;

import com.github.houbb.sensitive.word.api.IWordAllow;
import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 违禁词检测：词表来自 {@code src/main/resources/违禁词/}，检测引擎使用 houbb/sensitive-word。
 */
public final class SensitiveWordUtil {

    private static final String WORD_DIR = "/违禁词/";

    private static final SensitiveWordBs WORD_BS = SensitiveWordBs.newInstance()
            .wordDeny(wordDenyFromResources())
            .wordAllow(wordAllowFromResources())
            .init();

    private SensitiveWordUtil() {
    }

    public static boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String normalized = removeWhitespace(text);
        if (normalized.isEmpty()) {
            return false;
        }
        return WORD_BS.contains(normalized);
    }

    public static List<String> findAll(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String normalized = removeWhitespace(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return WORD_BS.findAll(normalized);
    }

    private static IWordDeny wordDenyFromResources() {
        return () -> {
            List<String> words = new ArrayList<>();
            words.addAll(loadResourceLines("主词表.txt"));
            words.addAll(loadResourceLines("英文词表.txt"));
            return words;
        };
    }

    private static IWordAllow wordAllowFromResources() {
        return () -> {
            List<String> words = new ArrayList<>();
            words.addAll(loadResourceLines("白名单.txt"));
            return words;
        };
    }

    private static List<String> loadResourceLines(String fileName) {
        String path = WORD_DIR + fileName;
        try (InputStream in = SensitiveWordUtil.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing sensitive word resource: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    lines.add(line);
                }
                return lines;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load sensitive word resource: " + path, e);
        }
    }

    private static String removeWhitespace(String text) {
        return text.replaceAll("[\\s\\u200B\\u200C\\u200D\\u2060\\uFEFF]+", "");
    }
}
