package com.plagiarismcheckerx.plagx.cite;

import static com.plagiarismcheckerx.plagx.cite.Values.filled;
import static com.plagiarismcheckerx.plagx.cite.Values.str;
import static com.plagiarismcheckerx.plagx.cite.Values.trim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Name parsing and initials. */
final class Names {
    private Names() {}

    static final class Name {
        final String literal;
        final String family;
        final String given;
        final String suffix;

        Name(String literal, String family, String given, String suffix) {
            this.literal = literal;
            this.family = family;
            this.given = given;
            this.suffix = suffix;
        }
    }

    private static final Pattern SPACES = Pattern.compile(Values.WS + "+");

    /** Accepts a CSL name ({family, given, suffix} or {literal}) or a string ("Smith, John" / "John Smith"). */
    static Name parts(Object name) {
        if (name instanceof String) return parseString((String) name);
        Map<String, Object> o = Values.map(name);
        if (o == null) return new Name(null, "", "", null);
        if (filled(o.get("literal"))) return new Name(str(o.get("literal")), "", "", null);
        return new Name(null, Values.text(o, "family"), Values.text(o, "given"),
            filled(o.get("suffix")) ? str(o.get("suffix")) : null);
    }

    private static Name parseString(String value) {
        String s = trim(value);
        int comma = s.indexOf(',');
        if (comma >= 0) return new Name(null, trim(s.substring(0, comma)), trim(s.substring(comma + 1)), null);
        List<String> parts = words(s);
        if (parts.size() <= 1) return new Name(null, s, "", null);
        return new Name(null, parts.get(parts.size() - 1), String.join(" ", parts.subList(0, parts.size() - 1)), null);
    }

    static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        for (String w : SPACES.split(s)) if (!w.isEmpty()) out.add(w);
        return out;
    }

    private static final Pattern DOTTED_LETTER = Pattern.compile("\\p{L}\\.");

    /** "John Ronald" -> "J. R.", "J.R.R." -> "J. R. R.", "Jean-Paul" -> "J.-P." */
    static String initials(String given) {
        List<String> out = new ArrayList<>();
        for (String part : words(given)) {
            List<String> dotted = new ArrayList<>();
            Matcher m = DOTTED_LETTER.matcher(part);
            while (m.find()) dotted.add(m.group());
            if (!dotted.isEmpty() && String.join("", dotted).equals(part)) {
                List<String> upper = new ArrayList<>();
                for (String d : dotted) upper.add(d.toUpperCase(Locale.ROOT));
                out.add(String.join(" ", upper));
                continue;
            }
            List<String> segs = new ArrayList<>();
            for (String seg : part.split("-")) {
                if (!seg.isEmpty()) segs.add(String.valueOf(seg.charAt(0)).toUpperCase(Locale.ROOT) + ".");
            }
            out.add(String.join("-", segs));
        }
        return String.join(" ", out);
    }

    /** Family name (or literal) used for in-text citations and sorting. */
    static String familyName(Object name) {
        Name n = parts(name);
        return n.literal != null ? n.literal : n.family;
    }
}
