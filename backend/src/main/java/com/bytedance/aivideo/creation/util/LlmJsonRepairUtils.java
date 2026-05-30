package com.bytedance.aivideo.creation.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 修复大模型常见的轻微 JSON 结构错误，例如多余的闭合括号或缺失的结尾括号。
 */
public final class LlmJsonRepairUtils {

    private LlmJsonRepairUtils() {
    }

    public static String normalizeJsonObjectText(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return trimmed;
        }

        String candidate = trimmed.substring(start);
        StringBuilder repaired = new StringBuilder(candidate.length() + 8);
        Deque<Character> expectedClosers = new ArrayDeque<>();
        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < candidate.length(); i++) {
            char ch = candidate.charAt(i);
            if (inString) {
                repaired.append(ch);
                if (escaping) {
                    escaping = false;
                } else if (ch == '\\') {
                    escaping = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
                repaired.append(ch);
                continue;
            }

            if (ch == '{') {
                expectedClosers.push('}');
                repaired.append(ch);
                continue;
            }
            if (ch == '[') {
                expectedClosers.push(']');
                repaired.append(ch);
                continue;
            }
            if (ch == '}' || ch == ']') {
                if (!expectedClosers.isEmpty() && expectedClosers.peek() == ch) {
                    expectedClosers.pop();
                    repaired.append(ch);
                }
                continue;
            }

            repaired.append(ch);
        }

        while (!expectedClosers.isEmpty()) {
            repaired.append(expectedClosers.pop());
        }
        return repaired.toString().trim();
    }
}
