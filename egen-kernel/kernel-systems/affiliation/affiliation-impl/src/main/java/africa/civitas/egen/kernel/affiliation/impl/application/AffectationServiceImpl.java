package africa.civitas.egen.kernel.affiliation.impl.application;

import africa.civitas.egen.kernel.affiliation.api.command.CreerAffectationCommand;
import africa.civitas.egen.kernel.affiliation.api.command.TerminerAffectationCommand;
import africa.civitas.egen.kernel.affiliation.api.domain.Affectation;
import africa.civitas.egen.kernel.affiliation.api.domain.StatutAffectation;
import africa.civitas.egen.kernel.affiliation.api.exception.AffectationIntrouvableException;
import africa.civitas.egen.kernel.affiliation.api.exception.MandatIntrouvableException;
import africa.civitas.egen.kernel.affiliation.api.service.AffectationService;
import africa.civitas.egen.kernel.affiliation.impl.domain.AffectationEntity;
import africa.civitas.egen.kernel.affiliation.impl.infrastructure.AffectationMapper;
import africa.civitas.egen.kernel.affiliation.impl.infrastructure.AffectationRepository;
import africa.civitas.egen.kernel.affiliation.impl.infrastructure.MandatRepository;
import africa.civitas.egen.kernel.identity.api.exception.PersonneIntrouvableException;
import africa.civitas.egen.kernel.identity.api.service.PersonneService;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.kernel.organization.api.service.CelluleService;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation du Systeme Rattachements pour l'Affectation — le pont reel entre A1
 * et A2. {@link #creer(CreerAffectationCommand)} n'ecrit jamais sans avoir verifie,
 * via les contrats identity-api et organization-api, que la Personne et la Cellule
 * referencees existent bel et bien.
 */
@ApplicationScoped
public class AffectationServiceImpl implements AffectationService {

    @Inject
    AffectationRepository repository;

    @Inject
    MandatRepository mandatRepository;

    @Inject
    PersonneService personneService;

    @Inject
    CelluleService celluleService;

    @Override
    @Transactional
    public Affectation creer(CreerAffectationCommand commande) {
        if (personneService.trouverParId(commande.personneId()).isEmpty()) {
            throw new PersonneIntrouvableException(commande.personneId());
        }
        if (celluleService.trouverParId(commande.celluleId()).isEmpty()) {
            throw new CelluleIntrouvableException(commande.celluleId());
        }
        if (mandatRepository.findById(commande.mandatId()) == null) {
            throw new MandatIntrouvableException(commande.mandatId());
        }

        AffectationEntity entity = new AffectationEntity();
        entity.id = UUID.randomUUID();
        entity.personneId = commande.personneId();
        entity.celluleId = commande.celluleId();
        entity.mandatId = commande.mandatId();
        entity.quotiteEngagement = commande.quotiteEngagement();
        entity.dateDebut = commande.dateDebut();
        entity.dateFin = null;
        entity.statut = StatutAffectation.ACTIVE;
        entity.motifFin = null;

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return AffectationMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public Affectation terminer(TerminerAffectationCommand commande) {
        AffectationEntity entity = repository.findById(commande.affectationId());
        if (entity == null) {
            throw new AffectationIntrouvableException(commande.affectationId());
        }

        entity.statut = StatutAffectation.TERMINEE;
        entity.dateFin = commande.dateFin();
        entity.motifFin = commande.motifFin();

        Tracabilite tracabiliteActuelle = entity.tracabilite.toDomain();
        Tracabilite tracabiliteMiseAJour =
                tracabiliteActuelle.avecModification(commande.demandePar(), commande.motif());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabiliteMiseAJour);

        return AffectationMapper.toDomain(entity);
    }

    @Override
    public Optional<Affectation> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(AffectationMapper::toDomain);
    }

    @Override
    public List<Affectation> listerParPersonne(UUID personneId) {
        return repository.listByPersonne(personneId).stream().map(AffectationMapper::toDomain).toList();
    }

    @Override
    public List<Affectation> listerParCellule(UUID celluleId) {
        return repository.listByCellule(celluleId).stream().map(AffectationMapper::toDomain).toList();
    }
}
