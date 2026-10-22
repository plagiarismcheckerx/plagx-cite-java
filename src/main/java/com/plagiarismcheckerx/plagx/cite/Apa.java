package com.plagiarismcheckerx.plagx.cite;

import static com.plagiarismcheckerx.plagx.cite.Values.dot;
import static com.plagiarismcheckerx.plagx.cite.Values.filled;
import static com.plagiarismcheckerx.plagx.cite.Values.isPageRange;
import static com.plagiarismcheckerx.plagx.cite.Values.list;
import static com.plagiarismcheckerx.plagx.cite.Values.pageRange;
import static com.plagiarismcheckerx.plagx.cite.Values.set;
import static com.plagiarismcheckerx.plagx.cite.Values.str;
import static com.plagiarismcheckerx.plagx.cite.Values.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** APA 7th edition (common reference types). */
final class Apa implements Style {
    private static final List<String> JOURNAL_TYPES = Arrays.asList("article-journal", "article-magazine", "article-newspaper", "article");
    private static final List<String> CONTAINER_TYPES = Arrays.asList("chapter", "paper-conference", "entry-encyclopedia", "entry-dictionary");
    private static final List<String> DATED_TYPES = Arrays.asList("webpage", "article-newspaper", "article-magazine", "post", "post-weblog");

    private static String authorName(Object name) {
        Names.Name n = Names.parts(name);
        if (n.literal != null) return n.literal;
        String s = n.family;
        if (!n.given.isEmpty()) s += ", " + Names.initials(n.given);
        if (n.suffix != null) s += ", " + n.suffix;
        return s;
    }

    private static String authorList(List<Object> list) {
        List<String> names = new ArrayList<>();
        for (Object n : list) names.add(authorName(n));
        String last = names.get(names.size() - 1);
        if (names.size() == 1) return last;
        if (names.size() <= 20) return String.join(", ", names.subList(0, names.size() - 1)) + ", & " + last;
        return String.join(", ", names.subList(0, 19)) + ", . . . " + last;
    }

    private static String editorList(List<Object> list) {
        List<String> names = new ArrayList<>();
        for (Object name : list) {
            Names.Name n = Names.parts(name);
            if (n.literal != null) names.add(n.literal);
            else names.add(n.given.isEmpty() ? n.family : Names.initials(n.given) + " " + n.family);
        }
        String s;
        if (names.size() == 1) s = names.get(0);
        else if (names.size() == 2) s = String.join(" & ", names);
        else s = String.join(", ", names.subList(0, names.size() - 1)) + ", & " + names.get(names.size() - 1);
        return s + " (" + (names.size() > 1 ? "Eds." : "Ed.") + ")";
    }

    private static String dateText(Map<String, Object> ref) {
        Integer[] d = Values.getDate(ref);
        if (!set(d[0])) return "n.d.";
        String s = d[0].toString();
        if (DATED_TYPES.contains(text(ref, "type")) && set(d[1])) {
            s += ", " + Values.MONTHS[d[1] - 1];
            if (set(d[2])) s += " " + d[2];
        }
        return s;
    }

    private static String titleText(Map<String, Object> ref, Formatter f) {
        String title = text(ref, "title");
        if (title.isEmpty()) return "";
        String type = text(ref, "type");
        boolean italic = !JOURNAL_TYPES.contains(type) && !CONTAINER_TYPES.contains(type);
        String s = italic ? f.i(title) : f.t(title);
        String note = "";
        if (filled(ref.get("edition"))) note = " (" + Values.ordinal(ref.get("edition")) + " ed.)";
        if ("thesis".equals(type)) {
            String publisher = filled(ref.get("publisher")) ? ", " + str(ref.get("publisher")) : "";
            note = " [" + (filled(ref.get("genre")) ? str(ref.get("genre")) : "Thesis") + publisher + "]";
        }
        if (!note.isEmpty()) return s + f.t(note + ".");
        return s + f.t(dot(title));
    }

    private static String sourceText(Map<String, Object> ref, Formatter f, String authors) {
        String type = text(ref, "type");
        String container = text(ref, "container-title");
        String publisher = text(ref, "publisher");

        if (JOURNAL_TYPES.contains(type)) {
            // Preprints and similar have no journal; name the repository instead (e.g. arXiv).
            if (container.isEmpty()) return !publisher.isEmpty() && !publisher.equals(authors) ? f.t(publisher + dot(publisher)) : "";
            String s = f.i(container);
            if (filled(ref.get("volume"))) s += f.t(", ") + f.i(str(ref.get("volume")));
            if (filled(ref.get("issue"))) s += f.t("(" + str(ref.get("issue")) + ")");
            if (filled(ref.get("page"))) s += f.t(", " + pageRange(ref.get("page")));
            return s + f.t(".");
        }

        if (CONTAINER_TYPES.contains(type)) {
            String s = f.t("In ");
            if (filled(ref.get("editor"))) s += f.t(editorList(list(ref.get("editor"))) + ", ");
            if (!container.isEmpty()) s += f.i(container);
            if (filled(ref.get("page"))) {
                s += f.t(" (" + (isPageRange(ref.get("page")) ? "pp." : "p.") + " " + pageRange(ref.get("page")) + ")");
            }
            s += f.t(".");
            if (!publisher.isEmpty()) s += f.t(" " + publisher + dot(publisher));
            return s;
        }

        if ("thesis".equals(type)) return "";

        // Webpage: site name; book, report and others: publisher. Omitted when it repeats the author.
        String source = "webpage".equals(type) ? container : publisher;
        if (source.isEmpty() || source.equals(authors)) return "";
        return f.t(source + dot(source));
    }

    @Override
    public String reference(Map<String, Object> ref, Formatter f) {
        String authors = filled(ref.get("author")) ? authorList(list(ref.get("author"))) : "";
        String date = f.t("(" + dateText(ref) + ").");
        String title = titleText(ref, f);
        List<String> parts = authors.isEmpty()
            ? new ArrayList<>(Arrays.asList(title, date))
            : new ArrayList<>(Arrays.asList(f.t(authors + dot(authors)), date, title));
        parts.add(sourceText(ref, f, authors));
        parts.add(f.t(Values.link(ref)));
        parts.removeIf(String::isEmpty);
        return String.join(" ", parts);
    }

    @Override
    public String inText(Map<String, Object> ref, String page, boolean narrative) {
        List<String> names = new ArrayList<>();
        for (Object n : list(ref.get("author"))) names.add(Names.familyName(n));
        String conj = narrative ? "and" : "&";

        String year = Values.getYear(ref);
        String detail = year != null ? year : "n.d.";
        if (filled(page)) detail += ", " + (isPageRange(page) ? "pp." : "p.") + " " + pageRange(page);

        // No author: the title stands in, and APA puts the comma inside the quotation marks.
        if (names.isEmpty()) {
            String title = filled(ref.get("title")) ? str(ref.get("title")) : "Untitled";
            return narrative ? "\u201c" + title + "\u201d (" + detail + ")" : "(\u201c" + title + ",\u201d " + detail + ")";
        }

        String who;
        if (names.size() == 1) who = names.get(0);
        else if (names.size() == 2) who = names.get(0) + " " + conj + " " + names.get(1);
        else who = names.get(0) + " et al.";
        return narrative ? who + " (" + detail + ")" : "(" + who + ", " + detail + ")";
    }
}
