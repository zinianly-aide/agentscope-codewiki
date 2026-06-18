package com.agentscope.codewiki.retriever;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TextTokenizer {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}_]+|[A-Z]?[a-z]+|[A-Z]+(?![a-z])");

    private TextTokenizer() {}

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(splitCamelCase(text));
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String splitCamelCase(String text) {
        return text.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ');
    }
}
