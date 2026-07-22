package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;

/**
 * Modele de lecture d'un Mandat Modele (B4.8) — symetrique a {@link TypeCelluleModele},
 * la version gabarit d'un Mandat, rattachee a un Modele Sectoriel, copiee vers le
 * Lexique des Mandats d'une Organisation (A3) a l'adoption.
 */
public record MandatModele(
        UUID id,
        UUID modeleSectorialId,
        String libelleSuggere,
        int niveauAutoriteIndicatif,
        String descriptionResponsabilites,
        Tracabilite tracabilite) {

    public MandatModele {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(modeleSectorialId, "modeleSectorialId ne peut pas etre nul.");
        if (libelleSuggere == null || libelleSuggere.isBlank()) {
            throw new IllegalArgumentException("libelleSuggere ne peut pas etre vide.");
        }
        if (niveauAutoriteIndicatif < 0) {
            throw new IllegalArgumentException(
                    "niveauAutoriteIndicatif ne peut pas etre negatif, recu : " + niveauAutoriteIndicatif);
        }
        if (descriptionResponsabilites == null || descriptionResponsabilites.isBlank()) {
            throw new IllegalArgumentException("descriptionResponsabilites ne peut pas etre vide.");
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }
}
