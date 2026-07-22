package africa.civitas.egen.modules.business.organization.impl.application;

import africa.civitas.egen.modules.business.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.modules.business.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.modules.business.organization.api.exception.OrganisationIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.service.LexiqueOrganisationnelService;
import africa.civitas.egen.modules.business.organization.impl.domain.LexiqueOrganisationnelEntity;
import africa.civitas.egen.modules.business.organization.impl.infrastructure.LexiqueOrganisationnelMapper;
import africa.civitas.egen.modules.business.organization.impl.infrastructure.LexiqueOrganisationnelRepository;
import africa.civitas.egen.modules.business.organization.impl.infrastructure.OrganisationRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LexiqueOrganisationnelServiceImpl implements LexiqueOrganisationnelService {

    @Inject
    LexiqueOrganisationnelRepository repository;

    @Inject
    OrganisationRepository organisationRepository;

    @Override
    @Transactional
    public LexiqueOrganisationnel creer(CreerLexiqueOrganisationnelCommand commande) {
        if (!organisationRepository.findByIdOptional(commande.organisationId()).isPresent()) {
            throw new OrganisationIntrouvableException(commande.organisationId());
        }

        LexiqueOrganisationnelEntity entity = new LexiqueOrganisationnelEntity();
        entity.id = UUID.randomUUID();
        entity.organisationId = commande.organisationId();
        entity.nom = commande.nom();
        entity.description = commande.description();
        entity.modeleSectorielOrigineId = commande.modeleSectorielOrigineId();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return LexiqueOrganisationnelMapper.toDomain(entity);
    }

    @Override
    public Optional<LexiqueOrganisationnel> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(LexiqueOrganisationnelMapper::toDomain);
    }

    @Override
    public List<LexiqueOrganisationnel> listerParOrganisation(UUID organisationId) {
        return repository.listByOrganisation(organisationId).stream()
                .map(LexiqueOrganisationnelMapper::toDomain)
                .toList();
    }
}
