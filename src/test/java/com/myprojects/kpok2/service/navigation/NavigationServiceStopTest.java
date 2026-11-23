package com.myprojects.kpok2.service.navigation;

import com.myprojects.kpok2.config.TestCenterProperties;
import com.myprojects.kpok2.model.QuizTarget;
import com.myprojects.kpok2.model.dto.ReviewAttemptResult;
import com.myprojects.kpok2.model.dto.ReviewPageParsingResult;
import com.myprojects.kpok2.service.AccountConfigurationService;
import com.myprojects.kpok2.service.parser.ReviewAttemptProcessor;
import com.myprojects.kpok2.service.parser.TestParsingStatistics;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NavigationServiceStopTest {

    @Test
    void immediateStopAfterAuthenticationPreventsEveryLaterWebsiteAction() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch authenticationEntered = new CountDownLatch(1);
        CountDownLatch releaseAuthentication = new CountDownLatch(1);
        when(fixture.navigator.authenticate(fixture.session)).thenAnswer(invocation -> {
            authenticationEntered.countDown();
            awaitIgnoringInterrupt(releaseAuthentication);
            return true;
        });

        try {
            assertTrue(fixture.service.startNavigation());
            assertTrue(authenticationEntered.await(2, TimeUnit.SECONDS));
            assertTrue(fixture.service.stopNow());
            releaseAuthentication.countDown();

            verify(fixture.navigator, after(1_000).never())
                    .navigateToTestPage(fixture.session);
            verify(fixture.navigator, never()).clickAttemptTestButton(fixture.session);
            verify(fixture.navigator, never()).clickStartAttemptButton(fixture.session);
            verify(fixture.navigator, never()).clickSubmitAllButton(fixture.session);
            verify(fixture.navigator, never()).clickConfirmSubmitButton(fixture.session);
            verify(fixture.session, timeout(2_000)).close();
            awaitStopped(fixture.service);
        } finally {
            releaseAuthentication.countDown();
            fixture.service.shutdown();
        }
    }

    @Test
    void gracefulStopBeforeAttemptPreventsStartingNewAttempt() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch authenticationEntered = new CountDownLatch(1);
        CountDownLatch releaseAuthentication = new CountDownLatch(1);
        when(fixture.navigator.authenticate(fixture.session)).thenAnswer(invocation -> {
            authenticationEntered.countDown();
            awaitIgnoringInterrupt(releaseAuthentication);
            return true;
        });

        try {
            assertTrue(fixture.service.startNavigation());
            assertTrue(authenticationEntered.await(2, TimeUnit.SECONDS));
            assertTrue(fixture.service.requestStopAfterCurrent());
            releaseAuthentication.countDown();

            verify(fixture.navigator, after(1_500).never())
                    .navigateToTestPage(fixture.session);
            verify(fixture.navigator, never()).clickAttemptTestButton(fixture.session);
            verify(fixture.session, timeout(2_000)).close();
            awaitStopped(fixture.service);
        } finally {
            releaseAuthentication.countDown();
            fixture.service.shutdown();
        }
    }

    @Test
    void immediateStopAfterResumingAttemptPreventsFinishAndSubmission() throws Exception {
        Fixture fixture = new Fixture();
        TestCenterNavigator resultOwner = new TestCenterNavigator();
        when(fixture.navigator.authenticate(fixture.session)).thenReturn(true);
        when(fixture.navigator.navigateToTestPage(fixture.session)).thenReturn(true);
        when(fixture.navigator.clickAttemptTestButton(fixture.session)).thenAnswer(invocation -> {
            fixture.runControl.stopNow();
            return resultOwner.new AttemptButtonResult(true, true);
        });

        try {
            assertTrue(fixture.service.startNavigation());

            verify(fixture.navigator, timeout(4_000)).clickAttemptTestButton(fixture.session);
            verify(fixture.navigator, after(500).never()).clickFinishAttemptLink(fixture.session);
            verify(fixture.navigator, never()).clickSubmitAllButton(fixture.session);
            verify(fixture.navigator, never()).clickConfirmSubmitButton(fixture.session);
            verify(fixture.session, timeout(2_000)).close();
            awaitStopped(fixture.service);
        } finally {
            fixture.service.shutdown();
        }
    }

    @Test
    void gracefulStopFinishesActiveAttemptWithoutPostAttemptDelay() throws Exception {
        Fixture fixture = new Fixture();
        TestCenterNavigator resultOwner = new TestCenterNavigator();
        when(fixture.navigator.authenticate(fixture.session)).thenReturn(true);
        when(fixture.navigator.navigateToTestPage(fixture.session)).thenReturn(true);
        when(fixture.navigator.clickAttemptTestButton(fixture.session)).thenAnswer(invocation -> {
            fixture.runControl.requestStopAfterCurrent();
            return resultOwner.new AttemptButtonResult(true, true);
        });
        when(fixture.navigator.clickFinishAttemptLink(fixture.session)).thenReturn(true);
        when(fixture.navigator.clickSubmitAllButton(fixture.session)).thenReturn(true);
        when(fixture.navigator.clickConfirmSubmitButton(fixture.session)).thenReturn(true);
        when(fixture.reviewPageResolver.resolve(fixture.session)).thenReturn(List.of("review"));
        when(fixture.reviewAttemptProcessor.process(List.of("review"), fixture.session))
                .thenReturn(new ReviewAttemptResult(List.of(
                        new ReviewPageParsingResult("review", 1, 1, 1, null))));
        when(fixture.statistics.incrementCompletedIterations()).thenReturn(1);
        when(fixture.statistics.getTotalIterationsNeeded()).thenReturn(1);
        when(fixture.navigationProperties.getThreadTimeoutSeconds()).thenReturn(60);

        try {
            assertTrue(fixture.service.startNavigation());

            verify(fixture.navigator, timeout(6_000))
                    .clickConfirmSubmitButton(fixture.session);
            verify(fixture.reviewAttemptProcessor, timeout(1_000))
                    .process(List.of("review"), fixture.session);
            verify(fixture.session, timeout(2_000)).close();
            awaitStopped(fixture.service);
            assertFalse(fixture.service.isRunning());
        } finally {
            fixture.service.shutdown();
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitStopped(NavigationService service) {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (service.isRunning()) {
                Thread.sleep(10);
            }
        });
    }

    private static final class Fixture {
        private final TestCenterProperties properties = mock(TestCenterProperties.class);
        private final TestCenterProperties.NavigationProperties navigationProperties =
                mock(TestCenterProperties.NavigationProperties.class);
        private final TestCenterNavigator navigator = mock(TestCenterNavigator.class);
        private final NavigationSessionFactory sessionFactory = mock(NavigationSessionFactory.class);
        private final TestParsingStatistics statistics = mock(TestParsingStatistics.class);
        private final AccountConfigurationService accountService =
                mock(AccountConfigurationService.class);
        private final ReviewPageResolver reviewPageResolver = mock(ReviewPageResolver.class);
        private final ReviewAttemptProcessor reviewAttemptProcessor =
                mock(ReviewAttemptProcessor.class);
        private final NavigationRunControl runControl = new NavigationRunControl();
        private final NavigationSession session = mock(NavigationSession.class);
        private final NavigationService service;

        private Fixture() throws InterruptedException {
            QuizTarget target = QuizTarget.fromUrl(
                    "https://test.testcentr.org.ua/mod/quiz/view.php?id=173");
            when(properties.getNavigation()).thenReturn(navigationProperties);
            when(navigationProperties.getThreadTimeoutSeconds()).thenReturn(0);
            when(accountService.getMaxThreads()).thenReturn(1);
            when(accountService.getIterationCount()).thenReturn(1);
            when(accountService.isReuseSession()).thenReturn(false);
            when(accountService.getQuizTarget()).thenReturn(target);
            when(sessionFactory.createSession()).thenReturn(session);
            when(session.getAccount()).thenReturn(new AccountCredentials("audit", "audit"));
            when(session.getWebDriver()).thenReturn(mock(WebDriver.class));
            when(session.getQuizTarget()).thenReturn(target);

            service = new NavigationService(
                    properties,
                    navigator,
                    sessionFactory,
                    statistics,
                    accountService,
                    reviewPageResolver,
                    reviewAttemptProcessor,
                    runControl);
        }
    }
}
