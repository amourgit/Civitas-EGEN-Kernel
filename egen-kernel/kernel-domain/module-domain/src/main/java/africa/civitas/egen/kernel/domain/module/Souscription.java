package africa.civitas.egen.kernel.domain.module;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;

/**
 * Ce qu'une Organisation a choisi d'acquerir dans le Catalogue (§B.2). Toujours a
 * l'echelle de l'Organisation entiere, jamais d'une Cellule precise — voir {@link
 * Activation} pour le palier suivant.
 *
 * <p>{@code organisationId} reference l'identifiant d'une Organisation par un simple
 * UUID, jamais par le type {@code Contexte} ni par un import du module business
 * Organization : module-registry est Niveau 0, et ne doit jamais dependre d'un module
 * Niveau 2. La resolution "cette Organisation existe-t-elle reellement" est une
 * responsabilite du code appelant (kernel-bootstrap ou une future interface
 * d'administration), jamais de ce domaine.
 *
 * <p>Une revocation de Souscription est toujours une suppression logique, jamais
 * physique — voir {@link Tracabilite#estSupprimee()}.
 */
public record Souscription(
        UUID id,
        UUID organisationId,
        ModuleId moduleId,
        Tracabilite tracabilite) {

    public Souscription {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    /** @return vrai si cette Souscription est active (ni resiliee, ni jamais supprimee logiquement). */
    public boolean active() {
        return !tracabilite.estSupprimee();
    }
}
