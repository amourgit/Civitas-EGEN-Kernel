package africa.civitas.egen.modules.business.organization.api.affiliation.service;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerMandatCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Mandat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MandatService {

    Mandat creer(CreerMandatCommand commande);

    Optional<Mandat> trouverParId(UUID id);

    List<Mandat> listerParLexique(UUID lexiqueMandatsId);
}
