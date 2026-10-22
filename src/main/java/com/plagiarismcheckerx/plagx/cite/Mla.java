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

/** MLA 9th edition (common reference types). */
final class Mla implements Style {
    private static final List<String> QUOTED_TYPES = Arrays.asList(
        "article-journal", "article-magazine", "article-newspaper", "article",
        "chapter", "paper-conference", "webpage", "entry-encyclopedia", "entry-dictionary", "post", "post-weblog");

    private static String natural(Names.Name n) {
        if (n.literal != null) return n.literal;
        if (n.given.isEmpty()) return n.family;
        return n.family.isEmpty() ? n.given : n.given + " " + n.family;
    }

    private static String authorList(List<Object> list) {
        Names.Name first = Names.parts(list.get(0));
        String lead = first.literal != null ? first.literal : first.family;
        if (first.literal == null && !first.given.isEmpty()) lead += ", " + first.given;
        if (first.literal == null && first.suffix != null) lead += ", " + first.suffix;
        if (list.size() == 1) return lead;
        if (list.size() == 2) return lead + ", and " + natural(Names.parts(list.get(1)));
        return lead + ", et al.";
    }

    private static String editorList(List<Object> list) {
        List<String> names = new ArrayList<>();
        for (Object n : list) names.add(natural(Names.parts(n)));
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        return names.get(0) + " et al.";
    }

    private static String dateText(Map<String, Object> ref) {
        Integer[] d = Values.getDate(ref);
        List<String> parts = new ArrayList<>();
        if (set(d[2])) parts.add(d[2].toString());
        if (set(d[1])) parts.add(Values.MONTHS_MLA[d[1] - 1]);
        if (set(d[0])) parts.add(d[0].toString());
        return String.join(" ", parts);
    }

    private static String pagesText(Object page) {
        return (isPageRange(page) ? "pp." : "p.") + " " + pageRange(page, "-");
    }

    @Override
    public String reference(Map<String, Object> ref, Formatter f) {
        String type = text(ref, "type");
        List<String> parts = new ArrayList<>();

        if (filled(ref.get("author"))) {
            String authors = authorList(list(ref.get("author")));
            parts.add(f.t(authors + dot(authors)));
        }

        String title = text(ref, "title");
        if (!title.isEmpty()) {
            parts.add(QUOTED_TYPES.contains(type) ? f.t("\u201c" + title + dot(title) + "\u201d") : f.i(title) + f.t(dot(title)));
        }

        // Container elements, separated by commas and closed with a period.
        String container = text(ref, "container-title");
        List<String> elements = new ArrayList<>();
        if (!container.isEmpty()) elements.add(f.i(container));
        if (filled(ref.get("editor"))) elements.add(f.t("edited by " + editorList(list(ref.get("editor")))));
        if (filled(ref.get("edition"))) elements.add(f.t(Values.ordinal(ref.get("edition")) + " ed."));
        if (filled(ref.get("volume"))) elements.add(f.t("vol. " + str(ref.get("volume"))));
        if (filled(ref.get("issue"))) elements.add(f.t("no. " + str(ref.get("issue"))));
        // Articles name their journal, not the publisher, unless there is no journal (e.g. arXiv preprints).
        if (filled(ref.get("publisher")) && (!type.startsWith("article") || container.isEmpty())) {
            elements.add(f.t(str(ref.get("publisher"))));
        }
        String date = dateText(ref);
        if (!date.isEmpty()) elements.add(f.t(date));
        if (filled(ref.get("page"))) elements.add(f.t(pagesText(ref.get("page"))));
        String url = Values.link(ref);
        if (!url.isEmpty()) elements.add(f.t(url));

        if (!elements.isEmpty()) parts.add(String.join(f.t(", "), elements) + f.t("."));
        return String.join(" ", parts);
    }

    @Override
    public String inText(Map<String, Object> ref, String page, boolean narrative) {
        List<String> names = new ArrayList<>();
        for (Object n : list(ref.get("author"))) names.add(Names.familyName(n));
        String who;
        if (names.isEmpty()) who = "\u201c" + (filled(ref.get("title")) ? str(ref.get("title")) : "Untitled") + "\u201d";
        else if (names.size() == 1) who = names.get(0);
        else if (names.size() == 2) who = names.get(0) + " and " + names.get(1);
        else who = names.get(0) + " et al.";
        String pages = filled(page) ? pageRange(page, "-") : "";
        if (narrative) return pages.isEmpty() ? who : who + " (" + pages + ")";
        return pages.isEmpty() ? "(" + who + ")" : "(" + who + " " + pages + ")";
    }
}
