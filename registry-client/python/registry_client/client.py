"""Self-registration client for the CaaS service catalog.

Announces a service to the catalog and keeps its lease alive:

    start()  -> POST {catalog_url}/register, remember the instance id
    (every heartbeat_interval seconds) -> PUT {catalog_url}/heartbeat/{id}
    stop()   -> DELETE {catalog_url}/deregister/{id}

Every network call is guarded so an unreachable catalog never crashes the host
process; a failed heartbeat drops the instance id so the next tick re-registers.
"""

import atexit
import logging
import os
import signal
import socket
import threading
from typing import Dict, Optional

import httpx

logger = logging.getLogger(__name__)

DEFAULT_HEARTBEAT_INTERVAL = 20.0
DEFAULT_HEALTH_PATH = "/actuator/health"
DEFAULT_TIMEOUT = 5.0


def _detect_host() -> str:
    """Best-effort host to advertise when the caller doesn't pass one explicitly.

    ``localhost`` is only correct for a single-process local run: in Docker/Kubernetes
    it resolves to the caller's own network namespace, not this instance. Priority:
    ``POD_IP`` env var (Kubernetes Downward API convention), then this process's own
    resolved IP (correct on a Docker network), then ``localhost`` as a last resort.
    """
    pod_ip = os.environ.get("POD_IP")
    if pod_ip:
        return pod_ip
    try:
        return socket.gethostbyname(socket.gethostname())
    except OSError:
        return "localhost"


class RegistryClient:
    def __init__(
        self,
        catalog_url: str,
        name: str,
        port: int,
        host: Optional[str] = None,
        heartbeat_interval: float = DEFAULT_HEARTBEAT_INTERVAL,
        health_path: str = DEFAULT_HEALTH_PATH,
        metadata: Optional[Dict[str, str]] = None,
        handle_signals: bool = False,
    ):
        self._catalog_url = catalog_url.rstrip("/")
        self._name = name
        self._host = host or _detect_host()
        self._port = port
        self._heartbeat_interval = heartbeat_interval
        self._health_path = health_path
        self._metadata = metadata or {}

        self._http = httpx.Client(timeout=DEFAULT_TIMEOUT)
        self._instance_id: Optional[str] = None
        self._stop_event = threading.Event()
        self._thread: Optional[threading.Thread] = None

        atexit.register(self.stop)
        if handle_signals:
            signal.signal(signal.SIGTERM, self._signal_handler)
            signal.signal(signal.SIGINT, self._signal_handler)

    @property
    def instance_id(self) -> Optional[str]:
        return self._instance_id

    def start(self) -> None:
        self._register()
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._heartbeat_loop, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread is not None:
            self._thread.join(timeout=self._heartbeat_interval)
            self._thread = None
        self._deregister()

    def __enter__(self) -> "RegistryClient":
        self.start()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.stop()

    def _signal_handler(self, signum, frame) -> None:
        self.stop()

    def _heartbeat_loop(self) -> None:
        while not self._stop_event.wait(self._heartbeat_interval):
            if self._instance_id is None:
                self._register()
            else:
                self._heartbeat()

    def _register(self) -> None:
        payload = {
            "name": self._name,
            "host": self._host,
            "port": self._port,
            "healthUrl": f"http://{self._host}:{self._port}{self._health_path}",
            "metadata": self._metadata,
        }
        try:
            response = self._http.post(f"{self._catalog_url}/register", json=payload)
            response.raise_for_status()
            self._instance_id = response.json()["id"]
            logger.info("Registered %s with catalog as instance %s", self._name, self._instance_id)
        except httpx.HTTPError as exc:
            logger.warning(
                "Could not register %s with catalog at %s (%s); will retry on next heartbeat tick",
                self._name, self._catalog_url, exc,
            )

    def _heartbeat(self) -> None:
        instance_id = self._instance_id
        try:
            response = self._http.put(f"{self._catalog_url}/heartbeat/{instance_id}")
            response.raise_for_status()
        except httpx.HTTPError as exc:
            logger.warning(
                "Heartbeat failed for instance %s (%s); will re-register on next tick",
                instance_id, exc,
            )
            self._instance_id = None

    def _deregister(self) -> None:
        instance_id = self._instance_id
        if instance_id is None:
            return
        try:
            response = self._http.delete(f"{self._catalog_url}/deregister/{instance_id}")
            response.raise_for_status()
            logger.info("Deregistered instance %s", instance_id)
        except httpx.HTTPError as exc:
            logger.warning(
                "Deregister failed for instance %s (%s); it will be evicted by the catalog's lease TTL",
                instance_id, exc,
            )
        finally:
            self._instance_id = None
