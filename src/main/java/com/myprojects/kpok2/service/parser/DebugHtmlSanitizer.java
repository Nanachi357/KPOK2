package com.myprojects.kpok2.service.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes Moodle session keys before an opt-in HTML diagnostic is persisted. */
final class DebugHtmlSanitizer {

    static final String REDACTED = "[REDACTED]";

    private static final Pattern INLINE_SESSKEY = Pattern.compile(
            "(?i)(\\bsesskey\\b\\s*(?:[\\\"']\\s*)?(?::|=|%3D)\\s*(?:[\\\"']|&quot;)?)([^&%\\s\\\"'<>;,)}]+)");

    private DebugHtmlSanitizer() {
    }

    static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        Document document = Jsoup.parse(html);
        for (Element input : document.select("input[name]")) {
            if ("sesskey".equalsIgnoreCase(input.attr("name"))) {
                input.attr("value", REDACTED);
            }
        }

        Matcher matcher = INLINE_SESSKEY.matcher(document.outerHtml());
        StringBuilder sanitized = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    sanitized,
                    Matcher.quoteReplacement(matcher.group(1) + REDACTED));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }
}
