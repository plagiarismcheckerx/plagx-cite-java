package com.plagiarismcheckerx.plagx.cite;

import java.util.regex.Pattern;

/** Escapes text and italicises titles for the chosen output format. */
final class Formatter {
    private static final Pattern MARKDOWN_SPECIAL = Pattern.compile("([\\\\*_])");
    private final String format;

    Formatter(String format) {
        if (!"text".equals(format) && !"html".equals(format) && !"markdown".equals(format)) {
            throw new IllegalArgumentException("Unknown output format \"" + format + "\". Use \"text\", \"html\" or \"markdown\".");
        }
        this.format = format;
    }

    /** Plain text in the output format. */
    String t(String s) {
        if ("html".equals(format)) {
            StringBuilder sb = new StringBuilder(s.length());
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '&': sb.append("&amp;"); break;
                    case '<': sb.append("&lt;"); break;
                    case '>': sb.append("&gt;"); break;
                    case '"': sb.append("&quot;"); break;
                    default: sb.append(c);
                }
            }
            return sb.toString();
        }
        if ("markdown".equals(format)) return MARKDOWN_SPECIAL.matcher(s).replaceAll("\\\\$1");
        return s;
    }

    /** Italic text in the output format. */
    String i(String s) {
        if ("html".equals(format)) return "<i>" + t(s) + "</i>";
        if ("markdown".equals(format)) return "*" + t(s) + "*";
        return s;
    }
}
