package com.learning.registry.client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;

/**
 * Self-registers the host application with the CaaS service catalog and keeps the lease alive.
 *
 * <ul>
 *   <li><b>register</b> &mdash; once the web server is up ({@link ApplicationReadyEvent}), POST to
 *       {@code {catalog.url}/register} and remember the returned instance id.</li>
 *   <li><b>heartbeat</b> &mdash; every {@code catalog.heartbeat-interval} ms, PUT to
 *       {@code {catalog.url}/heartbeat/{id}} to renew the lease.</li>
 *   <li><b>deregister</b> &mdash; on graceful shutdown ({@link PreDestroy}), DELETE
 *       {@code {catalog.url}/deregister/{id}} (the catalog's eviction job covers hard kills).</li>
 * </ul>
 *
 * <p>The service name is taken from {@code spring.application.name} and the port from
 * {@code server.port} (defaulting to 8080). Every network call is guarded so an unreachable
 * catalog never crashes the host process; a failed heartbeat drops the instance id so the next
 * tick re-registers cleanly.</p>
 */
public class ServiceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistrar.class);

    private final RegistryProperties properties;
    private final Environment environment;
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile String instanceId;
    private volatile String resolvedHost;

    public ServiceRegistrar(RegistryProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        register();
    }

    @Scheduled(fixedDelayString = "${catalog.heartbeat-interval:20000}")
    public void heartbeat() {
        String id = instanceId;
        if (id == null) {
            register();
            return;
        }
        try {
            restTemplate.put(properties.getUrl() + "/heartbeat/{id}", null, id);
        } catch (Exception ex) {
            log.warn("Heartbeat failed for instance {} ({}); will re-register on next tick", id, ex.getMessage());
            instanceId = null;
        }
    }

    @PreDestroy
    public void deregister() {
        String id = instanceId;
        if (id == null) {
            return;
        }
        try {
            restTemplate.delete(properties.getUrl() + "/deregister/{id}", id);
            log.info("Deregistered instance {}", id);
        } catch (Exception ex) {
            log.warn("Deregister failed for instance {} ({}); it will be evicted by the catalog's lease TTL",
                    id, ex.getMessage());
        } finally {
            instanceId = null;
        }
    }

    private void register() {
        try {
            String host = resolveHost();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("name", serviceName());
            request.put("host", host);
            request.put("port", servicePort());
            request.put("healthUrl", "http://" + host + ":" + servicePort() + properties.getHealthPath());
            request.put("metadata", properties.getMetadata());

            Map<String, String> response = restTemplate.postForObject(
                    properties.getUrl() + "/register", request, Map.class);
            if (response != null) {
                instanceId = response.get("id");
                log.info("Registered {} with catalog as instance {}", serviceName(), instanceId);
            }
        } catch (Exception ex) {
            log.warn("Could not register {} with catalog at {} ({}); will retry on next heartbeat tick",
                    serviceName(), properties.getUrl(), ex.getMessage());
        }
    }

    /**
     * Resolves the host to advertise, cached after the first lookup. Priority: explicit
     * {@code catalog.host}, then the {@code POD_IP} env var (Kubernetes Downward API convention),
     * then this process's own resolved IP (correct on a Docker network, where the caller and this
     * container share a network but not a loopback), then {@code localhost} as a last resort for
     * bare local runs.
     */
    private String resolveHost() {
        String cached = resolvedHost;
        if (cached != null) {
            return cached;
        }
        String host = properties.getHost();
        if (host == null || host.isBlank()) {
            host = System.getenv("POD_IP");
        }
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                host = "localhost";
            }
        }
        resolvedHost = host;
        return host;
    }

    private String serviceName() {
        return environment.getProperty("spring.application.name");
    }

    private int servicePort() {
        return environment.getProperty("server.port", Integer.class, 8080);
    }
}
