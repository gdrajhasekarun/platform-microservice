package com.learning.registry.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configures a {@link ServiceRegistrar} so any Spring Boot service can self-register with the
 * CaaS service catalog simply by adding this library to the classpath.
 *
 * <p>Activated only when {@code catalog.url} is set, so the dependency is inert in environments
 * (tests, local-without-catalog) that don't configure a catalog. {@link EnableScheduling} is
 * declared here for the heartbeat; it is idempotent if the host app already enables scheduling.</p>
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(RegistryProperties.class)
@ConditionalOnProperty(prefix = "catalog", name = "url")
public class RegistryClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceRegistrar serviceRegistrar(RegistryProperties properties, Environment environment) {
        return new ServiceRegistrar(properties, environment);
    }
}
