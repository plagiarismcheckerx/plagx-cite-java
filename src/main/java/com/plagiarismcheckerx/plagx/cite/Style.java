package com.plagiarismcheckerx.plagx.cite;

import java.util.Map;

interface Style {
    String reference(Map<String, Object> ref, Formatter f);

    String inText(Map<String, Object> ref, String page, boolean narrative);
}
