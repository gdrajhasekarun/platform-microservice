package com.learning.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.learning.gateway.route.CatalogRouteDefinitionLocator;

/**
 * Wires the catalog-backed gateway pieces:
 * <ul>
 *   <li>a {@link WebClient} pointing at the service catalog,</li>
 *   <li>the {@link CatalogRouteDefinitionLocator} that derives routes from it,</li>
 *   <li>the default load-balancer configuration ({@link CatalogLoadBalancerConfig}) used to resolve
 *       every {@code lb://} URI against the catalog.</li>
 * </ul>
 */
@Configuration
@LoadBalancerClients(defaultConfiguration = CatalogLoadBalancerConfig.class)
public class GatewayConfig {

    @Bean
    public WebClient catalogWebClient(@Value("${catalog.url}") String catalogUrl) {
        return WebClient.builder().baseUrl(catalogUrl).build();
    }

    @Bean
    public CatalogRouteDefinitionLocator catalogRouteDefinitionLocator(WebClient catalogWebClient) {
        return new CatalogRouteDefinitionLocator(catalogWebClient);
    }
}
