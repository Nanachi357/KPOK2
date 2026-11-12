package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.model.QuizTarget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewPageResolverTest {

    private final ReviewPageResolver resolver = new ReviewPageResolver();
    private final QuizTarget target = QuizTarget.fromUrl(
            "https://test.testcentr.org.ua/mod/quiz/view.php?id=173");

    @Test
    void keepsOneUrlWhenMoodleLoadsAllQuestions() throws IOException {
        String showAllUrl = target.reviewUrl("456");

        assertEquals(
                List.of(showAllUrl),
                resolver.resolve(target, "456", showAllUrl, fixture("review-show-all.html")));
    }

    @Test
    void discoversAndSortsAnyNumberOfPaginationLinks() throws IOException {
        String currentUrl =
                "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=456&cmid=173&page=0";

        assertEquals(
                List.of(
                        currentUrl,
                        "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=456&cmid=173&page=1",
                        "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=456&cmid=173&page=2",
                        "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=456&cmid=173&page=7"),
                resolver.resolve(target, "456", currentUrl, fixture("review-paginated.html")));
    }

    private String fixture(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/fixtures/moodle/" + name)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
