package africa.civitas.egen.kernel.moduleregistry.command;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.UUID;

/**
 * Demande de Souscription d'un Contexte a un module du Catalogue (§B.11).
 *
 * @param contexteId reference nue vers un Contexte (dont la nature reelle est
 *                    propriete d'un module Niveau 2) — voir la note de
 *                    {@link africa.civitas.egen.kernel.domain.module.Souscription}
 */
public record SouscrireModuleCommand(
        UUID contexteId,
        ModuleId moduleId,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public SouscrireModuleCommand {
        Objects.requireNonNull(contexteId, "contexteId ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
