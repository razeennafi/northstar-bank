/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: StatementStreamingConfiguration.java
 * Description: Keeps statement streaming off request threads and bounds concurrent snapshot work.
 * Project version: 0.1.0
 */

package com.northstar.banking.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Keeps statement streaming off request threads and bounds concurrent snapshot work. */
@Configuration
public class StatementStreamingConfiguration implements WebMvcConfigurer {
    /** Bounds the worker count and queue for streamed exports. */
    @Bean
    ThreadPoolTaskExecutor statementExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("statement-");
        return executor;
    }

    /** Assigns streamed responses to the dedicated executor and sets their timeout. */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(statementExecutor()).setDefaultTimeout(45_000);
    }
}
