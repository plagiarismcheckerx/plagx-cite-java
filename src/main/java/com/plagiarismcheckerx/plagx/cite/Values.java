package com.plagiarismcheckerx.plagx.cite;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Field access with JavaScript-style truthiness, plus the date, text and page helpers shared by the styles. */
final class Values {
    private Values() {}

    /** JavaScript's whitespace set, used instead of Java's ASCII-only {@code \s}. */
    private static final String WS_CHARS = "\\t\\n\\x0B\\f\\r \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff";
    static final String WS = "[" + WS_CHARS + "]";
    /** JavaScript's {@code \S}. */
    static final String NON_WS = "[^" + WS_CHARS + "]";

    static final String[] MONTHS = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    };

    static final String[] MONTHS_MLA = {
        "Jan.", "Feb.", "Mar.", "Apr.", "May", "June",
        "July", "Aug.", "Sept.", "Oct.", "Nov.", "Dec.",
    };

    /** False for null, false, "", 0 and empty lists (the string "0" counts as filled). */
    static boolean filled(Object value) {
        if (value == null || Boolean.FALSE.equals(value)) return false;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof List) return !((List<?>) value).isEmpty();
        return true;
    }

    /** The value as text: strings as is, whole numbers without ".0". */
    static String str(Object value) {
        if (value == null) return "";
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
            return value.toString();
        }
        if (value instanceof BigDecimal) return ((BigDecimal) value).stripTrailingZeros().toPlainString();
        return value.toString();
    }

    static String text(Map<String, Object> ref, String key) {
        Object v = ref.get(key);
        return filled(v) ? str(v) : "";
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object value) {
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    static Integer toInt(Object value) {
        if (!filled(value)) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return (int) Double.parseDouble(trim(str(value)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final Pattern LEADING = Pattern.compile("^" + WS + "+");
    private static final Pattern TRAILING = Pattern.compile(WS + "+$");

    /** JavaScript's String.prototype.trim(). */
    static String trim(String s) {
        return TRAILING.matcher(LEADING.matcher(s).replaceFirst("")).replaceFirst("");
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    /** Year, month and day from a CSL {@code issued} date or a plain {@code year} field. */
    static Integer[] getDate(Map<String, Object> ref) {
        Map<String, Object> issued = map(ref.get("issued"));
        if (issued != null) {
            List<Object> dp = list(issued.get("date-parts"));
            if (!dp.isEmpty() && dp.get(0) instanceof List) {
                List<Object> parts = list(dp.get(0));
                if (!parts.isEmpty() && filled(parts.get(0))) {
                    return new Integer[] {
                        toInt(parts.get(0)),
                        parts.size() > 1 ? toInt(parts.get(1)) : null,
                        parts.size() > 2 ? toInt(parts.get(2)) : null,
                    };
                }
            }
        }
        if (filled(ref.get("year"))) return new Integer[] {toInt(ref.get("year")), null, null};
        return new Integer[] {null, null, null};
    }

    static boolean set(Integer n) {
        return n != null && n != 0;
    }

    static String getYear(Map<String, Object> ref) {
        Integer year = getDate(ref)[0];
        return set(year) ? year.toString() : null;
    }

    private static final Pattern COMBINING = Pattern.compile("[\\u0300-\\u036f]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    /** Lowercase, strip accents and punctuation; used for comparisons and keys. */
    static String normalizeText(Object value) {
        String s = COMBINING.matcher(Normalizer.normalize(str(value), Normalizer.Form.NFKD)).replaceAll("");
        return trim(NON_ALNUM.matcher(lower(s)).replaceAll(" "));
    }

    private static final Pattern TERMINAL = Pattern.compile("[.?!][\"\u201d\u2019]?$");

    /** Period to append unless the text already ends with terminal punctuation. */
    static String dot(String value) {
        return TERMINAL.matcher(trim(value)).find() ? "" : ".";
    }

    private static final Pattern DASHES = Pattern.compile(WS + "*[-\u2013\u2014]+" + WS + "*");
    private static final Pattern DASH = Pattern.compile("[-\u2013\u2014]");

    static String pageRange(Object page, String dash) {
        return DASHES.matcher(trim(str(page))).replaceAll(dash);
    }

    static String pageRange(Object page) {
        return pageRange(page, "\u2013");
    }

    static boolean isPageRange(Object page) {
        return DASH.matcher(str(page)).find();
    }

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    static String ordinal(Object value) {
        int n;
        if (value instanceof Number && ((Number) value).doubleValue() == Math.rint(((Number) value).doubleValue())) {
            n = ((Number) value).intValue();
        } else if (value instanceof String && DIGITS.matcher(trim((String) value)).matches()) {
            n = Integer.parseInt(trim((String) value));
        } else {
            return str(value);
        }
        if (n % 100 >= 11 && n % 100 <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    /** Preferred link for a reference: a valid DOI first, then URL. */
    static String link(Map<String, Object> ref) {
        String doi = filled(ref.get("DOI")) ? Doi.normalize(str(ref.get("DOI"))) : null;
        if (doi != null) return "https://doi.org/" + doi;
        return text(ref, "URL");
    }
}
