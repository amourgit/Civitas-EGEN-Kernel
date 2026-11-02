package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

import java.util.Map;

/** La configuration publiee d'un service pour un environnement ; vide et de version 0 si jamais publiee. Jamais de secret. */
public record ServiceConfiguration(ServiceId serviceId, TargetEnvironment environment, long version,
                                    Map<String, String> values) {

    public ServiceConfiguration {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
