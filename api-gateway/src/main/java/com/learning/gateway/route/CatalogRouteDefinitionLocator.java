package com.learning.gateway.route;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.web.reactive.function.client.WebClient;

import com.learning.gateway.catalog.CatalogServiceInstance;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Builds one gateway route per service registered in the catalog.
 *
 * <p>For a service named {@code foo}, the route is:</p>
 * <pre>
 *   id:        foo
 *   uri:       lb://foo                 (resolved by the catalog-backed load balancer)
 *   predicate: Path=/foo/**
 *   filter:    StripPrefix=1            (drops the /foo prefix before forwarding)
 * </pre>
 *
 * <p>The {@code StripPrefix=1} filter is omitted when a registered instance carries
 * {@code metadata.stripPrefix=false}. This is needed by services that are themselves aware of
 * being mounted under a subpath (e.g. a Vite dev server configured with a matching {@code base})
 * and therefore need the full {@code /foo/**} path preserved, not stripped &mdash; stripping it
 * would otherwise send them a path they don't recognize and cause a redirect loop back to their
 * own base path.</p>
 *
 * <p>The last successfully fetched route set is cached so the gateway keeps routing even when the
 * catalog is temporarily unreachable.</p>
 */
public class CatalogRouteDefinitionLocator implements RouteDefinitionLocator {

    private static final Logger log = LoggerFactory.getLogger(CatalogRouteDefinitionLocator.class);

    private final WebClient catalogWebClient;

    private volatile List<RouteDefinition> lastKnown = new ArrayList<>();

    public CatalogRouteDefinitionLocator(WebClient catalogWebClient) {
        this.catalogWebClient = catalogWebClient;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return catalogWebClient.get()
                .uri("/services")
                .retrieve()
                .bodyToFlux(CatalogServiceInstance.class)
                .collectList()
                .map(this::buildRoutes)
                .doOnNext(routes -> this.lastKnown = routes)
                .onErrorResume(ex -> {
                    log.warn("Could not fetch routes from catalog ({}); serving {} cached route(s)",
                            ex.getMessage(), lastKnown.size());
                    return Mono.just(lastKnown);
                })
                .flatMapMany(Flux::fromIterable);
    }

    private List<RouteDefinition> buildRoutes(List<CatalogServiceInstance> instances) {
        // Group by service name (there may be several instances of the same service) so each
        // service still yields exactly one route; the first instance's metadata decides whether
        // that route strips its prefix.
        return instances.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CatalogServiceInstance::getName,
                        i -> i,
                        (first, second) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream()
                .map(this::buildRoute)
                .collect(java.util.stream.Collectors.toList());
    }

    private RouteDefinition buildRoute(CatalogServiceInstance instance) {
        String serviceName = instance.getName();
        RouteDefinition route = new RouteDefinition();
        route.setId(serviceName);
        route.setUri(URI.create("lb://" + serviceName));
        // The text-arg constructors parse "Name=args" exactly like YAML route config would.
        route.setPredicates(List.of(new PredicateDefinition("Path=/" + serviceName + "/**")));
        route.setFilters(stripPrefixFilters(instance));
        return route;
    }

    private List<FilterDefinition> stripPrefixFilters(CatalogServiceInstance instance) {
        String stripPrefix = instance.getMetadata() == null ? null : instance.getMetadata().get("stripPrefix");
        if ("false".equalsIgnoreCase(stripPrefix)) {
            return List.of();
        }
        return List.of(new FilterDefinition("StripPrefix=1"));
    }
}
