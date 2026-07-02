package com.learning.gateway.logging;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Logs which catalog-resolved backend instance actually served each request: the matched route
 * id (service name), the load-balanced target {@code host:port}, response status, and latency.
 *
 * <p>Ordered to run after {@code ReactiveLoadBalancerClientFilter} (order {@code 10100}), so
 * {@link ServerWebExchangeUtils#GATEWAY_REQUEST_URL_ATTR} already holds the real instance URI
 * (not the {@code lb://serviceName} placeholder) by the time this filter reads it.</p>
 */
@Component
public class GatewayAccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayAccessLogFilter.class);

    private static final int ORDER = 10_150;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAt = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            URI targetUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            Integer status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : null;
            long durationMs = System.currentTimeMillis() - startedAt;

            log.info("{} {} -> service={} target={} status={} ({} ms)",
                    request.getMethod(),
                    request.getPath().value(),
                    route != null ? route.getId() : "unmatched",
                    targetUri != null ? targetUri : "n/a",
                    status,
                    durationMs);
        }));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
