package africa.civitas.egen.modules.business.organization.api.affiliation.service;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerAffectationCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.command.TerminerAffectationCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Affectation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AffectationService {

    /**
     * Cree une nouvelle Affectation. L'implementation doit verifier l'existence de
     * la Personne (via identity-api) et de la Cellule (via organization-api) avant
     * toute ecriture — c'est le pont reel entre A1 et A2.
     */
    Affectation creer(CreerAffectationCommand commande);

    /** Fait passer une Affectation active a TERMINEE — une modification, jamais une suppression. */
    Affectation terminer(TerminerAffectationCommand commande);

    Optional<Affectation> trouverParId(UUID id);

    List<Affectation> listerParPersonne(UUID personneId);

    List<Affectation> listerParCellule(UUID celluleId);
}
