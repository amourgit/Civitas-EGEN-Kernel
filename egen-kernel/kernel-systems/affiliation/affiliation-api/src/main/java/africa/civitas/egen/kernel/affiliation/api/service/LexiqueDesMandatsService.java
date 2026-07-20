package africa.civitas.egen.kernel.affiliation.api.service;

import africa.civitas.egen.kernel.affiliation.api.command.CreerLexiqueDesMandatsCommand;
import africa.civitas.egen.kernel.affiliation.api.domain.LexiqueDesMandats;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LexiqueDesMandatsService {

    LexiqueDesMandats creer(CreerLexiqueDesMandatsCommand commande);

    Optional<LexiqueDesMandats> trouverParId(UUID id);

    List<LexiqueDesMandats> listerParOrganisation(UUID organisationId);
}
