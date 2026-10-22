// Same cases and expected output as the JavaScript package (plagx-cite on npm).
package com.plagiarismcheckerx.plagx.cite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PlagxCiteTest {
    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(String json) {
        return (Map<String, Object>) Json.parse(json);
    }

    static Map<String, Object> with(Map<String, Object> ref, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(ref);
        copy.put(key, value);
        return copy;
    }

    static final Map<String, Object> ARTICLE = obj("{\"type\": \"article-journal\", \"title\": \"Deep learning for text similarity\","
        + " \"author\": [{\"family\": \"Smith\", \"given\": \"John Allen\"}, {\"family\": \"Lee\", \"given\": \"Kate\"}],"
        + " \"issued\": {\"date-parts\": [[2020]]}, \"container-title\": \"Journal of Academic Writing\","
        + " \"volume\": \"12\", \"issue\": \"3\", \"page\": \"45-67\", \"DOI\": \"10.1234/jaw.2020.045\"}");

    static final Map<String, Object> BOOK = obj("{\"type\": \"book\", \"title\": \"Writing research papers\","
        + " \"author\": [{\"family\": \"Brown\", \"given\": \"Mary\"}], \"issued\": {\"date-parts\": [[2018]]},"
        + " \"publisher\": \"Academic Press\", \"edition\": \"2\"}");

    static final Map<String, Object> CHAPTER = obj("{\"type\": \"chapter\", \"title\": \"Citing sources well\","
        + " \"author\": [{\"family\": \"Khan\", \"given\": \"Ali\"}, {\"family\": \"Ng\", \"given\": \"Wei\"}, {\"family\": \"Ruiz\", \"given\": \"Ana\"}],"
        + " \"editor\": [{\"family\": \"Green\", \"given\": \"Paul\"}, {\"family\": \"White\", \"given\": \"Sara Jane\"}],"
        + " \"container-title\": \"Handbook of scholarly writing\", \"page\": \"101-120\", \"publisher\": \"Routledge\","
        + " \"issued\": {\"date-parts\": [[2019]]}}");

    static final Map<String, Object> WEBPAGE = obj("{\"type\": \"webpage\", \"title\": \"How to avoid plagiarism\","
        + " \"author\": [{\"literal\": \"Writing Centre\"}], \"container-title\": \"University Library\","
        + " \"issued\": {\"date-parts\": [[2023, 3, 15]]}, \"URL\": \"https://example.com/post\"}");

    @Test
    void apaReferenceListEntries() {
        assertEquals("Smith, J. A., & Lee, K. (2020). Deep learning for text similarity. Journal of Academic Writing, "
            + "12(3), 45\u201367. https://doi.org/10.1234/jaw.2020.045", PlagxCite.formatReference(ARTICLE));
        assertEquals("Brown, M. (2018). Writing research papers (2nd ed.). Academic Press.", PlagxCite.formatReference(BOOK));
        assertEquals("Khan, A., Ng, W., & Ruiz, A. (2019). Citing sources well. In P. Green & S. J. White (Eds.), "
            + "Handbook of scholarly writing (pp. 101\u2013120). Routledge.", PlagxCite.formatReference(CHAPTER));
        assertEquals("Writing Centre. (2023, March 15). How to avoid plagiarism. University Library. https://example.com/post",
            PlagxCite.formatReference(WEBPAGE));
    }

    @Test
    void apaWithoutAuthorOrDate() {
        assertEquals("Citation basics. (n.d.). https://example.com",
            PlagxCite.formatReference(obj("{\"type\": \"webpage\", \"title\": \"Citation basics\", \"URL\": \"https://example.com\"}")));
    }

    @Test
    void apaManyAuthorsUseEllipsis() {
        List<Object> authors = new ArrayList<>();
        for (int i = 1; i <= 22; i++) authors.add(obj("{\"family\": \"A" + i + "\", \"given\": \"B\"}"));
        Map<String, Object> ref = obj("{\"type\": \"article-journal\", \"title\": \"T\", \"issued\": {\"date-parts\": [[2021]]}}");
        ref.put("author", authors);
        String out = PlagxCite.formatReference(ref);
        assertTrue(Pattern.compile("^A1, B\\., .*A19, B\\., \\. \\. \\. A22, B\\. \\(2021\\)").matcher(out).find(), out);
        assertFalse(out.contains("A20"));
    }

    @Test
    void mlaWorksCitedEntries() {
        assertEquals("Smith, John Allen, and Kate Lee. \u201cDeep learning for text similarity.\u201d Journal of Academic Writing, "
            + "vol. 12, no. 3, 2020, pp. 45-67, https://doi.org/10.1234/jaw.2020.045.", PlagxCite.formatReference(ARTICLE, "mla"));
        assertEquals("Brown, Mary. Writing research papers. 2nd ed., Academic Press, 2018.", PlagxCite.formatReference(BOOK, "mla"));
        assertEquals("Khan, Ali, et al. \u201cCiting sources well.\u201d Handbook of scholarly writing, edited by Paul Green and "
            + "Sara Jane White, Routledge, 2019, pp. 101-120.", PlagxCite.formatReference(CHAPTER, "mla"));
        assertEquals("Writing Centre. \u201cHow to avoid plagiarism.\u201d University Library, 15 Mar. 2023, https://example.com/post.",
            PlagxCite.formatReference(WEBPAGE, "mla"));
    }

    @Test
    void htmlAndMarkdownOutput() {
        assertEquals("Brown, M. (2018). <i>Writing research papers</i> (2nd ed.). Academic Press.", PlagxCite.formatReference(BOOK, "apa", "html"));
        assertEquals("Brown, M. (2018). *Writing research papers* (2nd ed.). Academic Press.", PlagxCite.formatReference(BOOK, "apa", "markdown"));
        assertTrue(PlagxCite.formatReference(with(BOOK, "title", "Tom & <Jerry>"), "apa", "html").contains("<i>Tom &amp; &lt;Jerry&gt;</i>"));
    }

    @Test
    void inTextCitations() {
        assertEquals("(Smith & Lee, 2020, p. 50)", PlagxCite.citeInText(ARTICLE, "apa", "50", false));
        assertEquals("Smith and Lee (2020)", PlagxCite.citeInText(ARTICLE, "apa", null, true));
        assertEquals("(Khan et al., 2019, pp. 101\u2013103)", PlagxCite.citeInText(CHAPTER, "apa", "101-103", false));
        assertEquals("(Smith and Lee 50)", PlagxCite.citeInText(ARTICLE, "mla", "50", false));
        assertEquals("(Khan et al.)", PlagxCite.citeInText(CHAPTER, "mla"));
        assertEquals("(\u201cCitation guide,\u201d 2021)", PlagxCite.citeInText(obj("{\"title\": \"Citation guide\", \"year\": 2021}")));
        assertEquals("\u201cCitation guide\u201d (n.d.)", PlagxCite.citeInText(obj("{\"title\": \"Citation guide\"}"), "apa", null, true));
    }

    @Test
    void bibliographyIsSorted() {
        List<String> out = PlagxCite.formatBibliography(Arrays.asList(ARTICLE, CHAPTER, BOOK));
        List<String> leads = new ArrayList<>();
        for (String s : out) leads.add(s.split(",")[0]);
        assertEquals(Arrays.asList("Brown", "Khan", "Smith"), leads);
    }

    @Test
    void unknownStyleOrFormat() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PlagxCite.formatReference(BOOK, "xyz")).getMessage()
            .contains("Unknown citation style"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PlagxCite.formatReference(BOOK, "apa", "pdf")).getMessage()
            .contains("Unknown output format"));
    }

    @Test
    void invalidDoiIsNotALinkAndFullUrlNotDoubled() {
        Map<String, Object> base = obj("{\"type\": \"book\", \"title\": \"T\", \"author\": [{\"family\": \"A\", \"given\": \"B\"}], \"year\": 2001}");
        assertEquals("A, B. (2001). T.", PlagxCite.formatReference(with(base, "DOI", "not-a-doi")));
        assertEquals("A, B. (2001). T. https://doi.org/10.1234/x", PlagxCite.formatReference(with(base, "DOI", "https://doi.org/10.1234/x")));
    }

    @Test
    void bibTeXExport() {
        assertEquals(String.join("\n",
            "@article{smith2020deep,",
            "  author = {Smith, John Allen and Lee, Kate},",
            "  title = {Deep learning for text similarity},",
            "  journal = {Journal of Academic Writing},",
            "  year = {2020},",
            "  volume = {12},",
            "  number = {3},",
            "  pages = {45--67},",
            "  doi = {10.1234/jaw.2020.045}",
            "}"), PlagxCite.toBibTeX(ARTICLE));
        assertTrue(PlagxCite.toBibTeX(with(BOOK, "title", "50% of R&D")).contains("title = {50\\% of R\\&D}"));
        Matcher m = Pattern.compile("@book\\{(\\w+),").matcher(PlagxCite.toBibTeX(Arrays.asList(BOOK, BOOK)));
        List<String> keys = new ArrayList<>();
        while (m.find()) keys.add(m.group(1));
        assertEquals(Arrays.asList("brown2018writing", "brown2018writinga"), keys);
    }

    @Test
    void bibTeXRoundTrip() {
        List<Map<String, Object>> originals = Arrays.asList(ARTICLE, BOOK, CHAPTER);
        List<Map<String, Object>> parsed = PlagxCite.parseBibTeX(PlagxCite.toBibTeX(originals));
        for (int i = 0; i < originals.size(); i++) {
            parsed.get(i).remove("id");
            assertEquals(originals.get(i), parsed.get(i));
        }
    }

    @Test
    void bibTeXImportLatexMacrosAndOrganisations() {
        List<Map<String, Object>> refs = PlagxCite.parseBibTeX(String.join("\n",
            "@comment{ignored}",
            "@string{jaw = \"Journal of Academic Writing\"}",
            "@Article{muller21,",
            "  author  = {M{\\\"u}ller, J{\\\"o}rg and {World Health Organization}},",
            "  title   = \"{T}he {\\&} problem\",",
            "  journal = jaw,",
            "  year    = 2021, month = mar,",
            "  pages   = \"1--9\",",
            "  doi     = {https://doi.org/10.5555/abc}",
            "}"));
        assertEquals(1, refs.size());
        Map<String, Object> ref = refs.get(0);
        assertEquals("[{\"family\":\"M\u00fcller\",\"given\":\"J\u00f6rg\"},{\"literal\":\"World Health Organization\"}]", Json.stringify(ref.get("author")));
        assertEquals("The & problem", ref.get("title"));
        assertEquals("Journal of Academic Writing", ref.get("container-title"));
        assertEquals("{\"date-parts\":[[2021,3]]}", Json.stringify(ref.get("issued")));
        assertEquals("1-9", ref.get("page"));
        assertEquals("10.5555/abc", ref.get("DOI"));
    }

    @Test
    void doiNormalisingAndExtraction() {
        assertEquals("10.1234/ABC.5", PlagxCite.normalizeDoi("https://doi.org/10.1234/ABC.5"));
        assertEquals("10.1234/abc", PlagxCite.normalizeDoi("doi: 10.1234/abc"));
        assertNull(PlagxCite.normalizeDoi("not a doi"));
        assertFalse(PlagxCite.isDoi(null));
        assertEquals(Arrays.asList("10.1000/xyz123", "10.5555/a(b)c"),
            PlagxCite.extractDois("See (doi:10.1000/xyz123). Also https://doi.org/10.1000/xyz123 and 10.5555/a(b)c."));
    }

    @Test
    void doiLookupMapsCrossref() throws Exception {
        List<String> urls = new ArrayList<>();
        PlagxCite.HttpGet http = (url, headers) -> {
            urls.add(url);
            return new PlagxCite.HttpResult(200, "{\"message\": {\"type\": \"journal-article\", \"title\": [\"Deep learning for text similarity\"],"
                + " \"author\": [{\"given\": \"John Allen\", \"family\": \"Smith\"}, {\"given\": \"Kate\", \"family\": \"Lee\"}],"
                + " \"container-title\": [\"Journal of Academic Writing\"], \"issued\": {\"date-parts\": [[2020]]},"
                + " \"volume\": \"12\", \"issue\": \"3\", \"page\": \"45-67\", \"DOI\": \"10.1234/jaw.2020.045\","
                + " \"URL\": \"https://doi.org/10.1234/jaw.2020.045\", \"publisher\": \"Example Publisher\"}}");
        };
        Map<String, Object> ref = PlagxCite.lookupDoi("doi:10.1234/jaw.2020.045", "a@b.org", http);
        assertEquals(Arrays.asList("https://api.crossref.org/works/10.1234%2Fjaw.2020.045?mailto=a%40b.org"), urls);
        assertEquals(PlagxCite.formatReference(ARTICLE), PlagxCite.formatReference(ref));

        PlagxCite.HttpGet notFound = (url, headers) -> new PlagxCite.HttpResult(404, "");
        DoiLookupException e = assertThrows(DoiLookupException.class, () -> PlagxCite.lookupDoi("10.1234/none", null, notFound));
        assertTrue(e.getMessage().startsWith("DOI not found"));
        assertEquals(404, e.getStatusCode());
        assertThrows(IllegalArgumentException.class, () -> PlagxCite.lookupDoi("nope", null, notFound));
    }

    @Test
    void doiLookupFallsBackToDoiOrg() throws Exception {
        List<String> calls = new ArrayList<>();
        PlagxCite.HttpGet http = (url, headers) -> {
            calls.add(url + " " + headers.get("Accept"));
            if (url.startsWith("https://api.crossref.org/")) return new PlagxCite.HttpResult(404, "");
            return new PlagxCite.HttpResult(200, "{\"type\": \"article\", \"title\": \"Attention is all you need\","
                + " \"author\": [{\"family\": \"Vaswani\", \"given\": \"Ashish\"}, {\"family\": \"Shazeer\", \"given\": \"Noam\"}],"
                + " \"issued\": {\"date-parts\": [[2017]]}, \"publisher\": \"arXiv\", \"DOI\": \"10.48550/ARXIV.1706.03762\", \"abstract\": \"ignored\"}");
        };
        Map<String, Object> ref = PlagxCite.lookupDoi("10.48550/arXiv.1706.03762", null, http);
        assertEquals("https://doi.org/10.48550/arXiv.1706.03762 application/vnd.citationstyles.csl+json", calls.get(1));
        assertFalse(ref.containsKey("abstract"));
        assertEquals("Vaswani, A., & Shazeer, N. (2017). Attention is all you need. arXiv. https://doi.org/10.48550/ARXIV.1706.03762",
            PlagxCite.formatReference(ref));
        assertEquals("Vaswani, Ashish, and Noam Shazeer. \u201cAttention is all you need.\u201d arXiv, 2017, https://doi.org/10.48550/ARXIV.1706.03762.",
            PlagxCite.formatReference(ref, "mla"));
    }

    @Test
    void crossrefMarkupSubtitleAndBookTitle() {
        Map<String, Object> ref = PlagxCite.fromCrossref(obj("{\"type\": \"book-chapter\", \"title\": [\"Optuna\"],"
            + " \"subtitle\": [\"A next-generation <i>hyperparameter</i> framework\"],"
            + " \"container-title\": [\"Lecture Notes in Computer Science\", \"Knowledge Discovery &amp; Data Mining\"],"
            + " \"author\": [{\"family\": \"Akiba\", \"given\": \"Takuya\"}], \"publisher\": \"ACM\", \"DOI\": \"10.1145/1\"}"));
        assertEquals("Optuna: A next-generation hyperparameter framework", ref.get("title"));
        assertEquals("Knowledge Discovery & Data Mining", ref.get("container-title"));
        assertEquals("Nature", PlagxCite.fromCrossref(obj("{\"type\": \"journal-article\", \"container-title\": [\"Nature\", \"Nat.\"]}")).get("container-title"));
    }

    @Test
    void duplicateDetection() {
        List<DuplicateGroup> groups = PlagxCite.findDuplicates(Arrays.asList(
            ARTICLE,
            BOOK,
            with(with(ARTICLE, "title", "A different title"), "DOI", "https://doi.org/10.1234/JAW.2020.045"),
            with(BOOK, "title", "Writing Research Papers!"),
            with(BOOK, "author", Json.parse("[{\"family\": \"Someone\", \"given\": \"Else\"}]"))));
        assertEquals("[{\"indexes\":[0,2],\"reasons\":[\"doi\"]},{\"indexes\":[1,3],\"reasons\":[\"title\"]}]", Json.stringify(groups));
        assertTrue(PlagxCite.findDuplicates(Arrays.asList(ARTICLE, BOOK)).isEmpty());
    }

    @Test
    void checkReferences() {
        List<Map<String, Object>> refs = PlagxCite.parseBibTeX(String.join("\n",
            "@article{smith2020, author={Smith, John and Lee, Kate}, title={Deep learning for text similarity},",
            "  journal={Journal of Academic Writing}, year=2020, volume=12, number=3, pages={45--67}}",
            "@book{brown2018, author={Brown, Mary}, title={Writing research papers}, publisher={Academic Press}, year=2018}",
            "@article{smithdup, author={Smith, J.}, title={Deep Learning for Text Similarity!}, journal={JAW}, year=2020}",
            "@misc{bad, title={Untitled web page}, doi={not-a-doi}}"));
        CheckResult result = PlagxCite.checkReferences(refs);
        assertEquals(Arrays.asList(4, 2, 2), Arrays.asList(result.getReferences(), result.getErrors(), result.getWarnings()));
        List<String> codes = new ArrayList<>();
        for (CheckIssue x : result.getIssues()) codes.add(x.getCode());
        assertEquals(Arrays.asList("duplicate", "invalid-doi", "missing-author", "missing-year"), codes);
        assertEquals("Possible duplicate (title match) of smith2020: smith2020, smithdup", result.getIssues().get(0).getMessage());

        CheckResult missing = PlagxCite.checkReferences(
            Arrays.asList(obj("{\"title\": \"X\", \"author\": [\"A B\"], \"year\": 2000, \"DOI\": \"10.1234/missing\"}")),
            true, null, (url, headers) -> new PlagxCite.HttpResult(404, ""));
        assertEquals(1, missing.getIssues().size());
        assertEquals("doi-not-found", missing.getIssues().get(0).getCode());
    }

    @Test
    void referencesBuiltInCode() {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("family", "Brown");
        name.put("given", "Mary");
        Map<String, Object> issued = new LinkedHashMap<>();
        issued.put("date-parts", Arrays.asList(Arrays.asList(2018, 4)));
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("type", "book");
        ref.put("title", "Writing research papers");
        ref.put("author", Arrays.asList(name));
        ref.put("issued", issued);
        ref.put("edition", 3);
        ref.put("volume", 2.0);
        ref.put("publisher", "Academic Press");
        assertEquals("Brown, M. (2018). Writing research papers (3rd ed.). Academic Press.", PlagxCite.formatReference(ref));
        assertEquals("Brown, Mary. Writing research papers. 3rd ed., vol. 2, Academic Press, Apr. 2018.", PlagxCite.formatReference(ref, "mla"));
        assertTrue(PlagxCite.toBibTeX(ref).contains("month = {apr}"));
    }

    @Test
    void jsonRoundTrip() {
        String text = "{\"a\":[1,2.5,\"x\\\"y\",true,null],\"b\":{\"c\":\"\u00fc\\n\"}}";
        assertEquals(text, Json.stringify(Json.parse(text)));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{bad}"));
        assertEquals(2, PlagxCite.parseJson("[{\"title\": \"A\"}, {\"title\": \"B\"}]").size());
        assertThrows(IllegalArgumentException.class, () -> PlagxCite.parseJson("42"));
    }
}
