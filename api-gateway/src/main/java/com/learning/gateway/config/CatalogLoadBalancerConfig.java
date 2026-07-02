package com.learning.gateway.config;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

import com.learning.gateway.loadbalancer.CatalogServiceInstanceListSupplier;

/**
 * Per-client load-balancer configuration. This class is intentionally NOT annotated with
 * {@code @Configuration}: Spring Cloud LoadBalancer instantiates it once per service inside a
 * dedicated child context (wired via {@code @LoadBalancerClients(defaultConfiguration = ...)} on
 * {@link GatewayConfig}). The target service id is read from that child context's environment.
 */
public class CatalogLoadBalancerConfig {

    @Bean
    public ServiceInstanceListSupplier catalogServiceInstanceListSupplier(
            Environment environment, WebClient catalogWebClient) {
        String serviceId = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new CatalogServiceInstanceListSupplier(serviceId, catalogWebClient);
    }
}
