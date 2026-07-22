package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'un Type de Cellule Modele (B4.7) — la version gabarit d'un Type
 * de Cellule, rattachee a un Modele Sectoriel, copiee vers le Lexique Organisationnel
 * d'une Organisation (A2.3) au moment ou celle-ci adopte le Modele Sectoriel.
 *
 * @param modeleSectorialId le Modele Sectoriel parent
 * @param typeParentSuggereId le Type de Cellule Modele suggere comme parent dans la
 *                             hierarchie, absent si ce type est suggere comme racine
 *                             (Etablissement)
 */
public record TypeCelluleModele(
        UUID id,
        UUID modeleSectorialId,
        String libelleMetierSuggere,
        int niveauIndicatifSuggere,
        UUID typeParentSuggereId,
        Tracabilite tracabilite) {

    public TypeCelluleModele {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(modeleSectorialId, "modeleSectorialId ne peut pas etre nul.");
        if (libelleMetierSuggere == null || libelleMetierSuggere.isBlank()) {
            throw new IllegalArgumentException("libelleMetierSuggere ne peut pas etre vide.");
        }
        if (niveauIndicatifSuggere < 0) {
            throw new IllegalArgumentException(
                    "niveauIndicatifSuggere ne peut pas etre negatif, recu : " + niveauIndicatifSuggere);
        }
        if (typeParentSuggereId != null && typeParentSuggereId.equals(id)) {
            throw new IllegalArgumentException("Un Type de Cellule Modele ne peut pas etre son propre parent.");
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    /** @return le parent suggere, absent si ce type est suggere comme racine. */
    public Optional<UUID> typeParentSuggere() {
        return Optional.ofNullable(typeParentSuggereId);
    }
}
