package com.copmap.auth.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void seedOrUpdateDevUsers() {
        // Seed users disabled as per fresh start requirement
        log.info("DevUserSeeder: Seeding is disabled for fresh start.");
    }
}
