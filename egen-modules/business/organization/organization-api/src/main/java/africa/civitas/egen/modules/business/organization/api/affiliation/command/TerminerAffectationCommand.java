package africa.civitas.egen.modules.business.organization.api.affiliation.command;

import africa.civitas.egen.modules.business.organization.api.affiliation.domain.MotifFinAffectation;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Commande de fin d'une Affectation existante — fait passer son statut a TERMINEE. */
public record TerminerAffectationCommand(
        UUID affectationId,
        LocalDate dateFin,
        MotifFinAffectation motifFin,
        Acteur demandePar,
        String motif) {

    public TerminerAffectationCommand {
        Objects.requireNonNull(affectationId, "affectationId ne peut pas etre nul.");
        Objects.requireNonNull(dateFin, "dateFin ne peut pas etre nulle.");
        Objects.requireNonNull(motifFin, "motifFin ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException(
                    "motif ne peut pas etre vide : requis par le Socle de Traçabilite pour toute "
                            + "modification posterieure a la creation.");
        }
    }
}
