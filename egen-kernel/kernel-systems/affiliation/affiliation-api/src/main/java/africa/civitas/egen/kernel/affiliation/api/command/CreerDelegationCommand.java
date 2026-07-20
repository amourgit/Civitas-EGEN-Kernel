package africa.civitas.egen.kernel.affiliation.api.command;

import africa.civitas.egen.kernel.affiliation.api.domain.EtendueDelegation;
import africa.civitas.egen.kernel.affiliation.api.domain.MotifDelegation;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CreerDelegationCommand(
        UUID affectationOrigineId,
        UUID personneBeneficiaireId,
        EtendueDelegation etendue,
        String actionsCouvertes,
        LocalDate dateDebut,
        LocalDate dateFin,
        MotifDelegation motif,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerDelegationCommand {
        Objects.requireNonNull(affectationOrigineId, "affectationOrigineId ne peut pas etre nul.");
        Objects.requireNonNull(personneBeneficiaireId, "personneBeneficiaireId ne peut pas etre nul.");
        Objects.requireNonNull(etendue, "etendue ne peut pas etre nulle.");
        if (etendue == EtendueDelegation.PARTIELLE && (actionsCouvertes == null || actionsCouvertes.isBlank())) {
            throw new IllegalArgumentException(
                    "actionsCouvertes est obligatoire lorsque etendue == PARTIELLE.");
        }
        if (etendue == EtendueDelegation.TOTALE && actionsCouvertes != null) {
            throw new IllegalArgumentException(
                    "actionsCouvertes ne doit pas etre renseigne lorsque etendue == TOTALE.");
        }
        Objects.requireNonNull(dateDebut, "dateDebut ne peut pas etre nulle.");
        Objects.requireNonNull(dateFin, "dateFin ne peut pas etre nulle : une Delegation est toujours bornee.");
        if (dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("dateFin ne peut pas etre anterieure a dateDebut.");
        }
        Objects.requireNonNull(motif, "motif ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
