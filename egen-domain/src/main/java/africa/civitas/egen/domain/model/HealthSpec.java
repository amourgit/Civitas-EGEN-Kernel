package africa.civitas.egen.domain.model;

import java.time.Duration;

/**
 * Politique de health check HTTP d'un service (voir
 * docs/architecture/06-service-manifest.md, section "health.http").
 * Necessaire des la Phase 2 : sans check, un enregistrement Consul ne
 * distingue jamais une instance saine d'une instance morte — voir
 * docs/architecture/07-ports-et-adapters.md, "Discovery Port". Le bloc
 * "readiness" separe du schema complet rejoint cette classe quand un besoin
 * reel le justifie (voir docs/architecture/19-feuille-de-route.md), jamais
 * par anticipation.
 */
public record HealthSpec(String httpEndpoint, Duration interval, Duration timeout,
                          int failuresBeforeUnhealthy) {

    public HealthSpec {
        if (httpEndpoint == null || httpEndpoint.isBlank()) {
            throw new IllegalArgumentException("HealthSpec.httpEndpoint ne peut pas etre vide");
        }
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("HealthSpec.interval doit etre strictement positif");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("HealthSpec.timeout doit etre strictement positif");
        }
        if (failuresBeforeUnhealthy < 1) {
            throw new IllegalArgumentException("HealthSpec.failuresBeforeUnhealthy doit etre >= 1");
        }
    }
}
