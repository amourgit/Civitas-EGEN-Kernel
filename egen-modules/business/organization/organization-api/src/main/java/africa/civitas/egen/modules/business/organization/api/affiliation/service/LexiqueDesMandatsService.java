package africa.civitas.egen.modules.business.organization.api.affiliation.service;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerLexiqueDesMandatsCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.LexiqueDesMandats;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LexiqueDesMandatsService {

    LexiqueDesMandats creer(CreerLexiqueDesMandatsCommand commande);

    Optional<LexiqueDesMandats> trouverParId(UUID id);

    List<LexiqueDesMandats> listerParOrganisation(UUID organisationId);
}
