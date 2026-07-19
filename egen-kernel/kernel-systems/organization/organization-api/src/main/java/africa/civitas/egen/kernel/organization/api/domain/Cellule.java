package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'une Cellule (A2.4) — le noeud generique et recursif de la
 * hierarchie interne d'une Organisation. Un Etablissement n'est qu'une convention :
 * une Cellule dont {@link #celluleParentId()} est absent.
 *
 * @param celluleParentId absent si cette Cellule est un Etablissement (racine)
 * @param validDu date de creation effective de la Cellule (distincte de la
 *                traçabilite technique — c'est une date metier)
 * @param validAu date de fermeture ou de fusion, absente si la Cellule est toujours active
 */
public record Cellule(
        UUID id,
        UUID organisationId,
        UUID celluleParentId,
        UUID typeCelluleId,
        String nom,
        String codeInterne,
        String description,
        String codePaysLocalisation,
        String adressePhysique,
        StatutCellule statut,
        LocalDate validDu,
        LocalDate validAu,
        Tracabilite tracabilite) {

    public Cellule {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        if (celluleParentId != null && celluleParentId.equals(id)) {
            throw new IllegalArgumentException("Une Cellule ne peut pas etre sa propre Cellule parente.");
        }
        Objects.requireNonNull(typeCelluleId, "typeCelluleId ne peut pas etre nul.");
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("nom ne peut pas etre vide.");
        }
        if (codeInterne == null || codeInterne.isBlank()) {
            throw new IllegalArgumentException("codeInterne ne peut pas etre vide.");
        }
        Objects.requireNonNull(statut, "statut ne peut pas etre nul.");
        Objects.requireNonNull(validDu, "validDu ne peut pas etre nul.");
        if (validAu != null && validAu.isBefore(validDu)) {
            throw new IllegalArgumentException("validAu ne peut pas etre anterieure a validDu.");
        }
        if (statut == StatutCellule.FERME && validAu == null) {
            throw new IllegalArgumentException("Une Cellule FERME doit porter une validAu.");
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    /** @return vrai si cette Cellule est un Etablissement (racine de son arbre). */
    public boolean estEtablissement() {
        return celluleParentId == null;
    }

    public Optional<UUID> celluleParente() {
        return Optional.ofNullable(celluleParentId);
    }
}
