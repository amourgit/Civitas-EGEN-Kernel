"""Politique de resilience d'un service cible, telle que servie par ``GET /api/v1/discover/{service}``.

Valeurs EFFECTIVES en millisecondes ; les defauts sont ceux du doc 06 et sont verifies par
``sdk/contract/resiliency-contract.json``.
"""
from __future__ import annotations

from dataclasses import dataclass, field

MAX_BACKOFF_MILLIS = 30_000


@dataclass(frozen=True)
class Retry:
    max_attempts: int = 1
    backoff: str = "fixed"  # "fixed" | "exponential"
    base_delay_millis: int = 200

    def __post_init__(self):
        if self.max_attempts < 1:
            raise ValueError("retry.maxAttempts doit etre >= 1")
        if self.backoff not in ("fixed", "exponential"):
            raise ValueError(f"retry.backoff invalide : {self.backoff!r}")
        if self.base_delay_millis < 1:
            raise ValueError("retry.baseDelayMillis doit etre >= 1")


@dataclass(frozen=True)
class CircuitBreakerSpec:
    failure_threshold: int = 5
    open_duration_millis: int = 30_000

    def __post_init__(self):
        if self.failure_threshold < 1:
            raise ValueError("circuitBreaker.failureThreshold doit etre >= 1")
        if self.open_duration_millis < 1:
            raise ValueError("circuitBreaker.openDurationMillis doit etre >= 1")


@dataclass(frozen=True)
class ResiliencyPolicy:
    timeout_millis: int = 30_000
    retry: Retry = field(default_factory=Retry)
    circuit_breaker: CircuitBreakerSpec = field(default_factory=CircuitBreakerSpec)

    def __post_init__(self):
        if self.timeout_millis < 1:
            raise ValueError("timeoutMillis doit etre >= 1")

    @staticmethod
    def defaults() -> "ResiliencyPolicy":
        return ResiliencyPolicy()

    @staticmethod
    def from_json(node) -> "ResiliencyPolicy":
        """Champs absents => valeurs par defaut (noyau plus ancien, ou bloc partiel)."""
        if not isinstance(node, dict):
            return ResiliencyPolicy.defaults()
        d = ResiliencyPolicy.defaults()
        retry = node.get("retry") if isinstance(node.get("retry"), dict) else {}
        breaker = node.get("circuitBreaker") if isinstance(node.get("circuitBreaker"), dict) else {}
        return ResiliencyPolicy(
            timeout_millis=int(node.get("timeoutMillis", d.timeout_millis)),
            retry=Retry(
                max_attempts=int(retry.get("maxAttempts", d.retry.max_attempts)),
                backoff=str(retry.get("backoff", d.retry.backoff)).lower(),
                base_delay_millis=int(retry.get("baseDelayMillis", d.retry.base_delay_millis)),
            ),
            circuit_breaker=CircuitBreakerSpec(
                failure_threshold=int(breaker.get("failureThreshold", d.circuit_breaker.failure_threshold)),
                open_duration_millis=int(breaker.get("openDurationMillis", d.circuit_breaker.open_duration_millis)),
            ),
        )
