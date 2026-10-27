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
from .errors import CallFailedError, CircuitOpenError, EgenError, NoInstanceError, PublishRejectedError
from .executor import Outcome, ResilienceExecutor
from .policy import ResiliencyPolicy

CORRELATION_HEADER = "Egen-Correlation-Id"
TRACEPARENT_HEADER = "traceparent"
IDEMPOTENCY_HEADER = "Idempotency-Key"
# Nom de la "cible" de la publication dans les erreurs (le Kernel n'est pas un service resolu).
_PUBLISH_TARGET = "events.publish"


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
class PublishReceipt:
    """Accuse du Kernel apres ``202 Accepted`` sur ``POST /api/v1/events/publish`` (docs/architecture/13)."""

    id: str
    type: str
    correlation_id: str


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
        event_source: Optional[str] = None,
        publish_policy: Optional[ResiliencyPolicy] = None,
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
        # Source par defaut des evenements publies ; politique de la PUBLICATION (docs/architecture/12) : par
        # defaut celle du contrat partage — un seul essai, donc retry opt-in ; la cle d'idempotence etant
        # stable, un retry ne cree jamais de doublon. Circuit DEDIE, independant de ceux des services cibles.
        self._event_source = event_source
        self._publish_policy = publish_policy if publish_policy is not None else ResiliencyPolicy.defaults()
        self._publish_breaker = CircuitBreaker(self._publish_policy.circuit_breaker, clock_millis)

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


    # ------------------------------------------------------------------ publication

    def publish(self, type: str, data, *, source: Optional[str] = None, correlation_id: Optional[str] = None,
                causation_id: Optional[str] = None, traceparent: Optional[str] = None,
                idempotency_key: Optional[str] = None) -> PublishReceipt:
        """Publie un evenement via ``POST /api/v1/events/publish`` (docs/architecture/12 et 13).

        Politique de publication du client : meme classification, meme retry et meme circuit que ``call``,
        circuit dedie, UNE cle d'idempotence pour toutes les tentatives, propagation de ``traceparent`` et
        ``Egen-Correlation-Id``. ``data`` (toute valeur JSON-serialisable) n'est jamais transforme.

        Renvoie le recu du Kernel (``202``). Leve ``ValueError`` (type, source ou data absents ou invalides —
        rien n'est envoye), ``PublishRejectedError`` (le Kernel a repondu sans publier : 4xx definitif, ou 5xx
        apres les tentatives), ``CircuitOpenError`` (aucun acces reseau) ou ``CallFailedError`` (aucune
        tentative n'a obtenu de reponse).
        """
        if not type or not type.strip():
            raise ValueError("type obligatoire")
        source = source if source is not None else self._event_source
        if not source or not source.strip():
            raise ValueError("source obligatoire : event_source=... sur le client, ou source=... a l'appel")
        try:
            data_json = json.dumps(data, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        except (TypeError, ValueError) as e:
            raise ValueError(f"data doit etre du JSON valide : {e}") from e

        traceparent = traceparent or new_traceparent()
        correlation_id = correlation_id or str(uuid.uuid4())
        idempotency_key = idempotency_key or str(uuid.uuid4())
        envelope = {"specversion": "1.0", "type": type, "source": source, "egen_correlation_id": correlation_id}
        if causation_id and causation_id.strip():
            envelope["egen_causation_id"] = causation_id
        # data est insere tel quel : l'enveloppe est serialisee sans data, puis data est ajoute en dernier.
        body = (json.dumps(envelope, ensure_ascii=False, separators=(",", ":"))[:-1]
                + ',"data":' + data_json + "}").encode("utf-8")

        result = ResilienceExecutor(self._publish_policy.retry, self._publish_breaker, self._sleeper).run(
            lambda attempt: self._send_publish(body, traceparent, correlation_id, idempotency_key))
        if result.rejected:
            raise CircuitOpenError(_PUBLISH_TARGET)
        response = result.last.response
        if response is None:
            raise CallFailedError(_PUBLISH_TARGET, result.last.error, result.attempts_made)
        if 200 <= response.status < 300:
            return _parse_receipt(response)
        raise PublishRejectedError(response.status, response.text[:500], result.attempts_made)

    def _send_publish(self, body: bytes, traceparent: str, correlation_id: str, idempotency_key: str) -> Outcome:
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            TRACEPARENT_HEADER: traceparent,
            CORRELATION_HEADER: correlation_id,
            IDEMPOTENCY_HEADER: idempotency_key,  # la MEME cle a chaque tentative
        }
        if self._token_supplier is not None:
            token = self._token_supplier()
            if token and token.strip():
                headers["Authorization"] = f"Bearer {token}"
        conn = self._connection(self._cp.scheme, self._cp.hostname, self._cp.port,
                                self._publish_policy.timeout_millis / 1000.0)
        try:
            conn.request("POST", f"{self._cp.path}/api/v1/events/publish", body=body, headers=headers)
            response = conn.getresponse()
            payload = response.read()
            return Outcome(status=response.status, response=EgenResponse(
                response.status, payload, {k.lower(): v for k, v in response.getheaders()}))
        except (socket.timeout, TimeoutError):
            return Outcome(error="timeout")
        except (OSError, http.client.HTTPException):
            return Outcome(error="network")
        finally:
            conn.close()


def _parse_receipt(response: EgenResponse) -> PublishReceipt:
    try:
        root = json.loads(response.text)
    except ValueError as e:
        raise EgenError(f"Accuse de publication illisible : {e}") from e
    if not isinstance(root, dict):
        raise EgenError("Accuse de publication illisible : objet JSON attendu")
    return PublishReceipt(str(root.get("id")), str(root.get("type")), str(root.get("egen_correlation_id")))


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
