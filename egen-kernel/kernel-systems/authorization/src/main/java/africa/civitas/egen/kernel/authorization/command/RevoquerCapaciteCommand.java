package africa.civitas.egen.kernel.authorization.command;

import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;

import java.util.Objects;
import java.util.UUID;

/**
 * Demande de revocation d'un octroi existant — toujours une suppression logique
 * (Tracabilite#avecSuppressionLogique), jamais une suppression physique : l'octroi
 * reste consultable dans un audit, marque revoque, pour toujours.
 *
 * @param octroiId l'octroi a revoquer
 * @param sujetDemandeur doit lui-meme etre le sujet bootstrap ou deja detenir {@link
 *                       africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability#ADMINISTRER_CAPACITES_NOYAU}
 * @param motif obligatoire — toute modification posterieure a la creation l'exige (Tracabilite)
 */
public record RevoquerCapaciteCommand(UUID octroiId, KernelSubject sujetDemandeur, String motif) {

    public RevoquerCapaciteCommand {
        Objects.requireNonNull(octroiId, "octroiId ne peut pas etre nul.");
        Objects.requireNonNull(sujetDemandeur, "sujetDemandeur ne peut pas etre nul.");
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("motif ne peut pas etre vide.");
        }
    }
}
