package africa.civitas.egen.modules.business.referencedata.api.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;

public record CreerModeleSectorielCommand(
        String nomSecteur,
        String description,
        String versionModele,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerModeleSectorielCommand {
        if (nomSecteur == null || nomSecteur.isBlank()) {
            throw new IllegalArgumentException("nomSecteur ne peut pas etre vide.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description ne peut pas etre vide.");
        }
        if (versionModele == null || versionModele.isBlank()) {
            throw new IllegalArgumentException("versionModele ne peut pas etre vide.");
        }
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
