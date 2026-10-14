"""Retry + circuit breaker autour d'une fonction d'envoi — semantique NORMATIVE de docs/architecture/12.

Rejouee par ``sdk/contract/resiliency-contract.json``. Le timeout par tentative est applique par la
fonction d'envoi (elle seule parle au reseau) ; ici on ne voit que son resultat.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, List, Optional

from .breaker import CircuitBreaker, OPEN
from .policy import MAX_BACKOFF_MILLIS, Retry


@dataclass(frozen=True)
class Outcome:
    """Resultat d'UNE tentative : une reponse HTTP (``status``) ou une erreur sans reponse
    (``error`` = "timeout" | "network"). ``response`` porte la reponse complete quand elle existe."""

    status: Optional[int] = None
    error: Optional[str] = None
    response: Any = None

    @property
    def is_retryable_failure(self) -> bool:
        """Echec reessayable : 5xx, 408, 429, delai depasse, erreur reseau (docs/architecture/12)."""
        return self.status is None or self.status >= 500 or self.status in (408, 429)

    def render(self) -> str:
        return f"status:{self.status}" if self.status is not None else f"error:{self.error}"


@dataclass
class Result:
    last: Optional[Outcome]
    rejected: bool
    attempts_made: int
    sleeps_millis: List[int] = field(default_factory=list)

    def render(self) -> str:
        return "rejected:circuit_open" if self.rejected else self.last.render()


def backoff_millis(retry: Retry, failed_attempts: int) -> int:
    """Attente apres la n-ieme tentative echouee : fixed = base ; exponential = base x 2^(n-1), plafonnee."""
    if retry.backoff == "fixed":
        delay = retry.base_delay_millis
    else:
        delay = retry.base_delay_millis * (1 << min(failed_attempts - 1, 20))
    return min(delay, MAX_BACKOFF_MILLIS)


class ResilienceExecutor:
    def __init__(self, retry: Retry, breaker: CircuitBreaker, sleeper: Callable[[int], None]):
        self._retry = retry
        self._breaker = breaker
        self._sleeper = sleeper

    def run(self, send: Callable[[int], Outcome]) -> Result:
        """``send`` recoit l'indice (0, 1, ...) de la tentative et la realise."""
        sleeps: List[int] = []
        last: Optional[Outcome] = None
        attempts = 0
        while True:
            if not self._breaker.try_acquire():
                # Rejete d'emblee, ou circuit ouvert par un autre appelant entre deux tentatives.
                return Result(last, last is None, attempts, sleeps)
            outcome = send(attempts)
            attempts += 1
            last = outcome
            if outcome.is_retryable_failure:
                self._breaker.record_failure()
            else:
                self._breaker.record_success()  # succes, ou reponse definitive 4xx : le service a repondu
            if (not outcome.is_retryable_failure
                    or attempts >= self._retry.max_attempts
                    or self._breaker.state == OPEN):
                return Result(last, False, attempts, sleeps)
            delay = backoff_millis(self._retry, attempts)
            sleeps.append(delay)
            self._sleeper(delay)
