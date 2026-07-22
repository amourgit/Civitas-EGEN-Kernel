package africa.civitas.egen.modules.business.organization.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'un Type de Cellule (A2.3) — une entree du Lexique
 * Organisationnel, le vocabulaire metier qu'une Organisation donne a un niveau de sa
 * hierarchie ("Faculte", "Agence", "Cycle"...).
 *
 * @param typesParentsAutorisesIds Types de Cellule autorises comme parent direct dans
 *                                  l'arbre ; liste vide = aucune contrainte de rattachement
 */
public record TypeCellule(
        UUID id,
        UUID lexiqueId,
        String libelle,
        String description,
        int niveauIndicatif,
        List<UUID> typesParentsAutorisesIds,
        UUID typeCelluleModeleOrigineId,
        StatutTypeCellule statut,
        Tracabilite tracabilite) {

    public TypeCellule {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(lexiqueId, "lexiqueId ne peut pas etre nul.");
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("libelle ne peut pas etre vide.");
        }
        if (niveauIndicatif < 0) {
            throw new IllegalArgumentException(
                    "niveauIndicatif ne peut pas etre negatif, recu : " + niveauIndicatif);
        }
        typesParentsAutorisesIds = typesParentsAutorisesIds == null
                ? List.of() : List.copyOf(typesParentsAutorisesIds);
        if (typesParentsAutorisesIds.contains(id)) {
            throw new IllegalArgumentException("Un Type de Cellule ne peut pas s'autoriser lui-meme comme parent.");
        }
        Objects.requireNonNull(statut, "statut ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    public Optional<UUID> typeCelluleModeleOrigine() {
        return Optional.ofNullable(typeCelluleModeleOrigineId);
    }

    /** @return vrai si aucune contrainte de rattachement n'est posee (tout parent est accepte). */
    public boolean sansContrainteDeParent() {
        return typesParentsAutorisesIds.isEmpty();
    }
}
