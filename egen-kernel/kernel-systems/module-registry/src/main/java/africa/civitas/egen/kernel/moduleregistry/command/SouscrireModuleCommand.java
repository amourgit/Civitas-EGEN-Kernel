package africa.civitas.egen.kernel.moduleregistry.command;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.UUID;

/**
 * Demande de Souscription d'une Organisation a un module du Catalogue (§B.11).
 *
 * @param organisationId reference nue vers une Organisation (Niveau 2) — voir la
 *                        note de {@link africa.civitas.egen.kernel.domain.module.Souscription}
 */
public record SouscrireModuleCommand(
        UUID organisationId,
        ModuleId moduleId,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public SouscrireModuleCommand {
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
