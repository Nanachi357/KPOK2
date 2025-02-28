package com.myprojects.kpok2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,    // Core pool size
                5,    // Max pool size
                60,   // Keep alive time
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10)  // Task queue
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}