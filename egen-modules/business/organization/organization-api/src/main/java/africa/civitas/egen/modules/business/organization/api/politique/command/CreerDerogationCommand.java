package africa.civitas.egen.modules.business.organization.api.politique.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CreerDerogationCommand(
        UUID politiqueId,
        UUID celluleDerogatoireId,
        String valeur,
        String justification,
        LocalDate dateEntreeVigueur,
        LocalDate dateFin,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerDerogationCommand {
        Objects.requireNonNull(politiqueId, "politiqueId ne peut pas etre nul.");
        Objects.requireNonNull(celluleDerogatoireId, "celluleDerogatoireId ne peut pas etre nul.");
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("valeur ne peut pas etre vide.");
        }
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("justification ne peut pas etre vide.");
        }
        Objects.requireNonNull(dateEntreeVigueur, "dateEntreeVigueur ne peut pas etre nulle.");
        if (dateFin != null && dateFin.isBefore(dateEntreeVigueur)) {
            throw new IllegalArgumentException("dateFin ne peut pas etre anterieure a dateEntreeVigueur.");
        }
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
