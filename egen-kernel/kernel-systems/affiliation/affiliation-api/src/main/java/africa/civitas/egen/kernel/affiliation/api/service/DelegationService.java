package africa.civitas.egen.kernel.affiliation.api.service;

import africa.civitas.egen.kernel.affiliation.api.command.CreerDelegationCommand;
import africa.civitas.egen.kernel.affiliation.api.domain.Delegation;

import java.util.List;
import java.util.UUID;

public interface DelegationService {

    Delegation creer(CreerDelegationCommand commande);

    List<Delegation> listerParAffectationOrigine(UUID affectationOrigineId);

    List<Delegation> listerParPersonneBeneficiaire(UUID personneBeneficiaireId);
}
