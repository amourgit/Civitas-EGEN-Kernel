package africa.civitas.egen.modules.business.organization.api.service;

import africa.civitas.egen.modules.business.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.modules.business.organization.api.domain.Cellule;

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

    /**
     * Liste tous les ancetres d'une Cellule, du plus proche au plus eloigne, via la
     * Fermeture Transitive. Introduit pour la Politique organisationnelle (§B.12 de la Charte v3) : la regle de
     * resolution d'une Derogation ("la plus proche dans l'arbre l'emporte toujours")
     * a besoin de remonter la chaine d'ancetres dans cet ordre precis.
     */
    List<Cellule> listerAncetres(UUID celluleId);
}
