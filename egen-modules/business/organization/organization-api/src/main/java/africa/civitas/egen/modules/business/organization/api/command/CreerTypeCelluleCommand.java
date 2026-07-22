package africa.civitas.egen.modules.business.organization.api.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreerTypeCelluleCommand(
        UUID lexiqueId,
        String libelle,
        String description,
        int niveauIndicatif,
        List<UUID> typesParentsAutorisesIds,
        UUID typeCelluleModeleOrigineId,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerTypeCelluleCommand {
        Objects.requireNonNull(lexiqueId, "lexiqueId ne peut pas etre nul.");
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("libelle ne peut pas etre vide.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description ne peut pas etre vide.");
        }
        if (niveauIndicatif < 0) {
            throw new IllegalArgumentException(
                    "niveauIndicatif ne peut pas etre negatif, recu : " + niveauIndicatif);
        }
        typesParentsAutorisesIds = typesParentsAutorisesIds == null
                ? List.of() : List.copyOf(typesParentsAutorisesIds);
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
