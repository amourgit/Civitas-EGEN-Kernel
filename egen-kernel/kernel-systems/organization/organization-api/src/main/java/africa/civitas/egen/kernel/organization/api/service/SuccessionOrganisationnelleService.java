package africa.civitas.egen.kernel.organization.api.service;

import africa.civitas.egen.kernel.organization.api.command.CreerSuccessionOrganisationnelleCommand;
import africa.civitas.egen.kernel.organization.api.domain.SuccessionOrganisationnelle;

import java.util.List;
import java.util.UUID;

public interface SuccessionOrganisationnelleService {

    SuccessionOrganisationnelle creer(CreerSuccessionOrganisationnelleCommand commande);

    /** Liste les Successions ou la Cellule apparait, comme origine ou comme heritiere. */
    List<SuccessionOrganisationnelle> listerParCellule(UUID celluleId);
}
