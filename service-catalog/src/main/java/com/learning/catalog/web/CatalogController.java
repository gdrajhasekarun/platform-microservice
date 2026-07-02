package com.learning.catalog.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.learning.catalog.dto.RegisterRequest;
import com.learning.catalog.dto.RegisterResponse;
import com.learning.catalog.model.ServiceInstance;
import com.learning.catalog.registry.ServiceRegistry;

/**
 * REST API of the service catalog.
 */
@RestController
public class CatalogController {

    private final ServiceRegistry registry;

    public CatalogController(ServiceRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        ServiceInstance instance = registry.register(request);
        return new RegisterResponse(instance.getId());
    }

    @PutMapping("/heartbeat/{id}")
    public ResponseEntity<Void> heartbeat(@PathVariable String id) {
        if (!registry.heartbeat(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown instance: " + id);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/deregister/{id}")
    public ResponseEntity<Void> deregister(@PathVariable String id) {
        if (!registry.deregister(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown instance: " + id);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/services")
    public List<ServiceInstance> services() {
        return registry.findAll();
    }

    @GetMapping("/services/{name}")
    public List<ServiceInstance> servicesByName(@PathVariable String name) {
        return registry.findByName(name);
    }
}
