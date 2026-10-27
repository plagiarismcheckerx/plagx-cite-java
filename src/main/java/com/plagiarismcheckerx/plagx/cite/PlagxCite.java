/*!
 * Plagx Cite: a free academic writing utility by Plagiarism Checker X.
 * https://plagiarismcheckerx.com/ \u00b7 MIT License
 */
package com.plagiarismcheckerx.plagx.cite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * APA 7 and MLA 9 citation generator and reference checker.
 *
 * <p>References are CSL-JSON objects as {@code Map<String, Object>}, for example a Zotero or Crossref export
 * read with {@link #parseJson(String)}.
 */
public final class PlagxCite {
    private PlagxCite() {}

    /** Version of this library. */
    public static final String VERSION = "0.2.2";

    private static final Map<String, Style> STYLES = new LinkedHashMap<>();

    static {
        STYLES.put("apa", new Apa());
        STYLES.put("mla", new Mla());
    }

    /** Names of the built-in citation styles: {@code apa}, {@code mla}. */
    public static final List<String> STYLE_NAMES = Collections.unmodifiableList(new ArrayList<>(STYLES.keySet()));

    /** Response from an {@link HttpGet}. */
    public static final class HttpResult {
        final int status;
        final String body;

        /**
         * Creates a response.
         *
         * @param status HTTP status code
         * @param body response body as text
         */
        public HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /** Replaces the built-in HTTP client for DOI lookups (for tests, proxies or your own client). */
    @FunctionalInterface
    public interface HttpGet {
        /**
         * Sends a GET request.
         *
         * @param url the URL
         * @param headers request headers
         * @return status and body
         * @throws IOException on a network error
         */
        HttpResult get(String url, Map<String, String> headers) throws IOException;
    }

    private static Style style(String name) {
        Style style = STYLES.get(String.valueOf(name).toLowerCase(Locale.ROOT));
        if (style == null) {
            throw new IllegalArgumentException("Unknown citation style \"" + name + "\". Available: " + String.join(", ", STYLE_NAMES) + ".");
        }
        return style;
    }

    /**
     * Reads CSL-JSON text: an array of references or a single reference.
     *
     * @param json CSL-JSON text
     * @return the references
     * @throws IllegalArgumentException when the text is not a JSON array or object
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseJson(String json) {
        Object data = Json.parse(json);
        List<Map<String, Object>> refs = new ArrayList<>();
        if (data instanceof Map) {
            refs.add((Map<String, Object>) data);
        } else if (data instanceof List) {
            for (Object item : (List<Object>) data) if (item instanceof Map) refs.add((Map<String, Object>) item);
        } else {
            throw new IllegalArgumentException("Expected a CSL-JSON array or object.");
        }
        return refs;
    }

    /**
     * Formats one reference as an APA plain-text reference-list entry.
     *
     * @param ref CSL-JSON reference
     * @return the entry
     */
    public static String formatReference(Map<String, Object> ref) {
        return formatReference(ref, "apa", "text");
    }

    /**
     * Formats one reference as a plain-text reference-list entry.
     *
     * @param ref CSL-JSON reference
     * @param style {@code "apa"} or {@code "mla"}
     * @return the entry
     */
    public static String formatReference(Map<String, Object> ref, String style) {
        return formatReference(ref, style, "text");
    }

    /**
     * Formats one reference as a reference-list entry.
     *
     * @param ref CSL-JSON reference
     * @param style {@code "apa"} or {@code "mla"}
     * @param format {@code "text"}, {@code "html"} (titles in &lt;i&gt;, text escaped) or {@code "markdown"} (titles in *\u2026*)
     * @return the entry
     */
    public static String formatReference(Map<String, Object> ref, String style, String format) {
        return style(style).reference(ref, new Formatter(format));
    }

    /**
     * Sorts references and formats each one as APA plain text.
     *
     * @param refs CSL-JSON references
     * @return the entries
     */
    public static List<String> formatBibliography(List<Map<String, Object>> refs) {
        return formatBibliography(refs, "apa", "text");
    }

    /**
     * Sorts references and formats each one.
     *
     * @param refs CSL-JSON references
     * @param style {@code "apa"} or {@code "mla"}
     * @param format {@code "text"}, {@code "html"} or {@code "markdown"}
     * @return the entries
     */
    public static List<String> formatBibliography(List<Map<String, Object>> refs, String style, String format) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> ref : sortReferences(refs)) out.add(formatReference(ref, style, format));
        return out;
    }

    /**
     * APA parenthetical in-text citation, e.g. "(Smith &amp; Lee, 2020)".
     *
     * @param ref CSL-JSON reference
     * @return the citation
     */
    public static String citeInText(Map<String, Object> ref) {
        return citeInText(ref, "apa", null, false);
    }

    /**
     * Parenthetical in-text citation.
     *
     * @param ref CSL-JSON reference
     * @param style {@code "apa"} or {@code "mla"}
     * @return the citation
     */
    public static String citeInText(Map<String, Object> ref, String style) {
        return citeInText(ref, style, null, false);
    }

    /**
     * In-text citation, e.g. "(Smith &amp; Lee, 2020, p. 4)" or "Smith and Lee (2020)".
     *
     * @param ref CSL-JSON reference
     * @param style {@code "apa"} or {@code "mla"}
     * @param page page or page range, or null
     * @param narrative true for "Smith and Lee (2020)", false for "(Smith &amp; Lee, 2020)"
     * @return the citation
     */
    public static String citeInText(Map<String, Object> ref, String style, String page, boolean narrative) {
        return style(style).inText(ref, page, narrative);
    }

    private static final Pattern ARTICLE = Pattern.compile("^(a|an|the)" + Values.WS + "+", Pattern.CASE_INSENSITIVE);

    private static String sortKey(Map<String, Object> ref) {
        String lead = Values.filled(ref.get("author"))
            ? Names.familyName(Values.list(ref.get("author")).get(0))
            : ARTICLE.matcher(Values.text(ref, "title")).replaceFirst("");
        String year = Values.getYear(ref);
        return Values.normalizeText(lead) + " " + (year != null ? year : "0000") + " " + Values.normalizeText(Values.text(ref, "title"));
    }

    /**
     * Returns a new list sorted by first author (or title), year, then title.
     *
     * @param refs CSL-JSON references
     * @return the sorted copy
     */
    public static List<Map<String, Object>> sortReferences(List<Map<String, Object>> refs) {
        List<Map<String, Object>> sorted = new ArrayList<>(refs);
        sorted.sort(Comparator.comparing(PlagxCite::sortKey));
        return sorted;
    }

    /**
     * Converts references to BibTeX.
     *
     * @param refs CSL-JSON references
     * @return BibTeX text
     */
    public static String toBibTeX(List<Map<String, Object>> refs) {
        return BibTeX.toBibTeX(refs);
    }

    /**
     * Converts one reference to BibTeX.
     *
     * @param ref CSL-JSON reference
     * @return BibTeX text
     */
    public static String toBibTeX(Map<String, Object> ref) {
        return BibTeX.toBibTeX(Collections.singletonList(ref));
    }

    /**
     * Parses BibTeX/BibLaTeX text into CSL-JSON references. Handles {@code @string} macros, {@code #} concatenation,
     * common LaTeX accents and BibLaTeX {@code date} / {@code journaltitle} fields.
     *
     * @param source BibTeX text
     * @return the references
     */
    public static List<Map<String, Object>> parseBibTeX(String source) {
        return BibTeX.parse(source);
    }

    /**
     * Returns a bare DOI ("10.xxxx/...") from a DOI, doi: string or doi.org URL.
     *
     * @param value the DOI text
     * @return the bare DOI, or null if invalid
     */
    public static String normalizeDoi(String value) {
        return Doi.normalize(value);
    }

    /**
     * Whether the value is a valid DOI.
     *
     * @param value the DOI text
     * @return true when valid
     */
    public static boolean isDoi(String value) {
        return Doi.normalize(value) != null;
    }

    /**
     * Finds every DOI in a block of text (unique, in order of appearance).
     *
     * @param text any text
     * @return the DOIs
     */
    public static List<String> extractDois(String text) {
        return Doi.extract(text);
    }

    /**
     * Maps a Crossref "work" record to CSL-JSON.
     *
     * @param work the Crossref {@code message} object
     * @return the reference
     */
    public static Map<String, Object> fromCrossref(Map<String, Object> work) {
        return Doi.fromCrossref(work);
    }

    /**
     * Looks up a DOI and returns CSL-JSON. Tries the public Crossref API first, then doi.org content negotiation
     * (covers DataCite DOIs such as arXiv and Zenodo). No API key is needed.
     *
     * @param doi DOI, doi: string or doi.org URL
     * @return the reference
     * @throws IllegalArgumentException the DOI is invalid
     * @throws DoiLookupException the DOI can't be found or the lookup failed
     */
    public static Map<String, Object> lookupDoi(String doi) throws DoiLookupException {
        return Doi.lookup(doi, null, null);
    }

    /**
     * Looks up a DOI and returns CSL-JSON.
     *
     * @param doi DOI, doi: string or doi.org URL
     * @param mailto optional email for Crossref's polite pool, or null
     * @param http optional HTTP client, or null for the built-in one
     * @return the reference
     * @throws IllegalArgumentException the DOI is invalid
     * @throws DoiLookupException the DOI can't be found or the lookup failed
     */
    public static Map<String, Object> lookupDoi(String doi, String mailto, HttpGet http) throws DoiLookupException {
        return Doi.lookup(doi, mailto, http);
    }

    /**
     * Finds references that share a DOI, or the same title and first author.
     *
     * @param refs CSL-JSON references
     * @return the duplicate groups
     */
    public static List<DuplicateGroup> findDuplicates(List<Map<String, Object>> refs) {
        return Checks.findDuplicates(refs);
    }

    /**
     * Checks a reference list offline. Errors: duplicates and invalid DOIs. Warnings: missing titles, authors or years.
     *
     * @param refs CSL-JSON references
     * @return the result
     */
    public static CheckResult checkReferences(List<Map<String, Object>> refs) {
        return Checks.check(refs, false, null, null);
    }

    /**
     * Checks a reference list. With {@code checkDois} every DOI is also looked up online (Crossref / doi.org)
     * and DOIs that can't be found are reported as errors.
     *
     * @param refs CSL-JSON references
     * @param checkDois look up every DOI online
     * @param mailto optional email for Crossref's polite pool, or null
     * @param http optional HTTP client, or null for the built-in one
     * @return the result
     */
    public static CheckResult checkReferences(List<Map<String, Object>> refs, boolean checkDois, String mailto, HttpGet http) {
        return Checks.check(refs, checkDois, mailto, http);
    }
}
