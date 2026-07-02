package com.learning.gateway.refresh;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically tells the gateway to re-evaluate its route definitions, so newly registered (or
 * deregistered) services are picked up without a restart. The {@link RefreshRoutesEvent} triggers
 * the {@code CatalogRouteDefinitionLocator} to re-query the catalog.
 */
@Component
public class RouteRefreshScheduler {

    private final ApplicationEventPublisher publisher;

    public RouteRefreshScheduler(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelay = 30_000)
    public void refreshRoutes() {
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
