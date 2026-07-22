package africa.civitas.egen.modules.business.organization.api.affiliation.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'une Affectation (A3.1) — la relation entre une Personne (A1) et
 * une Cellule (A2), avec un Mandat porte et une periode de validite. Le pont le plus
 * structurant de tout le Kernel, volontairement mince : il ne porte que des
 * identifiants, jamais les objets Personne ou Cellule eux-memes.
 *
 * @param personneId reference vers A1.Personne
 * @param celluleId reference vers A2.Cellule — a n'importe quel niveau de l'arbre
 * @param mandatId reference vers A3.Mandat
 */
public record Affectation(
        UUID id,
        UUID personneId,
        UUID celluleId,
        UUID mandatId,
        QuotiteEngagement quotiteEngagement,
        LocalDate dateDebut,
        LocalDate dateFin,
        StatutAffectation statut,
        MotifFinAffectation motifFin,
        Tracabilite tracabilite) {

    public Affectation {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(personneId, "personneId ne peut pas etre nul.");
        Objects.requireNonNull(celluleId, "celluleId ne peut pas etre nul.");
        Objects.requireNonNull(mandatId, "mandatId ne peut pas etre nul.");
        Objects.requireNonNull(quotiteEngagement, "quotiteEngagement ne peut pas etre nul.");
        Objects.requireNonNull(dateDebut, "dateDebut ne peut pas etre nulle.");
        if (dateFin != null && dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("dateFin ne peut pas etre anterieure a dateDebut.");
        }
        Objects.requireNonNull(statut, "statut ne peut pas etre nul.");
        if (statut == StatutAffectation.TERMINEE && motifFin == null) {
            throw new IllegalArgumentException("Une Affectation TERMINEE doit porter un motifFin.");
        }
        if (statut != StatutAffectation.TERMINEE && motifFin != null) {
            throw new IllegalArgumentException("motifFin ne doit etre renseigne que si statut == TERMINEE.");
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    public Optional<LocalDate> dateFinEventuelle() {
        return Optional.ofNullable(dateFin);
    }
}
