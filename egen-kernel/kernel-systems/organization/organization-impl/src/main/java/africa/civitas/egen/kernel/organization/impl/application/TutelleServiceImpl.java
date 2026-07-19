package africa.civitas.egen.kernel.organization.impl.application;

import africa.civitas.egen.kernel.organization.api.command.CreerTutelleCommand;
import africa.civitas.egen.kernel.organization.api.domain.Tutelle;
import africa.civitas.egen.kernel.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.kernel.organization.api.exception.OrganisationIntrouvableException;
import africa.civitas.egen.kernel.organization.api.exception.OrganisationnelConflitException;
import africa.civitas.egen.kernel.organization.api.service.TutelleService;
import africa.civitas.egen.kernel.organization.impl.domain.CelluleEntity;
import africa.civitas.egen.kernel.organization.impl.domain.TutelleEntity;
import africa.civitas.egen.kernel.organization.impl.infrastructure.CelluleRepository;
import africa.civitas.egen.kernel.organization.impl.infrastructure.OrganisationRepository;
import africa.civitas.egen.kernel.organization.impl.infrastructure.TutelleMapper;
import africa.civitas.egen.kernel.organization.impl.infrastructure.TutelleRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TutelleServiceImpl implements TutelleService {

    @Inject
    TutelleRepository repository;

    @Inject
    CelluleRepository celluleRepository;

    @Inject
    OrganisationRepository organisationRepository;

    @Override
    @Transactional
    public Tutelle creer(CreerTutelleCommand commande) {
        CelluleEntity celluleRacine = celluleRepository.findById(commande.celluleRacineId());
        if (celluleRacine == null) {
            throw new CelluleIntrouvableException(commande.celluleRacineId());
        }
        if (celluleRacine.celluleParentId != null) {
            throw new IllegalArgumentException(
                    "celluleRacineId doit designer un Etablissement (Cellule sans parent), "
                            + "recu une Cellule rattachee a un parent.");
        }
        if (!organisationRepository.findByIdOptional(commande.organisationId()).isPresent()) {
            throw new OrganisationIntrouvableException(commande.organisationId());
        }
        if (commande.tutellePrincipale()
                && repository.findPrincipaleByCelluleRacine(commande.celluleRacineId()).isPresent()) {
            throw new OrganisationnelConflitException(
                    "Une Tutelle principale existe deja pour cet Etablissement : "
                            + commande.celluleRacineId());
        }

        TutelleEntity entity = new TutelleEntity();
        entity.id = UUID.randomUUID();
        entity.celluleRacineId = commande.celluleRacineId();
        entity.organisationId = commande.organisationId();
        entity.nature = commande.nature();
        entity.tutellePrincipale = commande.tutellePrincipale();
        entity.dateDebut = commande.dateDebut();
        entity.dateFin = commande.dateFin();
        entity.acteJustificatifRef = commande.acteJustificatifRef();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return TutelleMapper.toDomain(entity);
    }

    @Override
    public List<Tutelle> listerParCellule(UUID celluleRacineId) {
        return repository.listByCelluleRacine(celluleRacineId).stream().map(TutelleMapper::toDomain).toList();
    }
}
