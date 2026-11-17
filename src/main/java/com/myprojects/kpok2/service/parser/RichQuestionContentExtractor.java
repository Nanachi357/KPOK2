package com.myprojects.kpok2.service.parser;

import com.myprojects.kpok2.model.QuestionContentLocation;
import com.myprojects.kpok2.model.dto.ParsedQuestionAssetDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Extracts portable, sanitized HTML and image references from Moodle markup. */
final class RichQuestionContentExtractor {

    private static final String REMOVED_CONTENT_SELECTOR = String.join(", ",
            "input",
            "button",
            "i.icon",
            ".specificfeedback",
            ".feedback",
            ".validationerror");

    private static final Safelist CONTENT_SAFELIST = Safelist.relaxed()
            .addTags(
                    "div", "span", "section", "article",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td",
                    "sub", "sup", "figure", "figcaption",
                    "math", "mrow", "mi", "mn", "mo", "msup", "msub",
                    "mfrac", "msqrt", "mroot", "mtext", "mtable", "mtr", "mtd",
                    "svg", "g", "path", "circle", "ellipse", "line", "polyline",
                    "polygon", "rect", "text")
            .addAttributes(
                    ":all", "class", "title", "lang", "dir", "role", "aria-label",
                    "d", "x", "y", "x1", "x2", "y1", "y2", "cx", "cy",
                    "r", "rx", "ry", "points", "fill", "stroke", "stroke-width")
            .addAttributes(
                    "img", "src", "alt", "width", "height", "loading",
                    "data-kpok2-ref")
            .addAttributes("table", "summary")
            .addAttributes("th", "scope", "rowspan", "colspan")
            .addAttributes("td", "rowspan", "colspan")
            .addAttributes("span", "data-tex")
            .addAttributes("math", "display", "xmlns")
            .addAttributes("svg", "viewBox", "width", "height")
            .addProtocols("img", "src", "http", "https", "data");

    RichQuestionContent extract(
            Element contextElement,
            Element questionTextElement,
            List<Element> answerElements,
            String baseUrl) {
        List<ParsedQuestionAssetDto> assets = new ArrayList<>();
        AtomicInteger referenceSequence = new AtomicInteger();

        Element contextContent = contextElement == null
                ? null
                : firstOrSelf(contextElement, "div.qtext");
        String contextHtml = sanitize(
                contextContent,
                QuestionContentLocation.CONTEXT,
                -1,
                baseUrl,
                assets,
                referenceSequence);
        String questionHtml = sanitize(
                questionTextElement,
                QuestionContentLocation.QUESTION,
                -1,
                baseUrl,
                assets,
                referenceSequence);

        List<String> answerHtml = new ArrayList<>();
        for (int index = 0; index < answerElements.size(); index++) {
            answerHtml.add(sanitize(
                    answerElements.get(index),
                    QuestionContentLocation.ANSWER,
                    index,
                    baseUrl,
                    assets,
                    referenceSequence));
        }

        return new RichQuestionContent(
                emptyToNull(contextHtml),
                emptyToNull(questionHtml),
                List.copyOf(answerHtml),
                List.copyOf(assets));
    }

    private String sanitize(
            Element source,
            QuestionContentLocation location,
            int answerIndex,
            String baseUrl,
            List<ParsedQuestionAssetDto> assets,
            AtomicInteger referenceSequence) {
        if (source == null) {
            return "";
        }

        Element clean = source.clone();
        normalizeMath(clean);
        clean.select(REMOVED_CONTENT_SELECTOR).remove();

        int imageSequence = 0;
        for (Element image : clean.select("img")) {
            String referenceId = "asset-" + referenceSequence.getAndIncrement();
            String sourceUrl = image.absUrl("src");
            if (sourceUrl.isBlank()) {
                sourceUrl = resolveUrl(baseUrl, firstNonBlank(
                        image.attr("src"), image.attr("data-src")));
            }
            image.attr("src", sourceUrl);
            image.attr("data-kpok2-ref", referenceId);
            assets.add(ParsedQuestionAssetDto.builder()
                    .referenceId(referenceId)
                    .contentLocation(location)
                    .answerIndex(answerIndex)
                    .sequenceIndex(imageSequence++)
                    .sourceUrl(sourceUrl)
                    .altText(image.attr("alt").trim())
                    .build());
        }

        Document dirty = Jsoup.parseBodyFragment(clean.html(), normalizedBaseUrl(baseUrl));
        Document sanitized = new Cleaner(CONTENT_SAFELIST).clean(dirty);
        sanitized.outputSettings().prettyPrint(false);
        return sanitized.body().html().trim();
    }

    private void normalizeMath(Element content) {
        for (Element script : content.select("script[type^=math/tex]")) {
            String tex = firstNonBlank(script.data(), script.html(), script.text()).trim();
            if (tex.isBlank()) {
                script.remove();
                continue;
            }
            boolean display = script.attr("type").contains("mode=display");
            Element replacement = new Element("span")
                    .addClass("math-tex")
                    .attr("data-tex", tex)
                    .text((display ? "\\[" : "\\(") + tex + (display ? "\\]" : "\\)"));
            script.replaceWith(replacement);
        }
        content.select(".MathJax, .MathJax_Preview, .MJX_Assistive_MathML").remove();
        content.select("script, style").remove();
    }

    private Element firstOrSelf(Element element, String selector) {
        Element selected = element.selectFirst(selector);
        return selected == null ? element : selected;
    }

    private String resolveUrl(String baseUrl, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        try {
            URI raw = URI.create(rawUrl.trim());
            if (raw.isAbsolute() || baseUrl == null || baseUrl.isBlank()) {
                return raw.toString();
            }
            return URI.create(baseUrl.trim()).resolve(raw).toString();
        } catch (IllegalArgumentException e) {
            return rawUrl.trim();
        }
    }

    private String normalizedBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    record RichQuestionContent(
            String contextHtml,
            String questionHtml,
            List<String> answerHtml,
            List<ParsedQuestionAssetDto> assets) {
    }
}
