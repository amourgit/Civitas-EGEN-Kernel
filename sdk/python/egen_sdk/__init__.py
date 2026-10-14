"""SDK leger de la Communication Fabric EGEN (voir docs/architecture/12-communication-fabric.md).

Bibliotheque standard uniquement — aucune dependance d'execution.
"""
from .client import (CORRELATION_HEADER, TRACEPARENT_HEADER, EgenClient, EgenRequest, EgenResponse,
                     ServiceInstance, ServiceResolution)
from .errors import CallFailedError, CircuitOpenError, EgenError, NoInstanceError
from .policy import CircuitBreakerSpec, ResiliencyPolicy, Retry

__all__ = [
    "EgenClient", "EgenRequest", "EgenResponse", "ServiceInstance", "ServiceResolution",
    "ResiliencyPolicy", "Retry", "CircuitBreakerSpec",
    "EgenError", "CircuitOpenError", "NoInstanceError", "CallFailedError",
    "CORRELATION_HEADER", "TRACEPARENT_HEADER",
]
