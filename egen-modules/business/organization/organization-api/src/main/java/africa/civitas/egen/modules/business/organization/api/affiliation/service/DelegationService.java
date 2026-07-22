package africa.civitas.egen.modules.business.organization.api.affiliation.service;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerDelegationCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Delegation;

import java.util.List;
import java.util.UUID;

public interface DelegationService {

    Delegation creer(CreerDelegationCommand commande);

    List<Delegation> listerParAffectationOrigine(UUID affectationOrigineId);

    List<Delegation> listerParPersonneBeneficiaire(UUID personneBeneficiaireId);
}
