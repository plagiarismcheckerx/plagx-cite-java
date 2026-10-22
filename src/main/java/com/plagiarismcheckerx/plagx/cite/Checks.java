package com.plagiarismcheckerx.plagx.cite;

import static com.plagiarismcheckerx.plagx.cite.Values.filled;
import static com.plagiarismcheckerx.plagx.cite.Values.list;
import static com.plagiarismcheckerx.plagx.cite.Values.lower;
import static com.plagiarismcheckerx.plagx.cite.Values.str;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Duplicate detection and reference list checks. */
final class Checks {
    private Checks() {}

    private static final String[] REASONS = {"doi", "title"};

    private static Map<String, String> keysFor(Map<String, Object> ref) {
        Map<String, String> keys = new HashMap<>();
        String doi = filled(ref.get("DOI")) ? Doi.normalize(str(ref.get("DOI"))) : null;
        if (doi != null) keys.put("doi", lower(doi));
        String title = Values.normalizeText(Values.text(ref, "title"));
        if (!title.isEmpty()) {
            String lead = filled(ref.get("author")) ? Values.normalizeText(Names.familyName(list(ref.get("author")).get(0))) : "";
            keys.put("title", title + "|" + lead);
        }
        return keys;
    }

    static List<DuplicateGroup> findDuplicates(List<Map<String, Object>> refs) {
        List<Map<String, String>> keys = new ArrayList<>();
        for (Map<String, Object> ref : refs) keys.add(keysFor(ref));
        int[] parent = new int[refs.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;

        Map<String, Integer> firstSeen = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            for (String reason : REASONS) {
                String value = keys.get(i).get(reason);
                if (value == null) continue;
                String id = reason + ":" + value;
                Integer seen = firstSeen.get(id);
                if (seen != null) parent[find(parent, i)] = find(parent, seen);
                else firstSeen.put(id, i);
            }
        }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < refs.size(); i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);

        List<DuplicateGroup> out = new ArrayList<>();
        for (List<Integer> indexes : groups.values()) {
            if (indexes.size() < 2) continue;
            List<String> reasons = new ArrayList<>();
            for (String reason : REASONS) {
                List<String> values = new ArrayList<>();
                for (int i : indexes) if (keys.get(i).get(reason) != null) values.add(keys.get(i).get(reason));
                if (values.size() > new HashSet<>(values).size()) reasons.add(reason);
            }
            out.add(new DuplicateGroup(indexes, reasons));
        }
        return out;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static String label(Map<String, Object> ref, int i) {
        return filled(ref.get("id")) ? str(ref.get("id")) : "#" + (i + 1);
    }

    static CheckResult check(List<Map<String, Object>> refs, boolean checkDois, String mailto, PlagxCite.HttpGet http) {
        List<CheckIssue> issues = new ArrayList<>();

        for (DuplicateGroup group : findDuplicates(refs)) {
            List<String> names = new ArrayList<>();
            for (int i : group.getIndexes()) names.add(label(refs.get(i), i));
            for (int i : group.getIndexes().subList(1, group.getIndexes().size())) {
                issues.add(new CheckIssue("error", "duplicate", i, label(refs.get(i), i),
                    "Possible duplicate (" + String.join(" and ", group.getReasons()) + " match) of " + names.get(0) + ": " + String.join(", ", names)));
            }
        }

        for (int i = 0; i < refs.size(); i++) {
            Map<String, Object> ref = refs.get(i);
            String l = label(ref, i);
            if (!filled(ref.get("title"))) issues.add(new CheckIssue("warning", "missing-title", i, l, "No title"));
            if (!filled(ref.get("author")) && !filled(ref.get("editor"))) {
                issues.add(new CheckIssue("warning", "missing-author", i, l, "No author or editor"));
            }
            if (Values.getYear(ref) == null) issues.add(new CheckIssue("warning", "missing-year", i, l, "No year (will show as n.d.)"));
            if (filled(ref.get("DOI")) && Doi.normalize(str(ref.get("DOI"))) == null) {
                issues.add(new CheckIssue("error", "invalid-doi", i, l, "Invalid DOI \"" + str(ref.get("DOI")) + "\""));
            }
        }

        if (checkDois) {
            for (int i = 0; i < refs.size(); i++) {
                Map<String, Object> ref = refs.get(i);
                String doi = filled(ref.get("DOI")) ? Doi.normalize(str(ref.get("DOI"))) : null;
                if (doi == null) continue;
                try {
                    Doi.lookup(doi, mailto, http);
                } catch (DoiLookupException | RuntimeException e) {
                    issues.add(new CheckIssue("error", "doi-not-found", i, label(ref, i), e.getMessage()));
                }
            }
        }

        // List.sort is stable, so issues of the same reference and level keep the order they were found in.
        issues.sort(Comparator.comparingInt(CheckIssue::getIndex).thenComparingInt(x -> "error".equals(x.getLevel()) ? 0 : 1));
        int errors = 0;
        for (CheckIssue x : issues) if ("error".equals(x.getLevel())) errors++;
        return new CheckResult(refs.size(), errors, issues.size() - errors, issues);
    }
}
