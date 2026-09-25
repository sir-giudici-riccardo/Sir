package org.sigma.codelab;

import java.util.List;

public final class SymbolEditHelper {
    public enum Status {
        REPLACE,
        NOT_FOUND,
        AMBIGUOUS,
        NO_TOKEN
    }

    public static final class Result {
        public final Status status;
        public final int start;
        public final int end;
        public final String token;
        public final String replacement;
        public final List<String> candidates;

        private Result(
                Status status,
                int start,
                int end,
                String token,
                String replacement,
                List<String> candidates) {
            this.status = status;
            this.start = start;
            this.end = end;
            this.token = token;
            this.replacement = replacement;
            this.candidates = candidates;
        }
    }

    private SymbolEditHelper() {}

    public static Result resolve(
            String text,
            int selectionStart,
            int selectionEnd,
            SymbolShortcutIndex index) {
        if (text == null) text = "";
        if (index == null) throw new IllegalArgumentException("index must not be null");

        int length = text.length();
        int start = clamp(Math.min(selectionStart, selectionEnd), 0, length);
        int end = clamp(Math.max(selectionStart, selectionEnd), 0, length);

        if (start == end) start = tokenStart(text, end);

        if (start == end) {
            return new Result(Status.NO_TOKEN, start, end, "", null, List.of());
        }

        String token = text.substring(start, end);
        List<String> candidates = index.candidates(token);
        if (candidates.isEmpty()) {
            return new Result(Status.NOT_FOUND, start, end, token, null, candidates);
        }
        if (candidates.size() > 1) {
            return new Result(Status.AMBIGUOUS, start, end, token, null, candidates);
        }

        return new Result(Status.REPLACE, start, end, token, candidates.get(0), candidates);
    }

    private static int tokenStart(String text, int cursor) {
        int i = cursor;
        while (i > 0 && !isBoundary(text.charAt(i - 1))) i--;
        return i;
    }

    private static boolean isBoundary(char c) {
        return Character.isWhitespace(c)
                || c == '(' || c == ')' || c == '{' || c == '}'
                || c == '[' || c == ']' || c == ',' || c == ';'
                || c == '"' || c == '\'' || c == '=' || c == ':'
                || c == '+' || c == '*' || c == '/' || c == '%'
                || c == '<' || c == '>' || c == '!' || c == '&' || c == '|';
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
