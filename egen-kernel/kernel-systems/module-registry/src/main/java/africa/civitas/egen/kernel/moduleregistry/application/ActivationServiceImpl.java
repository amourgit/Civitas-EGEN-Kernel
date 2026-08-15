package africa.civitas.egen.kernel.moduleregistry.application;

import africa.civitas.egen.kernel.domain.module.Activation;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.moduleregistry.command.ActiverModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.DesactiverModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.exception.ActivationDejaActiveException;
import africa.civitas.egen.kernel.moduleregistry.exception.ActivationIntrouvableException;
import africa.civitas.egen.kernel.moduleregistry.exception.ModuleIntrouvableAuCatalogueException;
import africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionRequiseException;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.ActivationEntity;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.ActivationMapper;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.ActivationRepository;
import africa.civitas.egen.kernel.moduleregistry.service.ActivationService;
import africa.civitas.egen.kernel.moduleregistry.service.CatalogueService;
import africa.civitas.egen.kernel.moduleregistry.service.SouscriptionService;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fait respecter la cascade complete (§B.11) a chaque Activation : Catalogue, puis
 * Souscription, puis seulement alors Activation — jamais dans un autre ordre, jamais
 * en sautant un palier.
 */
@ApplicationScoped
public class ActivationServiceImpl implements ActivationService {

    @Inject
    ActivationRepository repository;

    @Inject
    CatalogueService catalogueService;

    @Inject
    SouscriptionService souscriptionService;

    @Override
    @Transactional
    public Activation activer(ActiverModuleCommand commande) {
        if (!catalogueService.estAuCatalogue(commande.moduleId())) {
            throw new ModuleIntrouvableAuCatalogueException(
                    "Le module '" + commande.moduleId() + "' n'est pas au Catalogue : "
                            + "impossible de l'activer.");
        }
        if (!souscriptionService.estActivePour(commande.contexteSouscripteurId(), commande.moduleId())) {
            throw new SouscriptionRequiseException(
                    "Le Contexte " + commande.contexteSouscripteurId() + " n'a aucune Souscription "
                            + "active pour le module '" + commande.moduleId() + "' : impossible "
                            + "de l'activer pour l'un de ses Contextes dependants.");
        }
        if (repository.existeActive(commande.contexteCibleId(), commande.moduleId().valeur())) {
            throw new ActivationDejaActiveException(
                    "Le Contexte " + commande.contexteCibleId() + " a deja active le module '"
                            + commande.moduleId() + "'.");
        }

        ActivationEntity entity = new ActivationEntity();
        entity.id = UUID.randomUUID();
        entity.contexteId = commande.contexteCibleId();
        entity.moduleId = commande.moduleId().valeur();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return ActivationMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void desactiver(DesactiverModuleCommand commande) {
        ActivationEntity entity = repository.findByIdOptional(commande.activationId())
                .orElseThrow(() -> new ActivationIntrouvableException(
                        "Aucune Activation ne correspond a l'identifiant " + commande.activationId() + "."));

        Tracabilite actuelle = entity.tracabilite.toDomain();
        if (actuelle.estSupprimee()) {
            throw new ActivationIntrouvableException(
                    "L'Activation " + commande.activationId() + " est deja eteinte.");
        }

        Tracabilite miseAJour = actuelle.avecSuppressionLogique(commande.demandePar(), commande.motif());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(miseAJour);
    }

    @Override
    public List<Activation> listerPourContexte(UUID contexteId) {
        return repository.listerPourContexte(contexteId).stream()
                .map(ActivationMapper::toDomain)
                .toList();
    }
}
