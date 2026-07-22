package africa.civitas.egen.modules.business.organization.impl.affiliation.application;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerDelegationCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Delegation;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.StatutDelegation;
import africa.civitas.egen.modules.business.organization.api.affiliation.exception.AffectationIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.affiliation.service.DelegationService;
import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.DelegationEntity;
import africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure.AffectationRepository;
import africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure.DelegationMapper;
import africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure.DelegationRepository;
import africa.civitas.egen.modules.system.identity.api.exception.PersonneIntrouvableException;
import africa.civitas.egen.modules.system.identity.api.service.PersonneService;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DelegationServiceImpl implements DelegationService {

    @Inject
    DelegationRepository repository;

    @Inject
    AffectationRepository affectationRepository;

    @Inject
    PersonneService personneService;

    @Override
    @Transactional
    public Delegation creer(CreerDelegationCommand commande) {
        if (affectationRepository.findById(commande.affectationOrigineId()) == null) {
            throw new AffectationIntrouvableException(commande.affectationOrigineId());
        }
        if (personneService.trouverParId(commande.personneBeneficiaireId()).isEmpty()) {
            throw new PersonneIntrouvableException(commande.personneBeneficiaireId());
        }

        DelegationEntity entity = new DelegationEntity();
        entity.id = UUID.randomUUID();
        entity.affectationOrigineId = commande.affectationOrigineId();
        entity.personneBeneficiaireId = commande.personneBeneficiaireId();
        entity.etendue = commande.etendue();
        entity.actionsCouvertes = commande.actionsCouvertes();
        entity.dateDebut = commande.dateDebut();
        entity.dateFin = commande.dateFin();
        entity.motif = commande.motif();
        entity.statut = commande.dateDebut().isAfter(LocalDate.now())
                ? StatutDelegation.PROGRAMMEE : StatutDelegation.ACTIVE;

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return DelegationMapper.toDomain(entity);
    }

    @Override
    public List<Delegation> listerParAffectationOrigine(UUID affectationOrigineId) {
        return repository.listByAffectationOrigine(affectationOrigineId).stream()
                .map(DelegationMapper::toDomain)
                .toList();
    }

    @Override
    public List<Delegation> listerParPersonneBeneficiaire(UUID personneBeneficiaireId) {
        return repository.listByPersonneBeneficiaire(personneBeneficiaireId).stream()
                .map(DelegationMapper::toDomain)
                .toList();
    }
}
