package africa.civitas.egen.kernel.organization.api.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Modele de lecture d'une entree de Fermeture Transitive de Cellule (A2.5) —
 * entierement derivee, recalculee automatiquement a chaque changement de l'arbre.
 * Contrairement a toute autre entite du Kernel, elle ne porte pas de Socle de
 * Traçabilite : elle n'a ni Cree par ni Modifie par humains, puisqu'aucun humain ne
 * la manipule jamais directement.
 *
 * @param celluleAncetreId la Cellule ancetre (ou la Cellule elle-meme si profondeur == 0)
 * @param celluleDescendanteId la Cellule descendante
 * @param profondeur nombre de niveaux separant les deux ; 0 si les deux identifiants sont identiques
 */
public record FermetureTransitiveCellule(
        UUID celluleAncetreId,
        UUID celluleDescendanteId,
        int profondeur) {

    public FermetureTransitiveCellule {
        Objects.requireNonNull(celluleAncetreId, "celluleAncetreId ne peut pas etre nul.");
        Objects.requireNonNull(celluleDescendanteId, "celluleDescendanteId ne peut pas etre nul.");
        if (profondeur < 0) {
            throw new IllegalArgumentException("profondeur ne peut pas etre negative, recu : " + profondeur);
        }
        if (profondeur == 0 && !celluleAncetreId.equals(celluleDescendanteId)) {
            throw new IllegalArgumentException(
                    "Une profondeur de 0 implique que celluleAncetreId == celluleDescendanteId.");
        }
        if (profondeur > 0 && celluleAncetreId.equals(celluleDescendanteId)) {
            throw new IllegalArgumentException(
                    "Une Cellule ne peut pas etre son propre ancetre a une profondeur superieure a 0.");
        }
    }
}
