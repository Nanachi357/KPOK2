package com.myprojects.kpok2.service.navigation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationManager {
    private final NavigationService navigationService;

    public boolean startNavigation() {
        return navigationService.startNavigation();
    }

    public boolean requestStopAfterCurrent() {
        return navigationService.requestStopAfterCurrent();
    }

    /** The desktop's default stop action is deliberately graceful. */
    public boolean stopNavigation() {
        return requestStopAfterCurrent();
    }

    public boolean stopNow() {
        return navigationService.stopNow();
    }

    public boolean isRunning() {
        return navigationService.isRunning();
    }

    public boolean isStopAfterCurrentRequested() {
        return navigationService.isStopAfterCurrentRequested();
    }
}
