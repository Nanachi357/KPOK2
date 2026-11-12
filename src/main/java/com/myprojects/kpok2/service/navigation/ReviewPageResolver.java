package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.model.QuizTarget;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves every review page for a completed attempt without assuming a fixed
 * number of Moodle pagination pages.
 */
@Component
public class ReviewPageResolver {

    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(5);

    public List<String> resolve(NavigationSession session) {
        if (!session.hasActiveAttempt()) {
            return List.of();
        }

        QuizTarget target = session.getQuizTarget();
        String attemptId = session.getAttemptId();
        String showAllUrl = target.reviewUrl(attemptId);
        WebDriver driver = session.getWebDriver();

        driver.get(showAllUrl);
        new WebDriverWait(driver, PAGE_LOAD_TIMEOUT)
                .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#page-content")));

        String currentUrl = driver.getCurrentUrl();
        session.updateUrl(currentUrl);
        return resolve(target, attemptId, currentUrl, driver.getPageSource());
    }

    /**
     * Pure HTML/URL part of resolution, kept separate so pagination behavior
     * can be covered by fixture-based tests without starting a browser.
     */
    List<String> resolve(QuizTarget target, String attemptId, String currentUrl, String html) {
        Document document = Jsoup.parse(html, currentUrl);

        boolean showAllLoaded = "1".equals(QuizTarget.queryParameter(currentUrl, "showall").orElse(null))
                && !document.select("div.que, div[id^=question-], div.question").isEmpty();
        if (showAllLoaded) {
            return List.of(currentUrl);
        }

        Set<String> reviewUrls = new LinkedHashSet<>();
        if (target.matchesReviewUrl(currentUrl, attemptId)) {
            reviewUrls.add(currentUrl);
        }

        for (Element link : document.select("a[href*='/mod/quiz/review.php']")) {
            String url = link.absUrl("href");
            if (target.matchesReviewUrl(url, attemptId)) {
                reviewUrls.add(url);
            }
        }

        Optional<String> discoveredShowAll = reviewUrls.stream()
                .filter(url -> "1".equals(QuizTarget.queryParameter(url, "showall").orElse(null)))
                .findFirst();
        if (discoveredShowAll.isPresent()) {
            return List.of(discoveredShowAll.get());
        }

        List<String> pages = new ArrayList<>(reviewUrls);
        pages.sort(Comparator.comparingInt(ReviewPageResolver::pageNumber));
        return pages.isEmpty() ? List.of(target.reviewUrl(attemptId)) : List.copyOf(pages);
    }

    private static int pageNumber(String url) {
        return QuizTarget.queryParameter(url, "page")
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        return Integer.MAX_VALUE;
                    }
                })
                .orElse(0);
    }
}
