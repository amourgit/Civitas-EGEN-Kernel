"""Client de la Communication Fabric (docs/architecture/12).

Resout un service aupres de l'API de controle EGEN, puis l'appelle en appliquant la politique de
resilience de la CIBLE (retry, timeout, circuit breaker) et en propageant le contexte
(``traceparent``, ``Egen-Correlation-Id``).

Ce que ce client ne fait jamais : transformer, enrichir ou router selon le contenu d'un corps, ni
retenter une reponse definitive (4xx hors 408/429). Thread-safe ; une instance par processus suffit.
"""
from __future__ import annotations

import http.client
import json
import secrets
import socket
import threading
import time
import urllib.parse
import uuid
from dataclasses import dataclass, field
from typing import Callable, Dict, List, Mapping, Optional

from .breaker import CircuitBreaker
from .errors import CallFailedError, CircuitOpenError, EgenError, NoInstanceError
from .executor import Outcome, ResilienceExecutor
from .policy import ResiliencyPolicy

CORRELATION_HEADER = "Egen-Correlation-Id"
TRACEPARENT_HEADER = "traceparent"


@dataclass(frozen=True)
class ServiceInstance:
    instance_id: str
    address: str
    port: int


@dataclass(frozen=True)
class ServiceResolution:
    service_id: str
    instances: List[ServiceInstance]
    resiliency: ResiliencyPolicy


@dataclass(frozen=True)
class EgenResponse:
    status: int
    body: bytes
    headers: Mapping[str, str]

    @property
    def text(self) -> str:
        return self.body.decode("utf-8", errors="replace")

    @property
    def is_success(self) -> bool:
        return 200 <= self.status < 300


@dataclass(frozen=True)
class EgenRequest:
    """Requete vers un service cible. ``traceparent`` et ``correlation_id`` sont generes par le SDK
    s'ils sont absents, propages tels quels sinon (docs/architecture/12, "Propagation de contexte")."""

    method: str
    path: str
    body: Optional[bytes] = None
    headers: Mapping[str, str] = field(default_factory=dict)
    traceparent: Optional[str] = None
    correlation_id: Optional[str] = None

    def __post_init__(self):
        if not self.method or not self.method.strip():
            raise ValueError("method obligatoire")
        if not self.path or not self.path.startswith("/"):
            raise ValueError(f"path doit commencer par '/' (recu : {self.path!r})")
        object.__setattr__(self, "method", self.method.upper())

    @staticmethod
    def get(path: str) -> "EgenRequest":
        return EgenRequest("GET", path)

    @staticmethod
    def delete(path: str) -> "EgenRequest":
        return EgenRequest("DELETE", path)

    @staticmethod
    def post(path: str, body: "bytes | str | None" = None) -> "EgenRequest":
        return EgenRequest("POST", path, _as_bytes(body))

    @staticmethod
    def put(path: str, body: "bytes | str | None" = None) -> "EgenRequest":
        return EgenRequest("PUT", path, _as_bytes(body))

    def with_header(self, name: str, value: str) -> "EgenRequest":
        return EgenRequest(self.method, self.path, self.body, {**self.headers, name: value},
                           self.traceparent, self.correlation_id)

    def with_traceparent(self, value: str) -> "EgenRequest":
        return EgenRequest(self.method, self.path, self.body, self.headers, value, self.correlation_id)

    def with_correlation_id(self, value: str) -> "EgenRequest":
        return EgenRequest(self.method, self.path, self.body, self.headers, self.traceparent, value)


def _as_bytes(body):
    return body.encode("utf-8") if isinstance(body, str) else body


class EgenClient:
    def __init__(
        self,
        control_plane_url: str,
        token_supplier: Optional[Callable[[], Optional[str]]] = None,
        ssl_context=None,
        resolve_cache_ttl: float = 5.0,
        connect_timeout: float = 5.0,
        control_plane_timeout: float = 5.0,
        clock_millis: Callable[[], int] = lambda: int(time.time() * 1000),
        sleeper: Callable[[int], None] = lambda millis: time.sleep(millis / 1000.0),
    ):
        if not control_plane_url or not control_plane_url.strip():
            raise ValueError("control_plane_url obligatoire")
        parsed = urllib.parse.urlparse(control_plane_url.rstrip("/"))
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            raise ValueError(f"control_plane_url invalide : {control_plane_url!r}")
        self._cp = parsed
        self._token_supplier = token_supplier
        self._ssl_context = ssl_context
        self._ttl_millis = int(resolve_cache_ttl * 1000)
        self._connect_timeout = connect_timeout
        self._cp_timeout = control_plane_timeout
        self._clock = clock_millis
        self._sleeper = sleeper
        # Avec un ssl.SSLContext (mTLS), les services cibles sont adresses en https ; sinon en http.
        self.target_scheme = "https" if ssl_context is not None else "http"
        self._lock = threading.Lock()
        self._resolutions: Dict[str, "tuple[ServiceResolution, int]"] = {}
        self._breakers: Dict[str, CircuitBreaker] = {}
        self._round_robin: Dict[str, int] = {}

    # ------------------------------------------------------------------ resolution

    def resolve(self, service: str) -> ServiceResolution:
        """Instances saines et politique de la cible, gardees en cache brievement (5 s par defaut) pour ne
        pas faire du Kernel un intermediaire de chaque appel. Un resultat SANS instance n'est jamais cache."""
        now = self._clock()
        with self._lock:
            cached = self._resolutions.get(service)
        if cached is not None and now < cached[1]:
            return cached[0]
        fresh = self._fetch_resolution(service)
        with self._lock:
            if fresh.instances:
                self._resolutions[service] = (fresh, now + self._ttl_millis)
            else:
                self._resolutions.pop(service, None)
        return fresh

    def _fetch_resolution(self, service: str) -> ServiceResolution:
        path = f"{self._cp.path}/api/v1/discover/{urllib.parse.quote(service, safe='')}"
        headers = {"Accept": "application/json"}
        if self._token_supplier is not None:
            token = self._token_supplier()
            if token and token.strip():
                headers["Authorization"] = f"Bearer {token}"
        conn = self._connection(self._cp.scheme, self._cp.hostname, self._cp.port, self._cp_timeout)
        try:
            conn.request("GET", path, headers=headers)
            response = conn.getresponse()
            body = response.read()
            status = response.status
        except (OSError, http.client.HTTPException) as e:
            raise EgenError(f"API de controle EGEN injoignable ({self._cp.geturl()}) : {e}") from e
        finally:
            conn.close()
        if status != 200:
            raise EgenError(f"API de controle EGEN : HTTP {status} pour la resolution de {service}")
        return parse_resolution(service, body.decode("utf-8", errors="replace"))

    def _connection(self, scheme: str, host: str, port: Optional[int], timeout: float):
        if scheme == "https":
            return http.client.HTTPSConnection(host, port, timeout=timeout, context=self._ssl_context)
        return http.client.HTTPConnection(host, port, timeout=timeout)

    # ------------------------------------------------------------------ appel

    def call(self, service: str, request: EgenRequest) -> EgenResponse:
        """Appelle ``service`` avec la politique de resilience de la cible.

        Renvoie la reponse du service, telle quelle — y compris un 4xx, ou un 5xx une fois les tentatives
        epuisees. Leve ``NoInstanceError`` (aucune instance saine), ``CircuitOpenError`` (circuit ouvert,
        aucun acces reseau) ou ``CallFailedError`` (aucune tentative n'a obtenu de reponse).
        """
        resolution = self.resolve(service)
        instances = resolution.instances
        if not instances:
            raise NoInstanceError(service)
        policy = resolution.resiliency
        with self._lock:
            breaker = self._breakers.get(service)
            if breaker is None or breaker.spec != policy.circuit_breaker:
                breaker = CircuitBreaker(policy.circuit_breaker, self._clock)
                self._breakers[service] = breaker
            start = self._round_robin.get(service, 0)
            self._round_robin[service] = start + 1

        traceparent = request.traceparent or new_traceparent()
        correlation_id = request.correlation_id or str(uuid.uuid4())

        def send(attempt: int) -> Outcome:
            # Chaque tentative vise l'instance SUIVANTE : retenter une instance defaillante n'a guere de sens.
            target = instances[(start + attempt) % len(instances)]
            return self._send(target, request, policy, traceparent, correlation_id)

        result = ResilienceExecutor(policy.retry, breaker, self._sleeper).run(send)
        if result.rejected:
            raise CircuitOpenError(service)
        if result.last.response is not None:
            return result.last.response
        raise CallFailedError(service, result.last.error, result.attempts_made)

    def _send(self, target: ServiceInstance, request: EgenRequest, policy: ResiliencyPolicy,
              traceparent: str, correlation_id: str) -> Outcome:
        headers = dict(request.headers)
        headers[TRACEPARENT_HEADER] = traceparent
        headers[CORRELATION_HEADER] = correlation_id
        timeout = policy.timeout_millis / 1000.0
        conn = self._connection(self.target_scheme, target.address, target.port, timeout)
        try:
            conn.request(request.method, request.path, body=request.body, headers=headers)
            response = conn.getresponse()
            body = response.read()
            return Outcome(status=response.status, response=EgenResponse(
                response.status, body, {k.lower(): v for k, v in response.getheaders()}))
        except (socket.timeout, TimeoutError):
            return Outcome(error="timeout")
        except (OSError, http.client.HTTPException):
            return Outcome(error="network")
        finally:
            conn.close()


def parse_resolution(service: str, text: str) -> ServiceResolution:
    try:
        root = json.loads(text)
    except ValueError as e:
        raise EgenError(f"Reponse de resolution illisible pour {service} : {e}") from e
    if not isinstance(root, dict):
        raise EgenError(f"Reponse de resolution illisible pour {service} : objet JSON attendu")
    instances = [
        ServiceInstance(str(i.get("instanceId")), str(i.get("address")), int(i.get("port")))
        for i in root.get("instances", []) if isinstance(i, dict)
    ]
    return ServiceResolution(str(root.get("serviceId", service)), instances,
                             ResiliencyPolicy.from_json(root.get("resiliency")))


def new_traceparent() -> str:
    """W3C Trace Context : ``00-<trace-id 32 hex>-<parent-id 16 hex>-01``."""
    return f"00-{secrets.token_hex(16)}-{secrets.token_hex(8)}-01"
