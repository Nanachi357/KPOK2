package com.myprojects.kpok2.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Backfills normalized origin rows from the legacy source columns. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionOriginBackfill implements ApplicationRunner {

    private final QuestionOriginService questionOriginService;

    @Override
    public void run(ApplicationArguments args) {
        int inserted = questionOriginService.backfillLegacyOrigins();
        if (inserted > 0) {
            log.info("Backfilled {} question origin records", inserted);
        }
    }
}
