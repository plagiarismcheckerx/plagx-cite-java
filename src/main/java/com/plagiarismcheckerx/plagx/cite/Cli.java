package com.plagiarismcheckerx.plagx.cite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plagx Cite command line: {@code java -jar plagx-cite.jar format refs.bib}. A free academic writing utility by Plagiarism Checker X. */
public final class Cli {
    private Cli() {}

    private static final String HELP = String.join("\n",
        "Plagx Cite " + PlagxCite.VERSION + ": format and check references (APA 7 / MLA 9, BibTeX, DOI)",
        "",
        "Usage:",
        "  plagx-cite format <file>      Print a formatted reference list",
        "  plagx-cite check <file>       Check for duplicates, invalid DOIs and missing fields",
        "  plagx-cite doi <doi> [...]    Look up DOIs and print formatted references",
        "  plagx-cite bibtex <file>      Convert a reference file to BibTeX",
        "",
        "<file> is a .bib (BibTeX) or .json (CSL-JSON) file. Use - to read from stdin.",
        "",
        "Options:",
        "  --style apa|mla               Citation style (default: apa)",
        "  --format text|html|markdown   Output format (default: text)",
        "  --check-dois                  check: look up every DOI online",
        "  --strict                      check: treat warnings as errors",
        "  --json                        check: print the result as JSON",
        "  --mailto <email>              Email for the Crossref polite pool (optional)",
        "  -v, --version                 Show version",
        "  -h, --help                    Show this help",
        "",
        "Exit codes: 0 = OK, 1 = problems found (check), 2 = usage or input error.",
        "",
        "A free academic writing utility by Plagiarism Checker X. It does not check for",
        "plagiarism or AI-generated text: https://plagiarismcheckerx.com/");

    private static final class UsageError extends Exception {
        private static final long serialVersionUID = 1L;

        UsageError(String message) {
            super(message);
        }
    }

    private static final class Options {
        String style = "apa";
        String format = "text";
        String mailto;
        boolean checkDois;
        boolean strict;
        boolean json;
        boolean help;
        boolean version;
        final List<String> args = new ArrayList<>();
    }

    private static Options parseArgs(String[] argv) throws UsageError {
        Options o = new Options();
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            if (a.equals("--style") || a.equals("--format") || a.equals("--mailto")) {
                if (i + 1 >= argv.length) throw new UsageError(a + " needs a value");
                String v = argv[++i];
                if (a.equals("--style")) o.style = v;
                else if (a.equals("--format")) o.format = v;
                else o.mailto = v;
            } else if (a.startsWith("--style=")) {
                o.style = a.substring(8);
            } else if (a.startsWith("--format=")) {
                o.format = a.substring(9);
            } else if (a.equals("--check-dois")) {
                o.checkDois = true;
            } else if (a.equals("--strict")) {
                o.strict = true;
            } else if (a.equals("--json")) {
                o.json = true;
            } else if (a.equals("-h") || a.equals("--help")) {
                o.help = true;
            } else if (a.equals("-v") || a.equals("--version")) {
                o.version = true;
            } else if (a.startsWith("-") && !a.equals("-")) {
                throw new UsageError("Unknown option " + a);
            } else {
                o.args.add(a);
            }
        }
        if (!PlagxCite.STYLE_NAMES.contains(o.style)) {
            throw new UsageError("Unknown style \"" + o.style + "\". Use: " + String.join(", ", PlagxCite.STYLE_NAMES));
        }
        return o;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadReferences(String path, InputStream stdin) throws UsageError {
        String text;
        try {
            text = path.equals("-") ? readAll(stdin) : new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new UsageError("Cannot read " + path + ": file not found");
        } catch (IOException e) {
            throw new UsageError("Cannot read " + path + ": " + e.getMessage());
        }
        String start = Values.trim(text);
        boolean looksJson = path.toLowerCase(java.util.Locale.ROOT).endsWith(".json")
            || (path.equals("-") && (start.startsWith("[") || start.startsWith("{")));
        if (!looksJson) return PlagxCite.parseBibTeX(text);
        Object data;
        try {
            data = Json.parse(text);
        } catch (IllegalArgumentException e) {
            throw new UsageError(path + " is not valid JSON: " + e.getMessage());
        }
        Object refs = data;
        if (data instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) data;
            refs = m.get("items") != null ? m.get("items") : m.get("references");
        }
        if (!(refs instanceof List)) throw new UsageError(path + " must contain a CSL-JSON array of references");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object r : (List<Object>) refs) if (r instanceof Map) out.add((Map<String, Object>) r);
        return out;
    }

    private static String printCheck(String file, CheckResult r) {
        StringBuilder sb = new StringBuilder(file + ": " + r.getReferences() + " reference(s), " + r.getErrors() + " error(s), "
            + r.getWarnings() + " warning(s)");
        for (CheckIssue x : r.getIssues()) {
            sb.append('\n').append(String.format("  %-7s %s: %s", x.getLevel(), x.getRef(), x.getMessage()));
        }
        return sb.toString();
    }

    /**
     * Runs the command line and returns the exit code.
     *
     * @param argv arguments
     * @param out standard output
     * @param err standard error
     * @param stdin standard input (for files named "-")
     * @return 0 OK, 1 problems found, 2 usage or input error
     */
    public static int run(String[] argv, PrintStream out, PrintStream err, InputStream stdin) {
        Options o;
        try {
            o = parseArgs(argv);
        } catch (UsageError e) {
            err.print(e.getMessage() + "\n\nRun plagx-cite --help for usage.\n");
            return 2;
        }
        if (o.version) {
            out.print(PlagxCite.VERSION + "\n");
            return 0;
        }
        if (o.help || o.args.isEmpty()) {
            out.print(HELP + "\n");
            return o.help ? 0 : 2;
        }

        String command = o.args.get(0);
        List<String> rest = o.args.subList(1, o.args.size());
        try {
            if (command.equals("format") || command.equals("bibtex") || command.equals("check")) {
                if (rest.isEmpty()) throw new UsageError(command + " needs a file (.bib or .json, or - for stdin)");
                boolean failed = false;
                List<Map<String, Object>> reports = new ArrayList<>();
                List<String> texts = new ArrayList<>();
                for (String file : rest) {
                    List<Map<String, Object>> refs = loadReferences(file, stdin);
                    if (command.equals("format")) {
                        out.print(String.join("\n", PlagxCite.formatBibliography(refs, o.style, o.format)) + "\n");
                    } else if (command.equals("bibtex")) {
                        out.print(PlagxCite.toBibTeX(PlagxCite.sortReferences(refs)) + "\n");
                    } else {
                        CheckResult result = PlagxCite.checkReferences(refs, o.checkDois, o.mailto, null);
                        if (result.getErrors() > 0 || (o.strict && result.getWarnings() > 0)) failed = true;
                        Map<String, Object> report = new LinkedHashMap<>();
                        report.put("file", file);
                        report.putAll(result.toMap());
                        reports.add(report);
                        texts.add(printCheck(file, result));
                    }
                }
                if (command.equals("check")) {
                    out.print((o.json ? Json.stringifyPretty(reports) : String.join("\n\n", texts)) + "\n");
                    return failed ? 1 : 0;
                }
                return 0;
            }

            if (command.equals("doi")) {
                if (rest.isEmpty()) throw new UsageError("doi needs at least one DOI");
                boolean failed = false;
                for (String doi : rest) {
                    try {
                        out.print(PlagxCite.formatReference(PlagxCite.lookupDoi(doi, o.mailto, null), o.style, o.format) + "\n");
                    } catch (DoiLookupException | IllegalArgumentException e) {
                        err.print(e.getMessage() + "\n");
                        failed = true;
                    }
                }
                return failed ? 1 : 0;
            }

            throw new UsageError("Unknown command \"" + command + "\"");
        } catch (UsageError | IllegalArgumentException e) {
            err.print(e.getMessage() + "\n\nRun plagx-cite --help for usage.\n");
            return 2;
        }
    }

    /**
     * Entry point for {@code java -jar}.
     *
     * @param args arguments
     */
    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int code = run(args, out, err, System.in);
        out.flush();
        System.exit(code);
    }
}
