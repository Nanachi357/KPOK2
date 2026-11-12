package com.myprojects.kpok2.service.navigation;

import org.junit.jupiter.api.Test;

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
}
