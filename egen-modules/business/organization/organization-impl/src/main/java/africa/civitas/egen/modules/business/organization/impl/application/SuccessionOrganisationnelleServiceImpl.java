package africa.civitas.egen.modules.business.organization.impl.application;

import africa.civitas.egen.modules.business.organization.api.command.CreerSuccessionOrganisationnelleCommand;
import africa.civitas.egen.modules.business.organization.api.domain.SuccessionOrganisationnelle;
import africa.civitas.egen.modules.business.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.service.SuccessionOrganisationnelleService;
import africa.civitas.egen.modules.business.organization.impl.domain.SuccessionOrganisationnelleEntity;
import africa.civitas.egen.modules.business.organization.impl.infrastructure.CelluleRepository;
import africa.civitas.egen.modules.business.organization.impl.infrastructure.SuccessionOrganisationnelleMapper;
import africa.civitas.egen.modules.business.organization.impl.infrastructure.SuccessionOrganisationnelleRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SuccessionOrganisationnelleServiceImpl implements SuccessionOrganisationnelleService {

    @Inject
    SuccessionOrganisationnelleRepository repository;

    @Inject
    CelluleRepository celluleRepository;

    @Override
    @Transactional
    public SuccessionOrganisationnelle creer(CreerSuccessionOrganisationnelleCommand commande) {
        for (UUID id : commande.celluleOrigineIds()) {
            if (celluleRepository.findById(id) == null) {
                throw new CelluleIntrouvableException(id);
            }
        }
        for (UUID id : commande.celluleHeritiereIds()) {
            if (celluleRepository.findById(id) == null) {
                throw new CelluleIntrouvableException(id);
            }
        }

        SuccessionOrganisationnelleEntity entity = new SuccessionOrganisationnelleEntity();
        entity.id = UUID.randomUUID();
        entity.celluleOrigineIds = List.copyOf(commande.celluleOrigineIds());
        entity.celluleHeritiereIds = List.copyOf(commande.celluleHeritiereIds());
        entity.nature = commande.nature();
        entity.dateEffet = commande.dateEffet();
        entity.motifDecisionReference = commande.motifDecisionReference();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return SuccessionOrganisationnelleMapper.toDomain(entity);
    }

    @Override
    public List<SuccessionOrganisationnelle> listerParCellule(UUID celluleId) {
        return repository.listByCellule(celluleId).stream()
                .map(SuccessionOrganisationnelleMapper::toDomain)
                .toList();
    }
}
