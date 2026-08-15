package africa.civitas.egen.kernel.moduleregistry.command;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.UUID;

/**
 * Demande d'Activation d'un module par un Contexte precis, parmi ce que le Contexte
 * souscripteur dont il depend a Souscrit (§B.11).
 *
 * @param contexteSouscripteurId le Contexte dont depend {@code contexteCibleId} —
 *                        fourni explicitement par l'appelant, jamais resolu par ce
 *                        module-registry lui-meme : Niveau 0 ne connait jamais
 *                        comment plusieurs Contextes s'articulent entre eux, propriete
 *                        exclusive du module Niveau 2 qui modelise ce domaine. Sert
 *                        uniquement a verifier la Souscription ; n'est jamais stocke
 *                        sur l'Activation elle-meme (qui ne porte que
 *                        {@code contexteCibleId}).
 * @param contexteCibleId le Contexte qui active le module
 */
public record ActiverModuleCommand(
        UUID contexteSouscripteurId,
        UUID contexteCibleId,
        ModuleId moduleId,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public ActiverModuleCommand {
        Objects.requireNonNull(contexteSouscripteurId, "contexteSouscripteurId ne peut pas etre nul.");
        Objects.requireNonNull(contexteCibleId, "contexteCibleId ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
