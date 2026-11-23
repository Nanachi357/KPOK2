package com.myprojects.kpok2.service.navigation;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebDriverFactoryTest {

    @Test
    void normalCollectionOptionsAreHeadlessByDefault() {
        ChromeOptions options = WebDriverFactory.createChromeOptions(true);

        assertTrue(arguments(options).contains("--headless=new"));
        assertTrue(arguments(options).contains("--window-size=1920,1080"));
    }

    @Test
    void troubleshootingOptionsCanRemainVisible() {
        ChromeOptions options = WebDriverFactory.createChromeOptions(false);

        assertFalse(arguments(options).contains("--headless=new"));
    }

    @Test
    void createsIsolatedProfilePathsForSuccessiveBrowsers() {
        var first = WebDriverFactory.createProfilePath(7L, true, "first");
        var second = WebDriverFactory.createProfilePath(7L, true, "second");

        assertNotEquals(first, second);
        assertTrue(first.toString().contains("kpok2-chrome"));
        assertTrue(first.getFileName().toString().contains("headless"));
    }

    private List<?> arguments(ChromeOptions options) {
        Map<?, ?> chromeOptions = (Map<?, ?>) options.asMap().get("goog:chromeOptions");
        return (List<?>) chromeOptions.get("args");
    }
}
