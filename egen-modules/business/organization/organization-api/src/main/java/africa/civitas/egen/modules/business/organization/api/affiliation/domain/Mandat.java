package africa.civitas.egen.modules.business.organization.api.affiliation.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'un Mandat — une entree du Lexique des Mandats. Symetrique a
 * {@link africa.civitas.egen.modules.business.organization.api.domain.TypeCellule} (dans le
 * systeme A2), jusque dans sa structure : hierarchie optionnelle entre Mandats
 * (mandatsSupervisesIds) exactement comme les Types de Cellule autorisent des types
 * parents.
 *
 * @param mandatsSupervisesIds les Mandats que celui-ci supervise ; liste vide = aucune
 *                              hierarchie de supervision declaree
 */
public record Mandat(
        UUID id,
        UUID lexiqueMandatsId,
        String libelle,
        String description,
        int niveauAutoriteIndicatif,
        List<UUID> mandatsSupervisesIds,
        UUID mandatModeleOrigineId,
        StatutMandat statut,
        Tracabilite tracabilite) {

    public Mandat {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(lexiqueMandatsId, "lexiqueMandatsId ne peut pas etre nul.");
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("libelle ne peut pas etre vide.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description ne peut pas etre vide.");
        }
        if (niveauAutoriteIndicatif < 0) {
            throw new IllegalArgumentException(
                    "niveauAutoriteIndicatif ne peut pas etre negatif, recu : " + niveauAutoriteIndicatif);
        }
        mandatsSupervisesIds = mandatsSupervisesIds == null ? List.of() : List.copyOf(mandatsSupervisesIds);
        if (mandatsSupervisesIds.contains(id)) {
            throw new IllegalArgumentException("Un Mandat ne peut pas se superviser lui-meme.");
        }
        Objects.requireNonNull(statut, "statut ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    public Optional<UUID> mandatModeleOrigine() {
        return Optional.ofNullable(mandatModeleOrigineId);
    }
}
