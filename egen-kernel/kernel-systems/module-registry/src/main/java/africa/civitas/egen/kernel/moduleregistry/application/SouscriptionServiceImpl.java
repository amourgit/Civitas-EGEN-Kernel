package africa.civitas.egen.kernel.moduleregistry.application;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.domain.module.Souscription;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.moduleregistry.command.ResilierSouscriptionCommand;
import africa.civitas.egen.kernel.moduleregistry.command.SouscrireModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.exception.ModuleIntrouvableAuCatalogueException;
import africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionDejaActiveException;
import africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionIntrouvableException;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.SouscriptionEntity;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.SouscriptionMapper;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.SouscriptionRepository;
import africa.civitas.egen.kernel.moduleregistry.service.CatalogueService;
import africa.civitas.egen.kernel.moduleregistry.service.SouscriptionService;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SouscriptionServiceImpl implements SouscriptionService {

    @Inject
    SouscriptionRepository repository;

    @Inject
    CatalogueService catalogueService;

    @Override
    @Transactional
    public Souscription souscrire(SouscrireModuleCommand commande) {
        if (!catalogueService.estAuCatalogue(commande.moduleId())) {
            throw new ModuleIntrouvableAuCatalogueException(
                    "Le module '" + commande.moduleId() + "' n'est pas au Catalogue : "
                            + "impossible d'y souscrire.");
        }
        if (repository.existeActive(commande.organisationId(), commande.moduleId().valeur())) {
            throw new SouscriptionDejaActiveException(
                    "L'Organisation " + commande.organisationId() + " a deja une Souscription "
                            + "active pour le module '" + commande.moduleId() + "'.");
        }

        SouscriptionEntity entity = new SouscriptionEntity();
        entity.id = UUID.randomUUID();
        entity.organisationId = commande.organisationId();
        entity.moduleId = commande.moduleId().valeur();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return SouscriptionMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void resilier(ResilierSouscriptionCommand commande) {
        SouscriptionEntity entity = repository.findByIdOptional(commande.souscriptionId())
                .orElseThrow(() -> new SouscriptionIntrouvableException(
                        "Aucune Souscription ne correspond a l'identifiant " + commande.souscriptionId() + "."));

        Tracabilite actuelle = entity.tracabilite.toDomain();
        if (actuelle.estSupprimee()) {
            throw new SouscriptionIntrouvableException(
                    "La Souscription " + commande.souscriptionId() + " est deja resiliee.");
        }

        Tracabilite miseAJour = actuelle.avecSuppressionLogique(commande.demandePar(), commande.motif());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(miseAJour);
    }

    @Override
    public boolean estActivePour(UUID organisationId, ModuleId moduleId) {
        return repository.existeActive(organisationId, moduleId.valeur());
    }

    @Override
    public List<Souscription> listerPourOrganisation(UUID organisationId) {
        return repository.listerPourOrganisation(organisationId).stream()
                .map(SouscriptionMapper::toDomain)
                .toList();
    }
}
