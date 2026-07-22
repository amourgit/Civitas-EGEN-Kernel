package africa.civitas.egen.modules.business.organization.api.affiliation.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreerMandatCommand(
        UUID lexiqueMandatsId,
        String libelle,
        String description,
        int niveauAutoriteIndicatif,
        List<UUID> mandatsSupervisesIds,
        UUID mandatModeleOrigineId,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerMandatCommand {
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
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
