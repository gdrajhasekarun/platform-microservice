package com.learning.catalog.model;

import java.time.Instant;
import java.util.Map;

/**
 * A registered service instance and its current lease.
 */
public class ServiceInstance {

    private String id;
    private String name;
    private String host;
    private int port;
    private String healthUrl;
    private Instant lastHeartbeat;
    private Map<String, String> metadata;

    public ServiceInstance() {
    }

    public ServiceInstance(String id, String name, String host, int port, String healthUrl,
                           Instant lastHeartbeat, Map<String, String> metadata) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.healthUrl = healthUrl;
        this.lastHeartbeat = lastHeartbeat;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHealthUrl() {
        return healthUrl;
    }

    public void setHealthUrl(String healthUrl) {
        this.healthUrl = healthUrl;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
