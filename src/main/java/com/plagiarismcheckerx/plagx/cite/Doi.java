package com.plagiarismcheckerx.plagx.cite;

import static com.plagiarismcheckerx.plagx.cite.Values.filled;
import static com.plagiarismcheckerx.plagx.cite.Values.list;
import static com.plagiarismcheckerx.plagx.cite.Values.lower;
import static com.plagiarismcheckerx.plagx.cite.Values.map;
import static com.plagiarismcheckerx.plagx.cite.Values.str;
import static com.plagiarismcheckerx.plagx.cite.Values.text;
import static com.plagiarismcheckerx.plagx.cite.Values.trim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DOI normalising, extraction and lookup (Crossref, with a doi.org fallback). */
final class Doi {
    private Doi() {}

    private static final Pattern PATTERN = Pattern.compile("10\\.[0-9]{4,9}/[^" + Values.WS.substring(1, Values.WS.length() - 1) + "\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX = Pattern.compile("^(?:https?://(?:dx\\.)?doi\\.org/|doi:" + Values.WS + "*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE = Pattern.compile("10\\.[0-9]{4,9}/" + Values.NON_WS + "+");
    private static final Pattern TRAILING = Pattern.compile("[.,;:]+$");

    private static final Map<String, String> CROSSREF_TYPES = new HashMap<>();
    private static final Map<String, String> ENTITIES = new HashMap<>();

    static {
        String[] types = {
            "journal-article", "article-journal", "book", "book", "monograph", "book", "edited-book", "book",
            "reference-book", "book", "book-chapter", "chapter", "book-section", "chapter", "book-part", "chapter",
            "reference-entry", "entry-encyclopedia", "proceedings-article", "paper-conference", "report", "report",
            "dissertation", "thesis", "posted-content", "article", "dataset", "dataset",
        };
        for (int i = 0; i < types.length; i += 2) CROSSREF_TYPES.put(types[i], types[i + 1]);
        String[] entities = {"amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", "\u00a0"};
        for (int i = 0; i < entities.length; i += 2) ENTITIES.put(entities[i], entities[i + 1]);
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String trimTrailing(String doi) {
        String s = TRAILING.matcher(doi).replaceFirst("");
        // Drop a closing bracket that has no opening partner inside the DOI.
        while (s.endsWith(")") || s.endsWith("]")) {
            char close = s.charAt(s.length() - 1);
            char open = close == ')' ? '(' : '[';
            if (count(s, open) >= count(s, close)) break;
            s = TRAILING.matcher(s.substring(0, s.length() - 1)).replaceFirst("");
        }
        return s;
    }

    /** JavaScript's decodeURIComponent; returns null for malformed input. */
    private static String decodeComponent(String s) {
        if (s.indexOf('%') < 0) return s;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '%') {
                out.append(c);
                i++;
                continue;
            }
            bytes.reset();
            while (i < s.length() && s.charAt(i) == '%') {
                if (i + 3 > s.length()) return null;
                try {
                    bytes.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                } catch (NumberFormatException e) {
                    return null;
                }
                i += 3;
            }
            try {
                out.append(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())));
            } catch (java.nio.charset.CharacterCodingException e) {
                return null;
            }
        }
        return out.toString();
    }

    static String normalize(String value) {
        String s = PREFIX.matcher(trim(value == null ? "" : value)).replaceFirst("");
        String decoded = decodeComponent(s);
        if (decoded != null) s = decoded;
        return BARE.matcher(s).matches() ? trimTrailing(s) : null;
    }

    static List<String> extract(String text) {
        Map<String, String> found = new LinkedHashMap<>();
        Matcher m = PATTERN.matcher(text == null ? "" : text);
        while (m.find()) {
            String doi = trimTrailing(m.group());
            found.putIfAbsent(lower(doi), doi);
        }
        return new ArrayList<>(found.values());
    }

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-f]+|#[0-9]+|[a-z]+);", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile(Values.WS + "+");

    /** Metadata often contains HTML/JATS markup and entities; returns plain text. */
    private static String cleanText(Object value) {
        if (value == null || "".equals(value)) return null;
        String s = TAG.matcher(str(value)).replaceAll("");
        s = ENTITY.matcher(s).replaceAll(m -> {
            String e = m.group(1);
            String replacement;
            if (e.charAt(0) != '#') {
                replacement = ENTITIES.getOrDefault(lower(e), m.group());
            } else {
                try {
                    int code = Character.toLowerCase(e.charAt(1)) == 'x' ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
                    replacement = new String(Character.toChars(code));
                } catch (IllegalArgumentException ex) {
                    replacement = m.group();
                }
            }
            return Matcher.quoteReplacement(replacement);
        });
        s = trim(SPACES.matcher(s).replaceAll(" "));
        return s.isEmpty() ? null : s;
    }

    private static List<Object> cleanNames(Object names) {
        List<Object> items = list(names);
        if (items.isEmpty()) return null;
        List<Object> out = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> a = map(item);
            if (a == null) a = Collections.emptyMap();
            Map<String, Object> name = new LinkedHashMap<>();
            if (filled(a.get("family"))) {
                name.put("family", cleanText(a.get("family")));
                String given = cleanText(a.get("given"));
                name.put("given", given != null ? given : "");
            } else {
                String literal = cleanText(filled(a.get("name")) ? a.get("name") : a.get("literal"));
                name.put("literal", literal != null ? literal : "");
            }
            out.add(name);
        }
        return out;
    }

    private static List<String> texts(Object value) {
        List<Object> values = value instanceof List ? list(value) : Collections.singletonList(value);
        List<String> out = new ArrayList<>();
        for (Object v : values) {
            String t = cleanText(v);
            if (t != null) out.add(t);
        }
        return out;
    }

    private static String firstOf(List<String> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    private static final Pattern ENDS_WITH_MARK = Pattern.compile("[:?!]$");

    private static String withSubtitle(String title, String subtitle) {
        if (title == null || title.isEmpty() || subtitle == null || subtitle.isEmpty()) return title;
        if (lower(title).contains(lower(subtitle))) return title;
        return ENDS_WITH_MARK.matcher(title).find() ? title + " " + subtitle : title + ": " + subtitle;
    }

    private static Map<String, Object> dateParts(Object issued) {
        Map<String, Object> o = map(issued);
        if (o == null || !(o.get("date-parts") instanceof List)) return null;
        List<Object> dp = list(o.get("date-parts"));
        if (dp.isEmpty() || !(dp.get(0) instanceof List)) return null;
        List<Object> parts = new ArrayList<>();
        for (Object n : list(dp.get(0))) if (n != null) parts.add(n);
        List<Object> wrapped = new ArrayList<>();
        wrapped.add(parts);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date-parts", wrapped);
        return out;
    }

    /** Drops null and empty-string values. */
    private static Map<String, Object> compact(Object[] fields) {
        Map<String, Object> ref = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            Object v = fields[i + 1];
            if (v != null && !"".equals(v)) ref.put((String) fields[i], v);
        }
        return ref;
    }

    static Map<String, Object> fromCrossref(Map<String, Object> work) {
        String type = CROSSREF_TYPES.getOrDefault(text(work, "type"), "article");
        Object issued = work.get("issued");
        Map<String, Object> io = map(issued);
        boolean hasIssued = false;
        if (io != null) {
            List<Object> dp = list(io.get("date-parts"));
            hasIssued = !dp.isEmpty() && !list(dp.get(0)).isEmpty() && filled(list(dp.get(0)).get(0));
        }
        if (!hasIssued) {
            issued = null;
            for (String k : new String[] {"published-print", "published-online", "created"}) {
                if (filled(work.get(k))) {
                    issued = work.get(k);
                    break;
                }
            }
        }
        // For chapters and proceedings Crossref may list [series, book]; the book is last.
        List<String> containers = texts(work.get("container-title"));
        boolean bookPart = "chapter".equals(type) || "paper-conference".equals(type);
        String publisher = cleanText(work.get("publisher"));
        List<Object> institution = list(work.get("institution"));
        if ("thesis".equals(type) && !institution.isEmpty() && map(institution.get(0)) != null && filled(map(institution.get(0)).get("name"))) {
            publisher = cleanText(map(institution.get(0)).get("name"));
        }
        return compact(new Object[] {
            "id", work.get("DOI"),
            "type", type,
            "title", withSubtitle(firstOf(texts(work.get("title"))), firstOf(texts(work.get("subtitle")))),
            "author", cleanNames(work.get("author")),
            "editor", cleanNames(work.get("editor")),
            "container-title", bookPart ? (containers.isEmpty() ? null : containers.get(containers.size() - 1)) : firstOf(containers),
            "issued", dateParts(issued),
            "volume", work.get("volume"),
            "issue", work.get("issue"),
            "page", work.get("page"),
            "publisher", publisher,
            "publisher-place", cleanText(work.get("publisher-location")),
            "ISBN", firstOf(texts(work.get("ISBN"))),
            "DOI", work.get("DOI"),
            "URL", work.get("URL"),
        });
    }

    /** Keeps the CSL-JSON fields Plagx Cite uses from doi.org content negotiation (DataCite, arXiv, mEDRA\u2026). */
    private static Map<String, Object> fromCsl(Map<String, Object> data) {
        return compact(new Object[] {
            "id", data.get("DOI"),
            "type", filled(data.get("type")) ? data.get("type") : "article",
            "title", firstOf(texts(data.get("title"))),
            "author", cleanNames(data.get("author")),
            "editor", cleanNames(data.get("editor")),
            "container-title", firstOf(texts(data.get("container-title"))),
            "issued", dateParts(data.get("issued")),
            "volume", data.get("volume"),
            "issue", data.get("issue"),
            "page", data.get("page"),
            "publisher", cleanText(data.get("publisher")),
            "DOI", data.get("DOI"),
            "URL", data.get("URL"),
        });
    }

    /** Percent-encodes UTF-8 bytes except letters, digits and the given safe characters. */
    private static String encode(String value, String safe) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int u = b & 0xff;
            char c = (char) u;
            if (u < 128 && (Character.isLetterOrDigit(c) || safe.indexOf(c) >= 0)) sb.append(c);
            else sb.append('%').append(String.format("%02X", u));
        }
        return sb.toString();
    }

    private static final class DefaultHttp implements PlagxCite.HttpGet {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

        @Override
        public PlagxCite.HttpResult get(String url, Map<String, String> headers) throws IOException {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "plagx-cite-java (+https://github.com/plagiarismcheckerx/plagx-cite-java)");
            headers.forEach(request::header);
            try {
                HttpResponse<byte[]> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
                return new PlagxCite.HttpResult(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        }
    }

    private static boolean ok(int status) {
        return status >= 200 && status < 300;
    }

    static Map<String, Object> lookup(String value, String mailto, PlagxCite.HttpGet http) throws DoiLookupException {
        String doi = normalize(value);
        if (doi == null) throw new IllegalArgumentException("Not a valid DOI: \"" + value + "\"");
        PlagxCite.HttpGet get = http != null ? http : new DefaultHttp();
        try {
            String url = "https://api.crossref.org/works/" + encode(doi, "-_.!~*'()");
            if (mailto != null && !mailto.isEmpty()) url += "?mailto=" + encode(mailto, "-_.!~*'()");
            PlagxCite.HttpResult res = get.get(url, Collections.singletonMap("Accept", "application/json"));
            if (ok(res.status)) {
                Map<String, Object> data = map(Json.parse(res.body));
                Map<String, Object> message = data != null ? map(data.get("message")) : null;
                return fromCrossref(message != null ? message : new LinkedHashMap<>());
            }
            if (res.status != 404) throw new DoiLookupException("DOI lookup failed (" + res.status + ") for " + doi, res.status);

            res = get.get("https://doi.org/" + encode(doi, ";,/?:@&=+$-_.!~*'()#"),
                Collections.singletonMap("Accept", "application/vnd.citationstyles.csl+json"));
            if (res.status == 404) throw new DoiLookupException("DOI not found: " + doi, 404);
            if (!ok(res.status)) throw new DoiLookupException("DOI lookup failed (" + res.status + ") for " + doi, res.status);
            Map<String, Object> data = map(Json.parse(res.body));
            return fromCsl(data != null ? data : new LinkedHashMap<>());
        } catch (IOException e) {
            throw new DoiLookupException("DOI lookup failed for " + doi + ": " + e.getMessage(), 0);
        } catch (IllegalArgumentException e) {
            throw new DoiLookupException("DOI lookup failed for " + doi + ": invalid JSON response", 0);
        }
    }
}
