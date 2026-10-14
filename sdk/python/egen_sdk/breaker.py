"""Circuit breaker d'UN service cible — machine a etats normative de docs/architecture/12.

Identique a celle du Kernel (egen-application.resilience) et du SDK Java : tous trois rejouent
``sdk/contract/resiliency-contract.json``.

Protocole : ``try_acquire()`` avant l'appel ; si ``True``, exactement un ``record_success()`` ou
``record_failure()`` apres. Un rejet ne compte pas comme un echec et ne prolonge pas la fenetre
d'ouverture.
"""
from __future__ import annotations

import threading
from typing import Callable

from .policy import CircuitBreakerSpec

CLOSED = "CLOSED"
OPEN = "OPEN"
HALF_OPEN = "HALF_OPEN"


class CircuitBreaker:
    def __init__(self, spec: CircuitBreakerSpec, clock_millis: Callable[[], int]):
        self.spec = spec
        self._clock = clock_millis
        self._lock = threading.Lock()
        self._state = CLOSED
        self._consecutive_failures = 0
        self._opened_at = 0
        self._probe_in_flight = False

    def try_acquire(self) -> bool:
        """``False`` : l'appel doit etre rejete SANS aucun acces reseau."""
        with self._lock:
            if self._state == CLOSED:
                return True
            if self._state == OPEN:
                if self._clock() - self._opened_at >= self.spec.open_duration_millis:
                    self._state = HALF_OPEN
                    self._probe_in_flight = True
                    return True
                return False
            # HALF_OPEN : un seul essai a la fois
            if self._probe_in_flight:
                return False
            self._probe_in_flight = True
            return True

    def record_success(self) -> None:
        with self._lock:
            if self._state == OPEN:
                return
            self._consecutive_failures = 0
            self._probe_in_flight = False
            self._state = CLOSED

    def record_failure(self) -> None:
        with self._lock:
            if self._state == OPEN:
                return
            self._consecutive_failures += 1
            if self._state == HALF_OPEN or self._consecutive_failures >= self.spec.failure_threshold:
                self._state = OPEN
                self._opened_at = self._clock()
                self._probe_in_flight = False

    @property
    def state(self) -> str:
        """Etat interne courant — l'ouverture n'expire qu'au prochain ``try_acquire()``."""
        with self._lock:
            return self._state
