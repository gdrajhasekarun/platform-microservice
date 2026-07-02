package com.learning.gateway.loadbalancer;

import java.util.List;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.web.reactive.function.client.WebClient;

import com.learning.gateway.catalog.CatalogServiceInstance;

import reactor.core.publisher.Flux;

/**
 * Supplies load-balancer instances for a given service id by querying the catalog's
 * {@code GET /services/{name}} endpoint, mapping each catalog entry to a {@link DefaultServiceInstance}.
 *
 * <p>This is what makes {@code lb://{name}} URIs resolve against the catalog instead of a static list.</p>
 */
public class CatalogServiceInstanceListSupplier implements ServiceInstanceListSupplier {

    private final String serviceId;
    private final WebClient catalogWebClient;

    public CatalogServiceInstanceListSupplier(String serviceId, WebClient catalogWebClient) {
        this.serviceId = serviceId;
        this.catalogWebClient = catalogWebClient;
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return catalogWebClient.get()
                .uri("/services/{name}", serviceId)
                .retrieve()
                .bodyToFlux(CatalogServiceInstance.class)
                .map(this::toServiceInstance)
                .collectList()
                .flux();
    }

    private ServiceInstance toServiceInstance(CatalogServiceInstance instance) {
        return new DefaultServiceInstance(
                instance.getId(),
                serviceId,
                instance.getHost(),
                instance.getPort(),
                false,
                instance.getMetadata());
    }
}
