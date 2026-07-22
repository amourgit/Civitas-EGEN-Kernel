package africa.civitas.egen.modules.business.organization.impl.affiliation.application;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerLexiqueDesMandatsCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.LexiqueDesMandats;
import africa.civitas.egen.modules.business.organization.api.affiliation.service.LexiqueDesMandatsService;
import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.LexiqueDesMandatsEntity;
import africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure.LexiqueDesMandatsMapper;
import africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure.LexiqueDesMandatsRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.business.organization.api.exception.OrganisationIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.service.OrganisationService;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LexiqueDesMandatsServiceImpl implements LexiqueDesMandatsService {

    @Inject
    LexiqueDesMandatsRepository repository;

    @Inject
    OrganisationService organisationService;

    @Override
    @Transactional
    public LexiqueDesMandats creer(CreerLexiqueDesMandatsCommand commande) {
        if (organisationService.trouverParId(commande.organisationId()).isEmpty()) {
            throw new OrganisationIntrouvableException(commande.organisationId());
        }

        LexiqueDesMandatsEntity entity = new LexiqueDesMandatsEntity();
        entity.id = UUID.randomUUID();
        entity.organisationId = commande.organisationId();
        entity.nom = commande.nom();
        entity.description = commande.description();
        entity.modeleSectorielOrigineId = commande.modeleSectorielOrigineId();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return LexiqueDesMandatsMapper.toDomain(entity);
    }

    @Override
    public Optional<LexiqueDesMandats> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(LexiqueDesMandatsMapper::toDomain);
    }

    @Override
    public List<LexiqueDesMandats> listerParOrganisation(UUID organisationId) {
        return repository.listByOrganisation(organisationId).stream()
                .map(LexiqueDesMandatsMapper::toDomain)
                .toList();
    }
}
