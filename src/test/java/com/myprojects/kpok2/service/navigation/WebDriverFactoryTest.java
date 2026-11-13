package com.myprojects.kpok2.service.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebDriverFactoryTest {

    @Test
    void createsIsolatedProfilePathsForSuccessiveBrowsers() {
        var first = WebDriverFactory.createProfilePath(7L, true, "first");
        var second = WebDriverFactory.createProfilePath(7L, true, "second");

        assertNotEquals(first, second);
        assertTrue(first.toString().contains("kpok2-chrome"));
        assertTrue(first.getFileName().toString().contains("headless"));
    }
}
