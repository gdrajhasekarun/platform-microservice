# Platform Microservice (CaaS Discovery Platform)

A homegrown service discovery + API gateway platform:

- **service-catalog** — service registry (register / heartbeat / deregister / discovery)
- **api-gateway** — reverse proxy that dynamically routes requests based on what's registered in the catalog
- **registry-client** — SDKs (Java, Python, TypeScript) that backend services use to register themselves with the catalog

```
client → api-gateway (:8080) → service instance (registered via registry-client)
                ↑                        │
                └── service-catalog (:8761) ──┘
```

## Prerequisites

- Java 17+
- Node.js (for the TypeScript client)
- Python 3.9+ (for the Python client)

## 1. Run service-catalog

Start this first — the gateway depends on it.

```bash
cd service-catalog
./mvnw spring-boot:run
```

Runs on `http://localhost:8761`.

## 2. Run api-gateway

```bash
cd api-gateway
./mvnw spring-boot:run
```

Runs on `http://localhost:8080`. It polls `service-catalog` for registered services and
builds routes automatically — no manual route config needed.

## 3. Register a backend service

Any service you want reachable through the gateway needs to register itself with
`service-catalog` using one of the `registry-client` SDKs, then it becomes available at
`http://localhost:8080/{service-name}/**`.

### Java

Add the dependency (build locally first):

```bash
cd registry-client/java
./mvnw install
```

```xml
<dependency>
  <groupId>com.learning</groupId>
  <artifactId>platform-registry-client</artifactId>
  <version>3.0.0</version>
</dependency>
```

Configure in your service's `application.yml`:

```yaml
catalog:
  url: http://localhost:8761
```

The client auto-registers on startup and deregisters on shutdown.

### Python

```bash
cd registry-client/python
pip install .
```

```python
from registry_client import RegistryClient

client = RegistryClient(catalog_url="http://localhost:8761", name="my-service", port=5000)
client.start()
```

### TypeScript

```bash
cd registry-client/typescript
npm install
npm run build
```

```typescript
import { RegistryClient } from "@learning/registry-client";

const client = new RegistryClient({ catalogUrl: "http://localhost:8761", name: "my-service", port: 3000 });
client.start();
```

## Tech stack

- Java 17, Spring Boot 3.5.11, Spring Cloud 2025.0.0 (`api-gateway`, `service-catalog`)
- Python (`httpx`) and TypeScript (`fetch`) client SDKs
- Maven (Java modules), setuptools (Python), tsc (TypeScript)
