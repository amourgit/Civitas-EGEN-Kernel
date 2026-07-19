package africa.civitas.egen.kernel.organization.api.command;

import africa.civitas.egen.kernel.organization.api.domain.NatureSuccession;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreerSuccessionOrganisationnelleCommand(
        List<UUID> celluleOrigineIds,
        List<UUID> celluleHeritiereIds,
        NatureSuccession nature,
        LocalDate dateEffet,
        String motifDecisionReference,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerSuccessionOrganisationnelleCommand {
        if (celluleOrigineIds == null || celluleOrigineIds.isEmpty()) {
            throw new IllegalArgumentException("celluleOrigineIds doit contenir au moins un element.");
        }
        celluleOrigineIds = List.copyOf(celluleOrigineIds);
        if (celluleHeritiereIds == null || celluleHeritiereIds.isEmpty()) {
            throw new IllegalArgumentException("celluleHeritiereIds doit contenir au moins un element.");
        }
        celluleHeritiereIds = List.copyOf(celluleHeritiereIds);
        Objects.requireNonNull(nature, "nature ne peut pas etre nulle.");
        Objects.requireNonNull(dateEffet, "dateEffet ne peut pas etre nulle.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
