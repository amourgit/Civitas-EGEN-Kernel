package africa.civitas.egen.kernel.moduleregistry.service;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.domain.module.Souscription;
import africa.civitas.egen.kernel.moduleregistry.command.ResilierSouscriptionCommand;
import africa.civitas.egen.kernel.moduleregistry.command.SouscrireModuleCommand;

import java.util.List;
import java.util.UUID;

/** Administre les Souscriptions — le deuxieme palier de la cascade (§B.11). */
public interface SouscriptionService {

    /**
     * @throws africa.civitas.egen.kernel.moduleregistry.exception.ModuleIntrouvableAuCatalogueException
     *         si ce module n'est pas au Catalogue
     * @throws africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionDejaActiveException
     *         si une Souscription active existe deja pour ce Contexte et ce module
     */
    Souscription souscrire(SouscrireModuleCommand commande);

    /**
     * @throws africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionIntrouvableException
     *         si la Souscription est introuvable ou deja resiliee
     */
    void resilier(ResilierSouscriptionCommand commande);

    boolean estActivePour(UUID contexteId, ModuleId moduleId);

    List<Souscription> listerPourContexte(UUID contexteId);
}
