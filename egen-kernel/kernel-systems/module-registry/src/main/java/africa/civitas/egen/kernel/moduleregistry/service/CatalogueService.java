package africa.civitas.egen.kernel.moduleregistry.service;

import africa.civitas.egen.kernel.domain.module.CatalogueEntree;
import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.moduleregistry.command.EnregistrerModuleCommand;

import java.util.List;

/** Administre le Catalogue — le premier palier de la cascade Souscription/Activation (§B.11). */
public interface CatalogueService {

    /**
     * @throws africa.civitas.egen.kernel.moduleregistry.exception.ModuleDejaAuCatalogueException
     *         si ce moduleId est deja au Catalogue
     */
    CatalogueEntree enregistrer(EnregistrerModuleCommand commande);

    boolean estAuCatalogue(ModuleId moduleId);

    List<CatalogueEntree> lister();
}
