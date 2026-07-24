package africa.civitas.egen.kernel.authorization.domain;

import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;

/**
 * Modele de lecture d'un octroi de capacite noyau — la seule forme de "permission"
 * que le Kernel connaisse a son propre niveau (Niveau 1), bien avant que SpiceDB ne
 * gouverne les autorisations metier (Niveau 2). Voir {@code
 * africa.civitas.egen.kernel.sdk.permission.authorization.KernelPermissionCheck} pour
 * le contrat de verification qui consomme ces octrois.
 *
 * <p>Un octroi ne concerne jamais le sujet bootstrap ({@link KernelSubject#BOOTSTRAP_ID})
 * : celui-ci est toujours autorise pour toute capacite, par construction, sans jamais
 * avoir besoin — ni le droit d'avoir — un octroi explicite. Voir la validation du
 * constructeur canonique.
 *
 * @param sujetId identifiant du {@link KernelSubject} beneficiaire, jamais le sujet bootstrap
 * @param capacite la capacite noyau accordee
 * @param tracabilite l'octroi est revoque, jamais supprime physiquement — une
 *                     revocation se traduit par {@link Tracabilite#estSupprimee()}
 */
public record KernelCapabiliteOctroi(
        UUID id,
        UUID sujetId,
        KernelCapability capacite,
        Tracabilite tracabilite) {

    public KernelCapabiliteOctroi {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(sujetId, "sujetId ne peut pas etre nul.");
        if (sujetId.equals(KernelSubject.BOOTSTRAP_ID)) {
            throw new IllegalArgumentException(
                    "Le sujet bootstrap ne peut jamais faire l'objet d'un octroi explicite : "
                            + "il est autorise pour toute capacite noyau par construction.");
        }
        Objects.requireNonNull(capacite, "capacite ne peut pas etre nulle.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    /** @return vrai si cet octroi est actif (ni revoque, ni jamais supprime logiquement). */
    public boolean actif() {
        return !tracabilite.estSupprimee();
    }
}
