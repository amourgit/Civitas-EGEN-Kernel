package africa.civitas.egen.kernel.policy.api.command;

import africa.civitas.egen.kernel.policy.api.domain.DomainePolitique;
import africa.civitas.egen.kernel.sdk.contexte.ContexteNature;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CreerPolitiqueCommand(
        UUID contexteId,
        ContexteNature contexteNature,
        DomainePolitique domaine,
        String nomRegle,
        String valeur,
        LocalDate dateEntreeVigueur,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerPolitiqueCommand {
        Objects.requireNonNull(contexteId, "contexteId ne peut pas etre nul.");
        Objects.requireNonNull(contexteNature, "contexteNature ne peut pas etre nulle.");
        Objects.requireNonNull(domaine, "domaine ne peut pas etre nul.");
        if (nomRegle == null || nomRegle.isBlank()) {
            throw new IllegalArgumentException("nomRegle ne peut pas etre vide.");
        }
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("valeur ne peut pas etre vide.");
        }
        Objects.requireNonNull(dateEntreeVigueur, "dateEntreeVigueur ne peut pas etre nulle.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
