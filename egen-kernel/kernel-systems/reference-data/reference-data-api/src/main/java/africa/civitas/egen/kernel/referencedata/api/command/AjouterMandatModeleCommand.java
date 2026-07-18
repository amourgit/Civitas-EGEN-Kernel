package africa.civitas.egen.kernel.referencedata.api.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.UUID;

public record AjouterMandatModeleCommand(
        UUID modeleSectorialId,
        String libelleSuggere,
        int niveauAutoriteIndicatif,
        String descriptionResponsabilites,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public AjouterMandatModeleCommand {
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
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
