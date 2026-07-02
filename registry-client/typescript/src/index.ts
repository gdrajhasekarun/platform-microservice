/**
 * Self-registration client for the CaaS service catalog.
 *
 * Announces a service to the catalog and keeps its lease alive:
 *
 *   start()  -> POST {catalogUrl}/register, remember the instance id
 *   (every heartbeatInterval ms) -> PUT {catalogUrl}/heartbeat/{id}
 *   stop()   -> DELETE {catalogUrl}/deregister/{id}
 *
 * Every network call is guarded so an unreachable catalog never crashes the host
 * process; a failed heartbeat drops the instance id so the next tick re-registers.
 */

import { networkInterfaces } from "os";

const DEFAULT_HEARTBEAT_INTERVAL_MS = 20_000;
const DEFAULT_HEALTH_PATH = "/actuator/health";
const DEFAULT_TIMEOUT_MS = 5_000;

/**
 * Best-effort host to advertise when the caller doesn't pass one explicitly. `localhost` is only
 * correct for a single-process local run: in Docker/Kubernetes it resolves to the caller's own
 * network namespace, not this instance. Priority: `POD_IP` env var (Kubernetes Downward API
 * convention), then this process's own non-internal IPv4 address (correct on a Docker network),
 * then `localhost` as a last resort.
 */
function detectHost(): string {
  if (process.env.POD_IP) {
    return process.env.POD_IP;
  }
  for (const addresses of Object.values(networkInterfaces())) {
    for (const addr of addresses ?? []) {
      if (addr.family === "IPv4" && !addr.internal) {
        return addr.address;
      }
    }
  }
  return "localhost";
}

export interface RegistryClientOptions {
  catalogUrl: string;
  name: string;
  port: number;
  host?: string;
  heartbeatInterval?: number;
  healthPath?: string;
  metadata?: Record<string, string>;
  handleSignals?: boolean;
}

interface RegisterResponse {
  id: string;
}

export class RegistryClient {
  private readonly catalogUrl: string;
  private readonly name: string;
  private readonly host: string;
  private readonly port: number;
  private readonly heartbeatInterval: number;
  private readonly healthPath: string;
  private readonly metadata: Record<string, string>;

  private instanceId: string | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(options: RegistryClientOptions) {
    this.catalogUrl = options.catalogUrl.replace(/\/$/, "");
    this.name = options.name;
    this.host = options.host ?? detectHost();
    this.port = options.port;
    this.heartbeatInterval = options.heartbeatInterval ?? DEFAULT_HEARTBEAT_INTERVAL_MS;
    this.healthPath = options.healthPath ?? DEFAULT_HEALTH_PATH;
    this.metadata = options.metadata ?? {};

    process.once("beforeExit", () => this.stop());
    if (options.handleSignals) {
      process.once("SIGTERM", () => this.stop());
      process.once("SIGINT", () => this.stop());
    }
  }

  getInstanceId(): string | null {
    return this.instanceId;
  }

  async start(): Promise<void> {
    await this.register();
    this.timer = setInterval(() => {
      void (this.instanceId === null ? this.register() : this.heartbeat());
    }, this.heartbeatInterval);
    this.timer.unref?.();
  }

  async stop(): Promise<void> {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    await this.deregister();
  }

  private async register(): Promise<void> {
    const body = {
      name: this.name,
      host: this.host,
      port: this.port,
      healthUrl: `http://${this.host}:${this.port}${this.healthPath}`,
      metadata: this.metadata,
    };
    try {
      const response = await this.fetchWithTimeout(`${this.catalogUrl}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        throw new Error(`unexpected status ${response.status}`);
      }
      const parsed = (await response.json()) as RegisterResponse;
      this.instanceId = parsed.id;
      console.info(`Registered ${this.name} with catalog as instance ${this.instanceId}`);
    } catch (err) {
      console.warn(
        `Could not register ${this.name} with catalog at ${this.catalogUrl} (${(err as Error).message}); will retry on next heartbeat tick`,
      );
    }
  }

  private async heartbeat(): Promise<void> {
    const instanceId = this.instanceId;
    try {
      const response = await this.fetchWithTimeout(`${this.catalogUrl}/heartbeat/${instanceId}`, {
        method: "PUT",
      });
      if (!response.ok) {
        throw new Error(`unexpected status ${response.status}`);
      }
    } catch (err) {
      console.warn(
        `Heartbeat failed for instance ${instanceId} (${(err as Error).message}); will re-register on next tick`,
      );
      this.instanceId = null;
    }
  }

  private async deregister(): Promise<void> {
    const instanceId = this.instanceId;
    if (instanceId === null) {
      return;
    }
    try {
      const response = await this.fetchWithTimeout(`${this.catalogUrl}/deregister/${instanceId}`, {
        method: "DELETE",
      });
      if (!response.ok && response.status !== 404) {
        throw new Error(`unexpected status ${response.status}`);
      }
      console.info(`Deregistered instance ${instanceId}`);
    } catch (err) {
      console.warn(
        `Deregister failed for instance ${instanceId} (${(err as Error).message}); it will be evicted by the catalog's lease TTL`,
      );
    } finally {
      this.instanceId = null;
    }
  }

  private async fetchWithTimeout(url: string, init: RequestInit): Promise<Response> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);
    try {
      return await fetch(url, { ...init, signal: controller.signal });
    } finally {
      clearTimeout(timeout);
    }
  }
}
