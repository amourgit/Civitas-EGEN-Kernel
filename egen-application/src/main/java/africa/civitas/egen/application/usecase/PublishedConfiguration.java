package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

/** Resultat d'une publication : la version du store apres ecriture (monotone par couple service/environnement). */
public record PublishedConfiguration(ServiceId serviceId, TargetEnvironment environment, long version) {
}
