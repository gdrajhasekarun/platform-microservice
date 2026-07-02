package com.learning.gateway.error;

import java.util.Map;

import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * Turns a "no live instance for this route" failure into a clean {@code 503} JSON response,
 * instead of the opaque default Whitelabel/500 page.
 *
 * <p>This happens when a route exists (the catalog knows the service name) but
 * {@link com.learning.gateway.loadbalancer.CatalogServiceInstanceListSupplier} resolves zero
 * instances for it &mdash; e.g. right after the backing service's lease expired, or during the
 * brief window after a restart before it re-registers. It is distinct from a genuinely unmatched
 * route, which is left to fall through to the default 404 handling.</p>
 *
 * <p>Ordered ahead of Spring Boot's {@code DefaultErrorWebExceptionHandler} (order {@code -1}) so
 * it gets first look at the exception.</p>
 */
@Component
@Order(-2)
public class NoInstanceAvailableExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!isNoInstanceAvailable(ex)) {
            return Mono.error(ex);
        }

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "status", 503,
                "error", "Service Unavailable",
                "message", "No live instance is currently registered for this service in the catalog",
                "path", exchange.getRequest().getPath().value());

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationError) {
            return Mono.error(ex);
        }
        return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(bytes)));
    }

    private boolean isNoInstanceAvailable(Throwable ex) {
        return ex instanceof NotFoundException
                || (ex instanceof WebClientRequestException && ex.getCause() instanceof NotFoundException);
    }
}
