package africa.civitas.egen.kernel.moduleregistry.application;

import africa.civitas.egen.kernel.domain.module.CatalogueEntree;
import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.moduleregistry.command.EnregistrerModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.exception.ModuleDejaAuCatalogueException;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.CatalogueEntreeEntity;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.CatalogueEntreeMapper;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.CatalogueEntreeRepository;
import africa.civitas.egen.kernel.moduleregistry.service.CatalogueService;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CatalogueServiceImpl implements CatalogueService {

    @Inject
    CatalogueEntreeRepository repository;

    @Override
    @Transactional
    public CatalogueEntree enregistrer(EnregistrerModuleCommand commande) {
        if (repository.existeParModuleId(commande.moduleId().valeur())) {
            throw new ModuleDejaAuCatalogueException(
                    "Le module '" + commande.moduleId() + "' est deja au Catalogue.");
        }

        CatalogueEntreeEntity entity = new CatalogueEntreeEntity();
        entity.id = UUID.randomUUID();
        entity.moduleId = commande.moduleId().valeur();
        entity.nom = commande.nom();
        entity.description = commande.description();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return CatalogueEntreeMapper.toDomain(entity);
    }

    @Override
    public boolean estAuCatalogue(ModuleId moduleId) {
        return repository.existeParModuleId(moduleId.valeur());
    }

    @Override
    public List<CatalogueEntree> lister() {
        return repository.listAll().stream().map(CatalogueEntreeMapper::toDomain).toList();
    }
}
