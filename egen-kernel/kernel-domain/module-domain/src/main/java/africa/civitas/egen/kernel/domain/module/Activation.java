package africa.civitas.egen.kernel.domain.module;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;

/**
 * Ce qu'une Cellule precise a choisi d'allumer, parmi ce que son Organisation a
 * souscrit (§B.2). {@code celluleId} reference une Cellule par simple UUID — meme
 * discipline que {@link Souscription#organisationId()}, et pour la meme raison :
 * module-registry (Niveau 0) ignore tout du module business Organization (Niveau 2).
 *
 * <p><b>Consequence assumee</b> : ce domaine ne verifie jamais lui-meme que la Cellule
 * activee appartient bien a une Organisation ayant une Souscription active pour ce
 * module — cette verification croise a lieu au niveau service ({@code
 * ActivationService}, qui recoit explicitement les deux identifiants), jamais ici. Le
 * domaine ne fait que porter la donnee, valide dans sa propre forme.
 *
 * <p>Une desactivation est toujours une suppression logique, jamais physique.
 */
public record Activation(
        UUID id,
        UUID celluleId,
        ModuleId moduleId,
        Tracabilite tracabilite) {

    public Activation {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(celluleId, "celluleId ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    /** @return vrai si cette Activation est active (ni eteinte, ni jamais supprimee logiquement). */
    public boolean active() {
        return !tracabilite.estSupprimee();
    }
}
