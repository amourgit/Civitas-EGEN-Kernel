package africa.civitas.egen.kernel.policy.impl.application;

import africa.civitas.egen.kernel.policy.api.command.CreerDerogationCommand;
import africa.civitas.egen.kernel.policy.api.domain.Derogation;
import africa.civitas.egen.kernel.policy.api.exception.PolitiqueIntrouvableException;
import africa.civitas.egen.kernel.policy.api.service.DerogationService;
import africa.civitas.egen.kernel.policy.impl.domain.DerogationEntity;
import africa.civitas.egen.kernel.policy.impl.infrastructure.DerogationMapper;
import africa.civitas.egen.kernel.policy.impl.infrastructure.DerogationRepository;
import africa.civitas.egen.kernel.policy.impl.infrastructure.PolitiqueRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.kernel.organization.api.service.CelluleService;
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
