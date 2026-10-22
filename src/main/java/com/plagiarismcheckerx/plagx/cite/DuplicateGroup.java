package com.plagiarismcheckerx.plagx.cite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** References that appear more than once, and what matched ({@code "doi"}, {@code "title"}). */
public final class DuplicateGroup implements Json.JsonObject {
    private final List<Integer> indexes;
    private final List<String> reasons;

    DuplicateGroup(List<Integer> indexes, List<String> reasons) {
        this.indexes = Collections.unmodifiableList(indexes);
        this.reasons = Collections.unmodifiableList(reasons);
    }

    /**
     * Positions of the matching references in the list.
     *
     * @return the indexes, first occurrence first
     */
    public List<Integer> getIndexes() {
        return indexes;
    }

    /**
     * What matched: {@code "doi"} and/or {@code "title"}.
     *
     * @return the reasons
     */
    public List<String> getReasons() {
        return reasons;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("indexes", indexes);
        map.put("reasons", reasons);
        return map;
    }

    @Override
    public String toString() {
        return Json.stringify(this);
    }
}
