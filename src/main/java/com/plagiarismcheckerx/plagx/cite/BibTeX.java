package com.plagiarismcheckerx.plagx.cite;

import static com.plagiarismcheckerx.plagx.cite.Values.filled;
import static com.plagiarismcheckerx.plagx.cite.Values.list;
import static com.plagiarismcheckerx.plagx.cite.Values.lower;
import static com.plagiarismcheckerx.plagx.cite.Values.set;
import static com.plagiarismcheckerx.plagx.cite.Values.str;
import static com.plagiarismcheckerx.plagx.cite.Values.text;
import static com.plagiarismcheckerx.plagx.cite.Values.trim;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** BibTeX export and BibTeX/BibLaTeX import. */
final class BibTeX {
    private BibTeX() {}

    private static final Map<String, String> TO_BIBTEX_TYPE = new HashMap<>();
    private static final Map<String, String> FROM_BIBTEX_TYPE = new HashMap<>();
    private static final Map<String, String> ACCENTS = new HashMap<>();
    private static final List<String> MONTH_NAMES = Arrays.asList("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");

    static {
        String[] to = {
            "article-journal", "article", "article-magazine", "article", "article-newspaper", "article", "article", "article",
            "book", "book", "chapter", "incollection", "paper-conference", "inproceedings", "report", "techreport", "thesis", "phdthesis",
        };
        for (int i = 0; i < to.length; i += 2) TO_BIBTEX_TYPE.put(to[i], to[i + 1]);
        String[] from = {
            "article", "article-journal", "book", "book", "inbook", "chapter", "incollection", "chapter",
            "inproceedings", "paper-conference", "conference", "paper-conference", "techreport", "report", "report", "report",
            "phdthesis", "thesis", "mastersthesis", "thesis", "thesis", "thesis", "online", "webpage",
        };
        for (int i = 0; i < from.length; i += 2) FROM_BIBTEX_TYPE.put(from[i], from[i + 1]);
        String[] accents = {
            "\"", "\u0308", "'", "\u0301", "`", "\u0300", "^", "\u0302", "~", "\u0303", "=", "\u0304",
            ".", "\u0307", "c", "\u0327", "v", "\u030c", "u", "\u0306", "H", "\u030b",
        };
        for (int i = 0; i < accents.length; i += 2) ACCENTS.put(accents[i], accents[i + 1]);
    }

    // ------------------------------------------------------------------ export

    private static final Pattern SPECIAL = Pattern.compile("[\\\\&%$#_{}]");
    private static final Pattern DASHES = Pattern.compile(Values.WS + "*[-\u2013\u2014]+" + Values.WS + "*");
    private static final Pattern KEY_CHARS = Pattern.compile("[A-Za-z0-9_:.-]+");

    private static String escapeValue(String value) {
        return SPECIAL.matcher(value).replaceAll(m -> Matcher.quoteReplacement(m.group().equals("\\") ? "\\textbackslash{}" : "\\" + m.group()));
    }

    private static String bibName(Object name) {
        Names.Name n = Names.parts(name);
        if (n.literal != null) return "{" + n.literal + "}";
        List<String> parts = new ArrayList<>();
        for (String p : new String[] {n.family, n.suffix, n.given}) if (p != null && !p.isEmpty()) parts.add(p);
        return String.join(", ", parts);
    }

    private static String baseKey(Map<String, Object> ref) {
        String lead = filled(ref.get("author")) ? Names.familyName(list(ref.get("author")).get(0)) : "anon";
        String word = "";
        for (String w : Values.normalizeText(text(ref, "title")).split(" ")) {
            if (w.length() > 3 && !Arrays.asList("with", "from", "that", "this").contains(w)) {
                word = w;
                break;
            }
        }
        Integer year = Values.getDate(ref)[0];
        String key = Values.normalizeText(lead).replace(" ", "") + (set(year) ? year.toString() : "") + word;
        return key.isEmpty() ? "ref" : key;
    }

    private static String value(Object v) {
        return filled(v) ? str(v) : null;
    }

    private static String names(Map<String, Object> ref, String field) {
        List<String> out = new ArrayList<>();
        for (Object n : list(ref.get(field))) out.add(bibName(n));
        return String.join(" and ", out);
    }

    private static String entry(Map<String, Object> ref, String key) {
        String type = TO_BIBTEX_TYPE.getOrDefault(text(ref, "type"), "misc");
        Object container = ref.get("container-title");
        Integer[] d = Values.getDate(ref);
        String publisherField = "phdthesis".equals(type) ? "school" : "techreport".equals(type) ? "institution" : "publisher";
        boolean contained = Arrays.asList("article", "incollection", "inproceedings").contains(type);

        String[][] fields = {
            {"author", filled(ref.get("author")) ? names(ref, "author") : null},
            {"editor", filled(ref.get("editor")) ? names(ref, "editor") : null},
            {"title", value(ref.get("title"))},
            {"article".equals(type) ? "journal" : "booktitle", contained ? value(container) : null},
            {"howpublished", "misc".equals(type) ? value(container) : null},
            {"year", set(d[0]) ? d[0].toString() : null},
            {"month", set(d[1]) ? MONTH_NAMES.get(d[1] - 1) : null},
            {"volume", value(ref.get("volume"))},
            {"number", value(ref.get("issue"))},
            {"pages", filled(ref.get("page")) ? DASHES.matcher(str(ref.get("page"))).replaceAll("--") : null},
            {"edition", value(ref.get("edition"))},
            {publisherField, value(ref.get("publisher"))},
            {"address", value(ref.get("publisher-place"))},
            {"isbn", value(ref.get("ISBN"))},
            {"doi", value(ref.get("DOI"))},
            {"url", value(ref.get("URL"))},
        };
        List<String> body = new ArrayList<>();
        for (String[] f : fields) {
            if (f[1] == null || f[1].isEmpty()) continue;
            boolean raw = Arrays.asList("author", "editor", "url", "doi", "month").contains(f[0]);
            body.add("  " + f[0] + " = {" + (raw ? f[1] : escapeValue(f[1])) + "}");
        }
        return "@" + type + "{" + key + ",\n" + String.join(",\n", body) + "\n}";
    }

    static String toBibTeX(List<Map<String, Object>> refs) {
        Set<String> used = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (Map<String, Object> ref : refs) {
            String id = text(ref, "id");
            String key = !id.isEmpty() && KEY_CHARS.matcher(id).matches() ? id : baseKey(ref);
            if (used.contains(key)) {
                int n = 0;
                while (used.contains(key + (char) (97 + n))) n++;
                key += (char) (97 + n);
            }
            used.add(key);
            out.add(entry(ref, key));
        }
        return String.join("\n\n", out);
    }

    // ------------------------------------------------------------------ import

    private static final Pattern ACCENT_SHORT = Pattern.compile("\\\\([`'^\"~=.])" + Values.WS + "*\\{?\\\\?([A-Za-z])\\}?");
    private static final Pattern ACCENT_LETTER = Pattern.compile("\\\\([cvuH])" + Values.WS + "*\\{\\\\?([A-Za-z])\\}");
    private static final Pattern COMMAND = Pattern.compile("\\\\(?:emph|textit|textbf|textsc|mathrm)\\{([^{}]*)\\}");
    private static final Pattern ESCAPED = Pattern.compile("\\\\([&%$#_{}])");
    private static final Pattern BRACES = Pattern.compile("(?<!\\\\)[{}]");
    private static final Pattern SPACES = Pattern.compile(Values.WS + "+");

    /** Converts common LaTeX markup to plain Unicode text. */
    private static String cleanLatex(String value) {
        String s = ACCENT_SHORT.matcher(value).replaceAll(m -> Matcher.quoteReplacement(m.group(2) + ACCENTS.get(m.group(1))));
        s = ACCENT_LETTER.matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(2) + ACCENTS.get(m.group(1))));
        s = s.replace("\\textbackslash{}", "\\");
        s = COMMAND.matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        s = ESCAPED.matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        s = BRACES.matcher(s).replaceAll("").replace("~", " ");
        s = SPACES.matcher(s).replaceAll(" ");
        return trim(Normalizer.normalize(s, Normalizer.Form.NFC));
    }

    private static final Pattern AND = Pattern.compile(Values.WS + "+and" + Values.WS + "+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMA = Pattern.compile(",");

    private static List<String> splitTopLevel(String value, Pattern separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        Matcher m = separator.matcher(value);
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0 && m.region(i, value.length()).lookingAt()) {
                parts.add(current.toString());
                current.setLength(0);
                i = m.end();
                continue;
            }
            current.append(c);
            i++;
        }
        parts.add(current.toString());
        List<String> out = new ArrayList<>();
        for (String p : parts) if (!trim(p).isEmpty()) out.add(trim(p));
        return out;
    }

    private static final Pattern BRACED_NAME = Pattern.compile("\\{[^{}]*\\}");

    private static List<Object> parseNames(String value) {
        List<Object> out = new ArrayList<>();
        for (String raw : splitTopLevel(value, AND)) {
            Map<String, Object> name = new LinkedHashMap<>();
            if (BRACED_NAME.matcher(raw).matches()) {
                name.put("literal", cleanLatex(raw.substring(1, raw.length() - 1)));
                out.add(name);
                continue;
            }
            List<String> parts = new ArrayList<>();
            for (String p : splitTopLevel(raw, COMMA)) parts.add(cleanLatex(p));
            if (parts.size() == 3) {
                name.put("family", parts.get(0));
                name.put("suffix", parts.get(1));
                name.put("given", parts.get(2));
            } else if (parts.size() == 2) {
                name.put("family", parts.get(0));
                name.put("given", parts.get(1));
            } else {
                Names.Name n = Names.parts(cleanLatex(raw));
                name.put("family", n.family);
                name.put("given", n.given);
            }
            out.add(name);
        }
        return out;
    }

    private static int findClose(String src, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < src.length(); i++) {
            if (src.charAt(i) == open) {
                depth++;
            } else if (src.charAt(i) == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static final Pattern SPACE = Pattern.compile(Values.WS);
    private static final Pattern FIELD_NAME = Pattern.compile("[^" + Values.WS.substring(1, Values.WS.length() - 1) + "=,{}]+");
    private static final Pattern TOKEN = Pattern.compile("[^" + Values.WS.substring(1, Values.WS.length() - 1) + ",#}]+");

    private static boolean isSpace(char c) {
        return SPACE.matcher(String.valueOf(c)).matches();
    }

    private static Map<String, String> parseFields(String body, Map<String, String> macros) {
        Map<String, String> fields = new LinkedHashMap<>();
        int n = body.length();
        int i = 0;
        while (i < n) {
            while (i < n && (body.charAt(i) == ',' || isSpace(body.charAt(i)))) i++;
            Matcher name = FIELD_NAME.matcher(body).region(i, n);
            if (!name.lookingAt()) break;
            i = name.end();
            while (i < n && isSpace(body.charAt(i))) i++;
            if (i >= n || body.charAt(i) != '=') break;
            i++;

            StringBuilder value = new StringBuilder();
            while (true) {
                while (i < n && isSpace(body.charAt(i))) i++;
                if (i < n && body.charAt(i) == '{') {
                    int end = findClose(body, i, '{', '}');
                    if (end < 0) return fields;
                    value.append(body, i + 1, end);
                    i = end + 1;
                } else if (i < n && body.charAt(i) == '"') {
                    int depth = 0;
                    int j = i + 1;
                    for (; j < n; j++) {
                        char c = body.charAt(j);
                        if (c == '{') depth++;
                        else if (c == '}') depth--;
                        else if (c == '"' && depth == 0 && body.charAt(j - 1) != '\\') break;
                    }
                    value.append(body, i + 1, Math.min(j, n));
                    i = j + 1;
                } else {
                    Matcher token = i < n ? TOKEN.matcher(body).region(i, n) : null;
                    if (token == null || !token.lookingAt()) break;
                    value.append(macros.getOrDefault(lower(token.group()), token.group()));
                    i = token.end();
                }
                while (i < n && isSpace(body.charAt(i))) i++;
                if (i >= n || body.charAt(i) != '#') break;
                i++;
            }
            fields.put(lower(name.group()), value.toString());
        }
        return fields;
    }

    private static final Pattern ISO_DATE = Pattern.compile("^([0-9]{4})(?:-([0-9]{1,2}))?(?:-([0-9]{1,2}))?");
    private static final Pattern YEAR = Pattern.compile("[0-9]{4}");
    private static final Pattern HYPHENS = Pattern.compile(Values.WS + "*-+" + Values.WS + "*");
    private static final Pattern DOI_URL = Pattern.compile("^https?://(dx\\.)?doi\\.org/", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    private static String first(String... values) {
        for (String v : values) if (v != null && !v.isEmpty()) return v;
        return null;
    }

    private static Map<String, Object> toCsl(String type, String key, Map<String, String> fields) {
        java.util.function.Function<String, String> field = name -> {
            String v = fields.get(name);
            return v != null && !v.isEmpty() ? v : null;
        };
        java.util.function.Function<String, String> clean = name -> field.apply(name) == null ? null : cleanLatex(field.apply(name));

        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("id", key);
        ref.put("type", FROM_BIBTEX_TYPE.getOrDefault(type, field.apply("url") != null ? "webpage" : "document"));

        if (field.apply("author") != null) ref.put("author", parseNames(field.apply("author")));
        if (field.apply("editor") != null) ref.put("editor", parseNames(field.apply("editor")));
        if (field.apply("title") != null) ref.put("title", clean.apply("title"));
        String container = first(clean.apply("journal"), clean.apply("journaltitle"), clean.apply("booktitle"), clean.apply("howpublished"));
        if (container != null) ref.put("container-title", container);

        Matcher date = ISO_DATE.matcher(field.apply("date") != null ? field.apply("date") : "");
        boolean hasDate = date.find();
        Matcher yearMatch = YEAR.matcher(field.apply("year") != null ? field.apply("year") : "");
        String year = hasDate ? date.group(1) : yearMatch.find() ? yearMatch.group() : null;
        if (year != null) {
            String month = first(hasDate ? date.group(2) : null, field.apply("month"));
            int monthNumber = 0;
            if (month != null && DIGITS.matcher(month).matches()) {
                monthNumber = Integer.parseInt(month);
            } else if (month != null) {
                String key3 = lower(trim(month));
                monthNumber = MONTH_NAMES.indexOf(key3.length() > 3 ? key3.substring(0, 3) : key3) + 1;
            }
            List<Object> parts = new ArrayList<>();
            parts.add(Long.parseLong(year));
            if (monthNumber > 0) parts.add((long) monthNumber);
            if (hasDate && date.group(3) != null) parts.add(Long.parseLong(date.group(3)));
            Map<String, Object> issued = new LinkedHashMap<>();
            List<Object> dateParts = new ArrayList<>();
            dateParts.add(parts);
            issued.put("date-parts", dateParts);
            ref.put("issued", issued);
        }

        if (field.apply("volume") != null) ref.put("volume", clean.apply("volume"));
        if (field.apply("number") != null) ref.put("issue", clean.apply("number"));
        if (field.apply("pages") != null) ref.put("page", HYPHENS.matcher(clean.apply("pages")).replaceAll("-"));
        if (field.apply("edition") != null) ref.put("edition", clean.apply("edition"));
        String publisher = first(clean.apply("publisher"), clean.apply("school"), clean.apply("institution"), clean.apply("organization"));
        if (publisher != null) ref.put("publisher", publisher);
        if (field.apply("address") != null || field.apply("location") != null) {
            ref.put("publisher-place", first(clean.apply("address"), clean.apply("location")));
        }
        if (field.apply("isbn") != null) ref.put("ISBN", clean.apply("isbn"));
        if (field.apply("doi") != null) ref.put("DOI", DOI_URL.matcher(trim(field.apply("doi"))).replaceFirst(""));
        if (field.apply("url") != null) ref.put("URL", trim(field.apply("url")));
        if ("phdthesis".equals(type)) ref.put("genre", "Doctoral dissertation");
        if ("mastersthesis".equals(type)) ref.put("genre", "Master\u2019s thesis");
        return ref;
    }

    private static final Pattern HEAD = Pattern.compile("@" + Values.WS + "*([A-Za-z]+)" + Values.WS + "*([{(])");

    static List<Map<String, Object>> parse(String source) {
        Map<String, String> macros = new HashMap<>();
        for (int m = 0; m < MONTH_NAMES.size(); m++) macros.put(MONTH_NAMES.get(m), Integer.toString(m + 1));
        List<Map<String, Object>> refs = new ArrayList<>();
        int i = 0;
        while (true) {
            int at = source.indexOf('@', i);
            if (at < 0) break;
            Matcher head = HEAD.matcher(source).region(at, source.length());
            if (!head.lookingAt()) {
                i = at + 1;
                continue;
            }
            String type = lower(head.group(1));
            int openIndex = head.end() - 1;
            char open = head.group(2).charAt(0);
            int end = findClose(source, openIndex, open, open == '{' ? '}' : ')');
            if (end < 0) break;
            String body = source.substring(openIndex + 1, end);
            i = end + 1;

            if ("comment".equals(type) || "preamble".equals(type)) continue;
            if ("string".equals(type)) {
                macros.putAll(parseFields(body, macros));
                continue;
            }

            int comma = body.indexOf(',');
            String key = trim(comma < 0 ? body : body.substring(0, comma));
            Map<String, String> fields = comma < 0 ? new HashMap<>() : parseFields(body.substring(comma + 1), macros);
            refs.add(toCsl(type, key, fields));
        }
        return refs;
    }
}
