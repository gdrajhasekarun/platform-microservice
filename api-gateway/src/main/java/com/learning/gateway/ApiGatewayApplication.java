package com.learning.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * API gateway. Routes are not statically configured; they are derived at runtime from the service
 * catalog (see {@code CatalogRouteDefinitionLocator}) and periodically refreshed. Scheduling is
 * enabled for the route-refresh job.
 */
@SpringBootApplication
@EnableScheduling
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
