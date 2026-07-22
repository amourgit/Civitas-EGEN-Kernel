package africa.civitas.egen.modules.business.organization.api.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** celluleParentId nul = cette Cellule est creee comme Etablissement (racine). */
public record CreerCelluleCommand(
        UUID organisationId,
        UUID celluleParentId,
        UUID typeCelluleId,
        String nom,
        String codeInterne,
        String description,
        String codePaysLocalisation,
        String adressePhysique,
        LocalDate validDu,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerCelluleCommand {
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        Objects.requireNonNull(typeCelluleId, "typeCelluleId ne peut pas etre nul.");
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("nom ne peut pas etre vide.");
        }
        if (codeInterne == null || codeInterne.isBlank()) {
            throw new IllegalArgumentException("codeInterne ne peut pas etre vide.");
        }
        Objects.requireNonNull(validDu, "validDu ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
