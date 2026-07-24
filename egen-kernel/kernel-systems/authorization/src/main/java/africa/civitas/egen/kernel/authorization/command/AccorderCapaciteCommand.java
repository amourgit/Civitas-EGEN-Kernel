package africa.civitas.egen.kernel.authorization.command;

import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.UUID;

/**
 * Demande d'octroi d'une capacite noyau a un sujet.
 *
 * @param sujetBeneficiaireId identifiant du {@link KernelSubject} qui recevra la capacite,
 *                            jamais le sujet bootstrap (voir {@link
 *                            africa.civitas.egen.kernel.authorization.domain.KernelCapabiliteOctroi})
 * @param capacite la capacite accordee
 * @param sujetDemandeur le {@link KernelSubject} a l'origine de la demande — doit
 *                       lui-meme etre le sujet bootstrap ou deja detenir {@link
 *                       KernelCapability#ADMINISTRER_CAPACITES_NOYAU}, sans quoi le
 *                       service refuse la demande avant meme de la traiter
 * @param origineDonnee provenance de la donnee, pour le Socle de Traçabilite
 */
public record AccorderCapaciteCommand(
        UUID sujetBeneficiaireId,
        KernelCapability capacite,
        KernelSubject sujetDemandeur,
        OrigineDonnee origineDonnee) {

    public AccorderCapaciteCommand {
        Objects.requireNonNull(sujetBeneficiaireId, "sujetBeneficiaireId ne peut pas etre nul.");
        Objects.requireNonNull(capacite, "capacite ne peut pas etre nulle.");
        Objects.requireNonNull(sujetDemandeur, "sujetDemandeur ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
