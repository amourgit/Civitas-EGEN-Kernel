package africa.civitas.egen.kernel.moduleregistry.command;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.UUID;

/**
 * Demande d'Activation d'un module par une Cellule precise, parmi ce que son
 * Organisation a Souscrit (§B.11).
 *
 * @param organisationId l'Organisation dont depend {@code celluleId} — fourni
 *                        explicitement par l'appelant, jamais resolu par ce
 *                        module-registry lui-meme : Niveau 0 ne connait pas la
 *                        hierarchie des Cellules, propriete du module business
 *                        Organization (Niveau 2). Sert uniquement a verifier la
 *                        Souscription ; n'est jamais stocke sur l'Activation
 *                        elle-meme (qui ne porte que {@code celluleId}).
 * @param celluleId la Cellule qui active le module
 */
public record ActiverModuleCommand(
        UUID organisationId,
        UUID celluleId,
        ModuleId moduleId,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public ActiverModuleCommand {
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        Objects.requireNonNull(celluleId, "celluleId ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
