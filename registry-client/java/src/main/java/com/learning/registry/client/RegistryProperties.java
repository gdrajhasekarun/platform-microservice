package com.learning.registry.client;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the CaaS service-catalog registry client.
 *
 * <p>Bound from the {@code catalog.*} prefix. Registration is enabled only when {@code catalog.url}
 * is present (see {@link RegistryClientAutoConfiguration}).</p>
 */
@ConfigurationProperties(prefix = "catalog")
public class RegistryProperties {

    /** Base URL of the service catalog, e.g. {@code http://localhost:8761}. Required to enable registration. */
    private String url;

    /**
     * Host advertised to the catalog for this instance. If left unset, {@link ServiceRegistrar}
     * auto-detects one (in order: the {@code POD_IP} env var, then the container/host's own
     * resolved IP, then {@code localhost}) since {@code localhost} is only ever correct for a
     * single-process local run &mdash; in Docker/Kubernetes it would resolve to the caller's own
     * network namespace instead of this instance.
     */
    private String host;

    /** Heartbeat interval in milliseconds. The catalog evicts after 60s, so this gives ~3 attempts. */
    private long heartbeatInterval = 20_000L;

    /** Path (relative to this service) of the health endpoint advertised to the catalog. */
    private String healthPath = "/actuator/health";

    /** Arbitrary metadata sent with the registration. */
    private Map<String, String> metadata = new LinkedHashMap<>();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
