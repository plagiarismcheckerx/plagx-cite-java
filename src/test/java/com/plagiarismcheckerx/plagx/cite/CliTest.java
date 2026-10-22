// Command line tests (same behaviour as `npx plagx-cite`).
package com.plagiarismcheckerx.plagx.cite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {
    static final String BIB = String.join("\n",
        "@article{smith2020, author={Smith, John and Lee, Kate}, title={Deep learning for text similarity},",
        "  journal={Journal of Academic Writing}, year=2020, volume=12, number=3, pages={45--67}}",
        "@book{brown2018, author={Brown, Mary}, title={Writing research papers}, publisher={Academic Press}, year=2018}",
        "@article{smithdup, author={Smith, J.}, title={Deep Learning for Text Similarity!}, journal={JAW}, year=2020}",
        "@misc{bad, title={Untitled web page}, doi={not-a-doi}}",
        "");

    @TempDir
    Path dir;

    String bib;
    String clean;

    static final class Run {
        int code;
        String out;
        String err;
    }

    static Run runWithStdin(String stdin, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Run r = new Run();
        r.code = Cli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8),
            new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
        r.out = new String(out.toByteArray(), StandardCharsets.UTF_8);
        r.err = new String(err.toByteArray(), StandardCharsets.UTF_8);
        return r;
    }

    static Run run(String... args) {
        return runWithStdin("", args);
    }

    @BeforeEach
    void files() throws IOException {
        bib = Files.write(dir.resolve("refs.bib"), BIB.getBytes(StandardCharsets.UTF_8)).toString();
        clean = Files.write(dir.resolve("clean.json"),
            "[{\"type\": \"book\", \"title\": \"T\", \"author\": [{\"family\": \"A\", \"given\": \"B\"}], \"year\": 2001}]".getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Test
    void format() {
        Run r = run("format", clean);
        assertEquals(0, r.code);
        assertEquals("A, B. (2001). T.\n", r.out);
        assertEquals("", r.err);
        assertTrue(run("format", bib, "--style", "mla").out.split("\n")[0].startsWith("Brown, Mary. Writing research papers."));
    }

    @Test
    void formatFromStdin() {
        Run r = runWithStdin(BIB, "format", "-");
        assertEquals(0, r.code);
        assertTrue(r.out.startsWith("Brown, M. (2018)."));
    }

    @Test
    void check() {
        Run r = run("check", bib);
        assertEquals(1, r.code);
        assertTrue(r.out.contains("4 reference(s), 2 error(s), 2 warning(s)"));
        assertTrue(r.out.contains("error   smithdup: Possible duplicate (title match)"));
        assertTrue(r.out.contains("error   bad: Invalid DOI \"not-a-doi\""));
        assertEquals(0, run("check", clean).code);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkJsonAndStrict() throws IOException {
        List<Object> reports = (List<Object>) Json.parse(run("check", bib, "--json").out);
        List<Object> issues = (List<Object>) ((Map<String, Object>) reports.get(0)).get("issues");
        StringBuilder codes = new StringBuilder();
        for (Object x : issues) codes.append(((Map<String, Object>) x).get("code")).append(' ');
        assertEquals("duplicate invalid-doi missing-author missing-year ", codes.toString());
        String warn = Files.write(dir.resolve("warn.json"), "[{\"title\": \"No author here\", \"year\": 2020}]".getBytes(StandardCharsets.UTF_8)).toString();
        assertEquals(0, run("check", warn).code);
        assertEquals(1, run("check", warn, "--strict").code);
    }

    @Test
    void bibtex() {
        Run r = run("bibtex", clean);
        assertEquals(0, r.code);
        assertTrue(r.out.startsWith("@book{a2001,"));
    }

    @Test
    void usageErrors() {
        assertEquals(2, run().code);
        assertEquals(2, run("format").code);
        assertEquals(2, run("format", dir.resolve("missing.bib").toString()).code);
        assertEquals(2, run("format", clean, "--style", "chicago").code);
        assertEquals(2, run("format", clean, "--format", "pdf").code);
        assertEquals(2, run("nope").code);
        Run v = run("--version");
        assertEquals(0, v.code);
        assertEquals(PlagxCite.VERSION, v.out.trim());
    }
}
