package com.myprojects.kpok2.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuizTargetTest {

    @Test
    void parsesQuizViewUrlAndBuildsAttemptUrls() {
        QuizTarget target = QuizTarget.fromUrl(
                "https://test.testcentr.org.ua/mod/quiz/view.php?section=all&id=173");

        assertEquals(173, target.courseModuleId());
        assertEquals("https://test.testcentr.org.ua/mod/quiz/view.php?id=173", target.viewUrl());
        assertEquals(
                "https://test.testcentr.org.ua/mod/quiz/summary.php?attempt=2593848&cmid=173",
                target.summaryUrl("2593848"));
        assertEquals(
                "https://test.testcentr.org.ua/mod/quiz/review.php?attempt=2593848&cmid=173&showall=1",
                target.reviewUrl("2593848"));
    }

    @Test
    void rejectsPagesThatAreNotSupportedQuizViews() {
        assertThrows(IllegalArgumentException.class,
                () -> QuizTarget.fromUrl("https://example.com/mod/quiz/view.php?id=173"));
        assertThrows(IllegalArgumentException.class,
                () -> QuizTarget.fromUrl("https://test.testcentr.org.ua/course/view.php?id=173"));
        assertThrows(IllegalArgumentException.class,
                () -> QuizTarget.fromUrl("https://test.testcentr.org.ua/mod/quiz/view.php"));
    }
}
