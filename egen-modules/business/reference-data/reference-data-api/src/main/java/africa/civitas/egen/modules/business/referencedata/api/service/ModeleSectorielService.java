package africa.civitas.egen.modules.business.referencedata.api.service;

import africa.civitas.egen.modules.business.referencedata.api.command.CreerModeleSectorielCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.ModeleSectoriel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModeleSectorielService {

    ModeleSectoriel creer(CreerModeleSectorielCommand commande);

    Optional<ModeleSectoriel> trouverParId(UUID id);

    /** Liste uniquement les Modeles Sectoriels ACTIF, proposables a l'adoption. */
    List<ModeleSectoriel> listerActifs();
}
