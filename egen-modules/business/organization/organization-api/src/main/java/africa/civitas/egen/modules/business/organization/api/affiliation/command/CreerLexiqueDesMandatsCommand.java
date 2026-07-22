package africa.civitas.egen.modules.business.organization.api.affiliation.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.UUID;

public record CreerLexiqueDesMandatsCommand(
        UUID organisationId,
        String nom,
        String description,
        UUID modeleSectorielOrigineId,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerLexiqueDesMandatsCommand {
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("nom ne peut pas etre vide.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description ne peut pas etre vide.");
        }
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
