package com.learning.gateway.catalog;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * View of a catalog {@code ServiceInstance} as returned by the catalog's {@code /services} endpoints.
 * Only the fields the gateway needs are mapped; unknown fields (e.g. lastHeartbeat) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogServiceInstance {

    private String id;
    private String name;
    private String host;
    private int port;
    private String healthUrl;
    private Map<String, String> metadata;

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

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
