package com.learning.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Service catalog: a lightweight, in-memory service registry (the platform's replacement for Eureka).
 * Scheduling is enabled for the lease-eviction job.
 */
@SpringBootApplication
@EnableScheduling
public class ServiceCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceCatalogApplication.class, args);
    }
}
