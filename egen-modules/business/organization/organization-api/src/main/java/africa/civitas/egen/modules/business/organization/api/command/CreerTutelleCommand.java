package africa.civitas.egen.modules.business.organization.api.command;

import africa.civitas.egen.modules.business.organization.api.domain.NatureTutelle;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CreerTutelleCommand(
        UUID celluleRacineId,
        UUID organisationId,
        NatureTutelle nature,
        boolean tutellePrincipale,
        LocalDate dateDebut,
        LocalDate dateFin,
        UUID acteJustificatifRef,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerTutelleCommand {
        Objects.requireNonNull(celluleRacineId, "celluleRacineId ne peut pas etre nul.");
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        Objects.requireNonNull(nature, "nature ne peut pas etre nulle.");
        Objects.requireNonNull(dateDebut, "dateDebut ne peut pas etre nulle.");
        if (dateFin != null && dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("dateFin ne peut pas etre anterieure a dateDebut.");
        }
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
