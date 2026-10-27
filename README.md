# Plagx Cite for Java

*A free academic writing tool by Plagiarism Checker X.*

**Plagx Cite for Java is a free, open-source citation generator and reference checker for Java, Kotlin and other JVM apps.** It turns sources into correct APA 7 and MLA 9 references and in-text citations, so researchers and students can build a clean reference list in seconds. It also checks BibTeX files for duplicate references, broken DOIs and missing details before you submit.

Use it as an **APA citation generator** or **MLA citation generator**: create a **reference list, bibliography or works cited** page, generate **in-text citations**, turn a **DOI into a citation**, import and export **BibTeX**, and find **duplicate references**.

- No dependencies, Java 11 or later
- Works in Spring Boot, Android, Kotlin, desktop apps and from the command line
- Uses [CSL-JSON](https://citeproc-js.readthedocs.io/en/latest/csl-json/markup.html), the reference format used by Zotero, Mendeley and Crossref
- Plain text, HTML or Markdown output
- Same output as the JavaScript version, [plagx-cite on npm](https://www.npmjs.com/package/plagx-cite)

**[Try the live demo →](https://plagiarismcheckerx.github.io/plagx-cite/)**

Developer downloads: [Maven Central](https://central.sonatype.com/artifact/io.github.plagiarismcheckerx/plagx-cite) · [javadoc.io](https://javadoc.io/doc/io.github.plagiarismcheckerx/plagx-cite) · [NuGet](https://www.nuget.org/packages/Plagx.Cite) · [PyPI](https://pypi.org/project/plagx-cite/) · [GitHub](https://github.com/plagiarismcheckerx/plagx-cite)

Need a quick citation online? Use the [free citation generator](https://plagiarismcheckerx.com/citation-generator?utm_source=plagx-cite-java).

> Plagx Cite is a free academic writing utility by Plagiarism Checker X. It is a citation generator that builds and checks APA and MLA reference lists. Plagiarism checking and AI-generated text detection are available in Plagiarism Checker X, linked at the end of this page.

## Install

Maven:

```xml
<dependency>
  <groupId>io.github.plagiarismcheckerx</groupId>
  <artifactId>plagx-cite</artifactId>
  <version>0.2.2</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.plagiarismcheckerx:plagx-cite:0.2.2'
```

## Quick start

```java
import com.plagiarismcheckerx.plagx.cite.PlagxCite;
import java.util.List;
import java.util.Map;

List<Map<String, Object>> refs = PlagxCite.parseJson("""
    [{"type": "article-journal", "title": "Deep learning for text similarity",
      "author": [{"family": "Smith", "given": "John Allen"}, {"family": "Lee", "given": "Kate"}],
      "issued": {"date-parts": [[2020]]}, "container-title": "Journal of Academic Writing",
      "volume": "12", "issue": "3", "page": "45-67", "DOI": "10.1234/jaw.2020.045"}]
    """);
Map<String, Object> ref = refs.get(0);

PlagxCite.formatReference(ref);
// Smith, J. A., & Lee, K. (2020). Deep learning for text similarity. Journal of Academic Writing, 12(3), 45–67. https://doi.org/10.1234/jaw.2020.045

PlagxCite.formatReference(ref, "mla");
// Smith, John Allen, and Kate Lee. “Deep learning for text similarity.” Journal of Academic Writing, vol. 12, no. 3, 2020, pp. 45-67, https://doi.org/10.1234/jaw.2020.045.

PlagxCite.citeInText(ref, "apa", "50", false);  // (Smith & Lee, 2020, p. 50)
PlagxCite.citeInText(ref, "apa", null, true);   // Smith and Lee (2020)
PlagxCite.citeInText(ref, "mla", "50", false);  // (Smith and Lee 50)
```

References are `Map<String, Object>` in CSL-JSON form (lists, strings and numbers), so a Zotero or Crossref export works with `PlagxCite.parseJson`. Maps you build yourself, or ones from Jackson or Gson, work too.

## Command line

The jar runs on its own:

```bash
java -jar plagx-cite-0.2.2.jar format references.bib --style apa
java -jar plagx-cite-0.2.2.jar check references.bib --check-dois
java -jar plagx-cite-0.2.2.jar doi 10.1038/nature14539 --style mla
java -jar plagx-cite-0.2.2.jar bibtex references.json > references.bib
```

| Command | What it does |
|---|---|
| `format <file>` | Prints the formatted reference list (`--style apa\|mla`, `--format text\|html\|markdown`) |
| `check <file>` | Reports duplicates, invalid DOIs and missing fields (`--check-dois`, `--strict`, `--json`) |
| `doi <doi> [...]` | Looks up DOIs and prints formatted references |
| `bibtex <file>` | Converts a CSL-JSON or BibTeX file to BibTeX |

`<file>` is a `.bib` or CSL-JSON `.json` file, or `-` for stdin. Exit codes: `0` OK, `1` problems found, `2` usage or input error.

## API

All methods are static on `com.plagiarismcheckerx.plagx.cite.PlagxCite`.

### Formatting references and citations

| Method | Returns |
|---|---|
| `formatReference(ref, style, format)` | One reference-list entry |
| `formatBibliography(refs, style, format)` | Sorted list of entries |
| `citeInText(ref, style, page, narrative)` | In-text citation |
| `sortReferences(refs)` | New list sorted by author, year, title |
| `parseJson(json)` | References from CSL-JSON text (an array or one object) |
| `STYLE_NAMES` | `["apa", "mla"]` |

- `style`: `"apa"` (default) or `"mla"`
- `format`: `"text"` (default), `"html"` (titles in `<i>`, text escaped) or `"markdown"` (titles in `*…*`)
- Shorter overloads use the defaults, e.g. `formatReference(ref)` or `citeInText(ref, "mla")`.

Supported reference types: `article-journal`, `article-magazine`, `article-newspaper`, `book`, `chapter`, `paper-conference`, `report`, `thesis`, `webpage`. Other types fall back to a sensible general layout.

### BibTeX import and export

```java
List<Map<String, Object>> refs = PlagxCite.parseBibTeX(Files.readString(Path.of("library.bib")));
System.out.println(PlagxCite.toBibTeX(refs));
```

`parseBibTeX` handles `@string` macros, `#` concatenation, common LaTeX accents (`{\"u}` → ü), organisation names in braces, and BibLaTeX `date` / `journaltitle` fields.

### DOI lookup

```java
Map<String, Object> ref = PlagxCite.lookupDoi("10.1038/nature14539", "you@example.com", null);

PlagxCite.normalizeDoi("https://doi.org/10.1038/NATURE14539"); // "10.1038/NATURE14539"
PlagxCite.extractDois(text);                                   // every DOI found in the text
```

`lookupDoi` calls the free public [Crossref API](https://www.crossref.org/documentation/retrieve-metadata/rest-api/). If a DOI isn't registered with Crossref, such as arXiv or Zenodo DOIs from DataCite, it falls back to [doi.org content negotiation](https://citation.doi.org/). No API key is needed. It throws `IllegalArgumentException` for an invalid DOI and `DoiLookupException` when the DOI can't be found. To use your own HTTP client, pass a `PlagxCite.HttpGet` as the last argument.

### Duplicate references

```java
PlagxCite.findDuplicates(refs);
// [{"indexes":[0,4],"reasons":["doi"]},{"indexes":[2,7],"reasons":["title"]}]
```

References count as duplicates when they share a DOI, or the same title and first author. Case, accents and punctuation are ignored.

### Checking a reference list

```java
CheckResult result = PlagxCite.checkReferences(refs, true, null, null);
// result.getReferences() = 4, result.getErrors() = 2, result.getWarnings() = 1
// result.getIssues().get(0): level "error", code "duplicate", ref "smith2020b", ...
```

Errors are duplicates, invalid DOIs and DOIs that can't be found (when `checkDois` is `true`). Warnings are missing titles, authors or years. `PlagxCite.checkReferences(refs)` runs the same checks offline, without the DOI lookups.

## Good to know

- Titles are used exactly as you give them. APA wants sentence case for article and book titles, and MLA wants title case. Plagx Cite does not change capitalisation automatically, because doing so would break names and acronyms.
- The output follows the main APA 7 and MLA 9 rules for common reference types. Always check the final list against your institution's or publisher's guidelines.

## Development

```bash
mvn test
```

## Plagx Cite - Free Citation & Reference Toolkit

An open-source citation utility for students, researchers and developers. Format references, organize citations and manage bibliographies.

Developed by Plagiarism Checker X.

Plagx Cite helps with source attribution; it does not detect plagiarism or AI-generated text. For full document checks, explore [Plagiarism Checker X](https://plagiarismcheckerx.com/?utm_source=plagx-cite-java) and its [AI Content Detector](https://plagiarismcheckerx.com/ai-content-detector?utm_source=plagx-cite-java).

## License

MIT © Plagiarism Checker X
