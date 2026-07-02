package com.learning.catalog.registry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.learning.catalog.dto.RegisterRequest;
import com.learning.catalog.model.ServiceInstance;

/**
 * In-memory registry of service instances.
 *
 * <p>Instances are keyed by their generated id. A scheduled job evicts instances whose lease
 * (last heartbeat) is older than {@link #LEASE_TTL}.</p>
 */
@Service
public class ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    /** An instance is evicted if no heartbeat is received within this window. */
    static final Duration LEASE_TTL = Duration.ofSeconds(60);

    private final ConcurrentHashMap<String, ServiceInstance> instances = new ConcurrentHashMap<>();

    /**
     * Registers an instance, or refreshes it if one already exists for the same
     * {@code (name, host, port)}. This dedupes registrations from a client that restarts without
     * a clean shutdown (e.g. killed rather than gracefully stopped): instead of accumulating a new
     * stale entry on every restart, the existing entry's id and lease are reused.
     */
    public ServiceInstance register(RegisterRequest request) {
        ServiceInstance existing = findByHostPort(request.getName(), request.getHost(), request.getPort());
        if (existing != null) {
            existing.setHealthUrl(request.getHealthUrl());
            existing.setMetadata(request.getMetadata() == null ? Map.of() : request.getMetadata());
            existing.setLastHeartbeat(Instant.now());
            log.info("Re-registered {} ({}:{}) reusing instance {}",
                    request.getName(), request.getHost(), request.getPort(), existing.getId());
            return existing;
        }

        String id = UUID.randomUUID().toString();
        ServiceInstance instance = new ServiceInstance(
                id,
                request.getName(),
                request.getHost(),
                request.getPort(),
                request.getHealthUrl(),
                Instant.now(),
                request.getMetadata() == null ? Map.of() : request.getMetadata());
        instances.put(id, instance);
        log.info("Registered {} ({}:{}) as instance {}",
                request.getName(), request.getHost(), request.getPort(), id);
        return instance;
    }

    private ServiceInstance findByHostPort(String name, String host, int port) {
        return instances.values().stream()
                .filter(i -> i.getName().equals(name) && i.getHost().equals(host) && i.getPort() == port)
                .findFirst()
                .orElse(null);
    }

    /**
     * Renew an instance's lease.
     *
     * @return {@code true} if the instance exists, {@code false} otherwise (caller maps to 404).
     */
    public boolean heartbeat(String id) {
        ServiceInstance instance = instances.get(id);
        if (instance == null) {
            return false;
        }
        instance.setLastHeartbeat(Instant.now());
        return true;
    }

    /**
     * Remove an instance.
     *
     * @return {@code true} if an instance was removed, {@code false} if it did not exist.
     */
    public boolean deregister(String id) {
        ServiceInstance removed = instances.remove(id);
        if (removed != null) {
            log.info("Deregistered instance {} ({})", id, removed.getName());
            return true;
        }
        return false;
    }

    /** All currently healthy (non-expired) instances. */
    public List<ServiceInstance> findAll() {
        Instant cutoff = Instant.now().minus(LEASE_TTL);
        return instances.values().stream()
                .filter(i -> i.getLastHeartbeat().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    /** Healthy instances for a named service. */
    public List<ServiceInstance> findByName(String name) {
        return findAll().stream()
                .filter(i -> i.getName().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * Evict instances whose lease has expired. Runs every 30s; removes anything not heard from
     * in the last {@link #LEASE_TTL}.
     */
    @Scheduled(fixedRate = 30_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(LEASE_TTL);
        instances.values().removeIf(instance -> {
            boolean expired = instance.getLastHeartbeat().isBefore(cutoff);
            if (expired) {
                log.info("Evicting expired instance {} ({}); last heartbeat {}",
                        instance.getId(), instance.getName(), instance.getLastHeartbeat());
            }
            return expired;
        });
    }
}
