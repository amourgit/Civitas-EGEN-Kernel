package africa.civitas.egen.kernel.organization.api.service;

import africa.civitas.egen.kernel.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.kernel.organization.api.domain.Organisation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationService {

    Organisation creer(CreerOrganisationCommand commande);

    Optional<Organisation> trouverParId(UUID id);

    List<Organisation> lister();
}
