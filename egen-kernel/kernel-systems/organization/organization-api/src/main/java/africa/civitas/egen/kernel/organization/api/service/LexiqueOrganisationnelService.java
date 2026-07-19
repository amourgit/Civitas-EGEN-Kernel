package africa.civitas.egen.kernel.organization.api.service;

import africa.civitas.egen.kernel.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.kernel.organization.api.domain.LexiqueOrganisationnel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LexiqueOrganisationnelService {

    LexiqueOrganisationnel creer(CreerLexiqueOrganisationnelCommand commande);

    Optional<LexiqueOrganisationnel> trouverParId(UUID id);

    List<LexiqueOrganisationnel> listerParOrganisation(UUID organisationId);
}
