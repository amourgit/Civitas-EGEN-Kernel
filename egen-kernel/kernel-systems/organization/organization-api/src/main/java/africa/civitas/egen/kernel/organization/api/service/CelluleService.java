package africa.civitas.egen.kernel.organization.api.service;

import africa.civitas.egen.kernel.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.kernel.organization.api.domain.Cellule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CelluleService {

    /**
     * Cree une Cellule. Si {@code commande.celluleParentId()} est absent, la Cellule
     * est creee comme Etablissement (racine). La Fermeture Transitive est mise a jour
     * automatiquement — jamais manipulee directement par l'appelant.
     */
    Cellule creer(CreerCelluleCommand commande);

    Optional<Cellule> trouverParId(UUID id);

    /** Liste toutes les Cellules d'une Organisation, tous niveaux confondus. */
    List<Cellule> listerParOrganisation(UUID organisationId);

    /** Liste uniquement les Etablissements (Cellules racines) d'une Organisation. */
    List<Cellule> listerEtablissements(UUID organisationId);

    /**
     * Liste tous les descendants d'une Cellule, a n'importe quelle profondeur, via la
     * Fermeture Transitive — une seule requete, jamais de recursion applicative.
     */
    List<Cellule> listerDescendants(UUID celluleId);
}
