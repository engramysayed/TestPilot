package delivery.mobile.runner.grammar;

import java.util.ArrayList;
import java.util.List;

/** Tokenizes authoring lines with quoted strings (Unicode preserved; escapes \\ \"). */
public final class QuotedStringLexer {
    private QuotedStringLexer() {}

    public static List<String> tokenize(String line) {
        List<String> out = new ArrayList<>();
        if (line == null || line.isBlank()) return out;
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '\\' && i + 1 < line.length()) {
                    cur.append(line.charAt(++i));
                } else if (c == quote) {
                    inQuote = false;
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    if (cur.length() > 0) {
                        out.add(cur.toString());
                        cur.setLength(0);
                    }
                    inQuote = true;
                    quote = c;
                } else if (Character.isWhitespace(c)) {
                    if (cur.length() > 0) {
                        out.add(cur.toString());
                        cur.setLength(0);
                    }
                } else {
                    cur.append(c);
                }
            }
        }
        if (inQuote) throw new GrammarException("UNCLOSED_QUOTE", "unclosed quote in: " + line);
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
