package africa.civitas.egen.kernel.policy.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'une Derogation (B1.2) — le remplacement local d'une Politique
 * heritee par une Cellule precise. Regle de resolution : la Derogation la plus
 * proche dans l'arbre l'emporte toujours (voir PolitiqueService#resoudrePourCellule).
 */
public record Derogation(
        UUID id,
        UUID politiqueId,
        UUID celluleDerogatoireId,
        String valeur,
        String justification,
        LocalDate dateEntreeVigueur,
        LocalDate dateFin,
        Tracabilite tracabilite) {

    public Derogation {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(politiqueId, "politiqueId ne peut pas etre nul.");
        Objects.requireNonNull(celluleDerogatoireId, "celluleDerogatoireId ne peut pas etre nul.");
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("valeur ne peut pas etre vide.");
        }
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException(
                    "justification ne peut pas etre vide : une Derogation sans justification "
                            + "n'est pas exploitable en audit.");
        }
        Objects.requireNonNull(dateEntreeVigueur, "dateEntreeVigueur ne peut pas etre nulle.");
        if (dateFin != null && dateFin.isBefore(dateEntreeVigueur)) {
            throw new IllegalArgumentException("dateFin ne peut pas etre anterieure a dateEntreeVigueur.");
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    public Optional<LocalDate> dateFinEventuelle() {
        return Optional.ofNullable(dateFin);
    }

    /** @return vrai si cette Derogation est en vigueur a la date donnee. */
    public boolean estEnVigueurLe(LocalDate date) {
        boolean apresDebut = !date.isBefore(dateEntreeVigueur);
        boolean avantFin = dateFin == null || !date.isAfter(dateFin);
        return apresDebut && avantFin;
    }
}
