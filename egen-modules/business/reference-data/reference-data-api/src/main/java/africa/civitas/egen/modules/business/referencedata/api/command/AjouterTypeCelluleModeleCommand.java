package africa.civitas.egen.modules.business.referencedata.api.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.UUID;

public record AjouterTypeCelluleModeleCommand(
        UUID modeleSectorialId,
        String libelleMetierSuggere,
        int niveauIndicatifSuggere,
        UUID typeParentSuggereId,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public AjouterTypeCelluleModeleCommand {
        Objects.requireNonNull(modeleSectorialId, "modeleSectorialId ne peut pas etre nul.");
        if (libelleMetierSuggere == null || libelleMetierSuggere.isBlank()) {
            throw new IllegalArgumentException("libelleMetierSuggere ne peut pas etre vide.");
        }
        if (niveauIndicatifSuggere < 0) {
            throw new IllegalArgumentException(
                    "niveauIndicatifSuggere ne peut pas etre negatif, recu : " + niveauIndicatifSuggere);
        }
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
