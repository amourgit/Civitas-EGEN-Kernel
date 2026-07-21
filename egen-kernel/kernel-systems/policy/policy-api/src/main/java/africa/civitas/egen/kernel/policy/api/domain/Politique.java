package africa.civitas.egen.kernel.policy.api.domain;

import africa.civitas.egen.kernel.sdk.contexte.ContexteNature;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Modele de lecture d'une Politique (B1.1) — un ensemble de regles definies a une
 * portee donnee. Premiere entite du Kernel a utiliser le Contexte unifie de
 * kernel-sdk tel que concu des le depart : un seul {@code contexteId}, jamais une
 * paire nullable Organisation/Cellule.
 *
 * @param contexteId l'Organisation ou la Cellule portant cette Politique
 * @param contexteNature precise si {@code contexteId} designe une Organisation ou une Cellule
 * @param valeur contenu structure du parametrage, dont la forme depend du domaine
 */
public record Politique(
        UUID id,
        UUID contexteId,
        ContexteNature contexteNature,
        DomainePolitique domaine,
        String nomRegle,
        String valeur,
        StatutPolitique statut,
        LocalDate dateEntreeVigueur,
        Tracabilite tracabilite) {

    public Politique {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(contexteId, "contexteId ne peut pas etre nul.");
        Objects.requireNonNull(contexteNature, "contexteNature ne peut pas etre nulle.");
        Objects.requireNonNull(domaine, "domaine ne peut pas etre nul.");
        if (nomRegle == null || nomRegle.isBlank()) {
            throw new IllegalArgumentException("nomRegle ne peut pas etre vide.");
        }
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("valeur ne peut pas etre vide.");
        }
        Objects.requireNonNull(statut, "statut ne peut pas etre nul.");
        Objects.requireNonNull(dateEntreeVigueur, "dateEntreeVigueur ne peut pas etre nulle.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }
}
