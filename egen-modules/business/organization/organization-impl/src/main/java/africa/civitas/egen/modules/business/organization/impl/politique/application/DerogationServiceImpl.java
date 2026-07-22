package africa.civitas.egen.modules.business.organization.impl.politique.application;

import africa.civitas.egen.modules.business.organization.api.politique.command.CreerDerogationCommand;
import africa.civitas.egen.modules.business.organization.api.politique.domain.Derogation;
import africa.civitas.egen.modules.business.organization.api.politique.exception.PolitiqueIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.politique.service.DerogationService;
import africa.civitas.egen.modules.business.organization.impl.politique.domain.DerogationEntity;
import africa.civitas.egen.modules.business.organization.impl.politique.infrastructure.DerogationMapper;
import africa.civitas.egen.modules.business.organization.impl.politique.infrastructure.DerogationRepository;
import africa.civitas.egen.modules.business.organization.impl.politique.infrastructure.PolitiqueRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.business.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.service.CelluleService;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DerogationServiceImpl implements DerogationService {

    @Inject
    DerogationRepository repository;

    @Inject
    PolitiqueRepository politiqueRepository;

    @Inject
    CelluleService celluleService;

    @Override
    @Transactional
    public Derogation creer(CreerDerogationCommand commande) {
        if (politiqueRepository.findById(commande.politiqueId()) == null) {
            throw new PolitiqueIntrouvableException(commande.politiqueId());
        }
        if (celluleService.trouverParId(commande.celluleDerogatoireId()).isEmpty()) {
            throw new CelluleIntrouvableException(commande.celluleDerogatoireId());
        }

        DerogationEntity entity = new DerogationEntity();
        entity.id = UUID.randomUUID();
        entity.politiqueId = commande.politiqueId();
        entity.celluleDerogatoireId = commande.celluleDerogatoireId();
        entity.valeur = commande.valeur();
        entity.justification = commande.justification();
        entity.dateEntreeVigueur = commande.dateEntreeVigueur();
        entity.dateFin = commande.dateFin();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return DerogationMapper.toDomain(entity);
    }

    @Override
    public List<Derogation> listerParPolitique(UUID politiqueId) {
        return repository.listByPolitique(politiqueId).stream().map(DerogationMapper::toDomain).toList();
    }
}
