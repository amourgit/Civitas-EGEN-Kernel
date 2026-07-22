package africa.civitas.egen.modules.business.organization.api.service;

import africa.civitas.egen.modules.business.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.modules.business.organization.api.domain.Organisation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationService {

    Organisation creer(CreerOrganisationCommand commande);

    Optional<Organisation> trouverParId(UUID id);

    List<Organisation> lister();
}
