package com.plagiarismcheckerx.plagx.cite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Result of checking a reference list. */
public final class CheckResult implements Json.JsonObject {
    private final int references;
    private final int errors;
    private final int warnings;
    private final List<CheckIssue> issues;

    CheckResult(int references, int errors, int warnings, List<CheckIssue> issues) {
        this.references = references;
        this.errors = errors;
        this.warnings = warnings;
        this.issues = Collections.unmodifiableList(issues);
    }

    /**
     * Number of references checked.
     *
     * @return the count
     */
    public int getReferences() {
        return references;
    }

    /**
     * Number of errors (duplicates, invalid DOIs, DOIs not found).
     *
     * @return the count
     */
    public int getErrors() {
        return errors;
    }

    /**
     * Number of warnings (missing titles, authors or years).
     *
     * @return the count
     */
    public int getWarnings() {
        return warnings;
    }

    /**
     * Every problem found, in reference order.
     *
     * @return the issues
     */
    public List<CheckIssue> getIssues() {
        return issues;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("references", references);
        map.put("errors", errors);
        map.put("warnings", warnings);
        map.put("issues", issues);
        return map;
    }

    @Override
    public String toString() {
        return Json.stringify(this);
    }
}
