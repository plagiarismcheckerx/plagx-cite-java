package com.plagiarismcheckerx.plagx.cite;

import java.util.LinkedHashMap;
import java.util.Map;

/** One problem found by {@link PlagxCite#checkReferences}. */
public final class CheckIssue implements Json.JsonObject {
    private final String level;
    private final String code;
    private final int index;
    private final String ref;
    private final String message;

    CheckIssue(String level, String code, int index, String ref, String message) {
        this.level = level;
        this.code = code;
        this.index = index;
        this.ref = ref;
        this.message = message;
    }

    /**
     * Severity.
     *
     * @return {@code "error"} or {@code "warning"}
     */
    public String getLevel() {
        return level;
    }

    /**
     * Problem type.
     *
     * @return {@code duplicate}, {@code invalid-doi}, {@code doi-not-found}, {@code missing-title},
     *     {@code missing-author} or {@code missing-year}
     */
    public String getCode() {
        return code;
    }

    /**
     * Position of the reference in the list.
     *
     * @return the index
     */
    public int getIndex() {
        return index;
    }

    /**
     * The reference's id, or {@code #n} when it has none.
     *
     * @return the label
     */
    public String getRef() {
        return ref;
    }

    /**
     * Readable description.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("level", level);
        map.put("code", code);
        map.put("index", index);
        map.put("ref", ref);
        map.put("message", message);
        return map;
    }

    @Override
    public String toString() {
        return Json.stringify(this);
    }
}
