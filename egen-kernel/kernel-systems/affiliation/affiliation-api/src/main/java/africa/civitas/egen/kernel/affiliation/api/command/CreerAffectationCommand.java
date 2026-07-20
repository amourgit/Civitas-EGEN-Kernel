package africa.civitas.egen.kernel.affiliation.api.command;

import africa.civitas.egen.kernel.affiliation.api.domain.QuotiteEngagement;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CreerAffectationCommand(
        UUID personneId,
        UUID celluleId,
        UUID mandatId,
        QuotiteEngagement quotiteEngagement,
        LocalDate dateDebut,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerAffectationCommand {
        Objects.requireNonNull(personneId, "personneId ne peut pas etre nul.");
        Objects.requireNonNull(celluleId, "celluleId ne peut pas etre nul.");
        Objects.requireNonNull(mandatId, "mandatId ne peut pas etre nul.");
        Objects.requireNonNull(quotiteEngagement, "quotiteEngagement ne peut pas etre nul.");
        Objects.requireNonNull(dateDebut, "dateDebut ne peut pas etre nulle.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
