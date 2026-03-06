package org.jh.batchbridge.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jh.batchbridge.service.BatchJobService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppStartupRunner {

    private final BatchJobService batchJobService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application is ready. Syncing unfinished batch jobs...");
        try {
            batchJobService.syncUnfinishedJobs();
        } catch (Exception e) {
            log.error("Error during initial batch job synchronization", e);
        }
    }
}
