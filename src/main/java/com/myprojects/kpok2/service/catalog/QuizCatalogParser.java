package com.myprojects.kpok2.service.catalog;

import com.myprojects.kpok2.model.QuizCatalogEntry;
import com.myprojects.kpok2.model.QuizTarget;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Moodle activity links without depending on the interface language.
 */
@Component
public class QuizCatalogParser {

    private static final String QUIZ_LINK_SELECTOR = "a[href*='/mod/quiz/view.php?id=']";
    private static final Pattern KROK_PATTERN = Pattern.compile(
            "(?iu)(?:^|\\s)(?:крок|krok)\\s*([123])(?:\\s|$)");
    private static final Pattern UKRAINIAN_PATTERN = Pattern.compile(
            "(?iu)(?:\\((?:ua|uk|ukr)\\)|\\b(?:українська|українською|ukrainian)\\b)");
    private static final Pattern ENGLISH_PATTERN = Pattern.compile(
            "(?iu)(?:\\((?:en|eng)\\)|\\b(?:англійська|англійською|english)\\b)");
    private static final Pattern LANGUAGE_MARKER_PATTERN = Pattern.compile(
            "(?iu)\\s*(?:\\((?:ua|uk|ukr|en|eng)\\)|"
                    + "\\b(?:українська|українською|ukrainian|англійська|англійською|english)\\b)\\s*");
    private static final Pattern CYRILLIC_PATTERN = Pattern.compile("\\p{IsCyrillic}");
    private static final Pattern LATIN_PATTERN = Pattern.compile("[A-Za-z]");

    public List<QuizCatalogEntry> parse(Document document) {
        Map<Long, QuizCatalogEntry> uniqueEntries = new LinkedHashMap<>();

        for (Element link : document.select(QUIZ_LINK_SELECTOR)) {
            String absoluteUrl = link.absUrl("href");
            if (absoluteUrl.isBlank()) {
                continue;
            }

            QuizTarget target;
            try {
                target = QuizTarget.fromUrl(absoluteUrl);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            String title = quizTitle(link);
            if (title.isBlank()) {
                continue;
            }

            String section = sectionTitle(link);
            String combined = (section + " " + title).trim();
            String krokLevel = krokLevel(combined);
            String language = language(title, combined);
            String specialty = specialty(title, krokLevel);

            uniqueEntries.putIfAbsent(target.courseModuleId(), new QuizCatalogEntry(
                    target.courseModuleId(),
                    title,
                    blankToNull(section),
                    krokLevel,
                    specialty,
                    language,
                    target.viewUrl()));
        }

        return new ArrayList<>(uniqueEntries.values());
    }

    private String quizTitle(Element link) {
        Element titleElement = link.selectFirst(".instancename");
        Element clean = (titleElement == null ? link : titleElement).clone();
        clean.select(".accesshide, .sr-only").remove();
        return clean.text().trim();
    }

    private String sectionTitle(Element link) {
        Element current = link.parent();
        while (current != null) {
            if (current.is("li.section, section.course-section, li.course-section, div.course-section")) {
                Element heading = current.selectFirst(
                        ".sectionname, .course-section-header h2, .course-section-header h3");
                return heading == null ? "" : heading.text().trim();
            }
            current = current.parent();
        }
        return "";
    }

    private String krokLevel(String text) {
        Matcher matcher = KROK_PATTERN.matcher(text);
        return matcher.find() ? "KROK " + matcher.group(1) : null;
    }

    private String language(String title, String context) {
        if (ENGLISH_PATTERN.matcher(context).find()) {
            return "en";
        }
        if (UKRAINIAN_PATTERN.matcher(context).find()) {
            return "uk";
        }
        if (CYRILLIC_PATTERN.matcher(title).find()) {
            return "uk";
        }
        if (LATIN_PATTERN.matcher(title).find()) {
            return "en";
        }
        return null;
    }

    private String specialty(String title, String krokLevel) {
        String value = title;
        if (krokLevel != null) {
            value = KROK_PATTERN.matcher(" " + value + " ").replaceFirst(" ");
        }
        value = LANGUAGE_MARKER_PATTERN.matcher(value).replaceAll(" ")
                .replaceAll("^[\\s—–:-]+|[\\s—–:-]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return blankToNull(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
