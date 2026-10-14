package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ServiceId;

/** Instances saines d'un service ET la politique de resilience que ses appelants lui appliquent. */
public record ServiceResolution(ServiceId serviceId, ResolvedInstances instances, ResiliencyPolicy resiliency) {
}
