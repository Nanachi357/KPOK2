package com.myprojects.kpok2.service.navigation;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCenterNavigatorTest {

    @Test
    void recognizesAttemptPageByStructureInsteadOfLocalizedText() {
        assertTrue(TestCenterNavigator.isAttemptPage(
                "https://test.testcentr.org.ua/mod/quiz/attempt.php?attempt=456&cmid=173"));
        assertFalse(TestCenterNavigator.isAttemptPage(
                "https://test.testcentr.org.ua/mod/quiz/view.php?id=173"));
        assertFalse(TestCenterNavigator.isAttemptPage(
                "https://test.testcentr.org.ua/mod/quiz/attempt.php?cmid=173"));
    }

    @Test
    void selectsFinishFormInsteadOfReturnToAttemptButton() {
        var document = Jsoup.parse("""
                <div class="submitbtns">
                  <form action="/mod/quiz/attempt.php">
                    <button class="btn btn-secondary" type="submit">Return</button>
                  </form>
                  <form action="/mod/quiz/processattempt.php">
                    <button class="btn btn-primary" type="submit">Submit all</button>
                  </form>
                </div>
                """);

        var matches = document.select(TestCenterNavigator.SUBMIT_ALL_BUTTON_CSS);

        assertEquals(1, matches.size());
        assertEquals("/mod/quiz/processattempt.php", matches.get(0).parent().attr("action"));
    }
}
