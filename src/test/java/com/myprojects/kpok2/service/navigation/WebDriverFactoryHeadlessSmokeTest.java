package com.myprojects.kpok2.service.navigation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "KPOK2_HEADLESS_SMOKE_TEST", matches = "(?i)true")
class WebDriverFactoryHeadlessSmokeTest {

    @Test
    void opensAndClosesALocalPageWithoutAWindow() {
        WebDriverFactory factory = new WebDriverFactory(true);
        try {
            WebDriver driver = factory.getDriver();
            driver.get("data:text/html,<title>KPOK2 headless smoke</title>");
            assertEquals("KPOK2 headless smoke", driver.getTitle());
        } finally {
            factory.closeDriver();
        }
    }
}
