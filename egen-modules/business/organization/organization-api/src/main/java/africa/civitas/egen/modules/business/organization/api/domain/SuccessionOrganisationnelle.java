package africa.civitas.egen.modules.business.organization.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'une Succession Organisationnelle (A2.7) — la memoire d'une
 * reorganisation (fusion, scission, renommage...) qui relie une ou plusieurs Cellules
 * d'origine a une ou plusieurs Cellules heritieres, sans jamais perdre la trace du
 * passe.
 *
 * @param celluleOrigineIds une ou plusieurs — plusieurs en cas de fusion
 * @param celluleHeritiereIds une ou plusieurs — plusieurs en cas de scission
 */
public record SuccessionOrganisationnelle(
        UUID id,
        List<UUID> celluleOrigineIds,
        List<UUID> celluleHeritiereIds,
        NatureSuccession nature,
        LocalDate dateEffet,
        String motifDecisionReference,
        Tracabilite tracabilite) {

    public SuccessionOrganisationnelle {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
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
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    public Optional<String> motifDecision() {
        return Optional.ofNullable(motifDecisionReference);
    }
}
