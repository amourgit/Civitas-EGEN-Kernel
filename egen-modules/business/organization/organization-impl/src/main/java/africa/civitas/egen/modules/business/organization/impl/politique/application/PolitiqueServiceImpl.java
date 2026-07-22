package africa.civitas.egen.modules.business.organization.impl.politique.application;

import africa.civitas.egen.modules.business.organization.api.politique.command.CreerPolitiqueCommand;
import africa.civitas.egen.modules.business.organization.api.politique.domain.Politique;
import africa.civitas.egen.modules.business.organization.api.politique.domain.StatutPolitique;
import africa.civitas.egen.modules.business.organization.api.politique.domain.ValeurEffective;
import africa.civitas.egen.modules.business.organization.api.politique.exception.PolitiqueIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.politique.service.PolitiqueService;
import africa.civitas.egen.modules.business.organization.impl.politique.domain.DerogationEntity;
import africa.civitas.egen.modules.business.organization.impl.politique.domain.PolitiqueEntity;
import africa.civitas.egen.modules.business.organization.impl.politique.infrastructure.DerogationRepository;
import africa.civitas.egen.modules.business.organization.impl.politique.infrastructure.PolitiqueMapper;
import africa.civitas.egen.modules.business.organization.impl.politique.infrastructure.PolitiqueRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.business.organization.api.domain.Cellule;
import africa.civitas.egen.modules.business.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.exception.OrganisationIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.service.CelluleService;
import africa.civitas.egen.modules.business.organization.api.service.OrganisationService;
import africa.civitas.egen.kernel.sdk.contexte.ContexteNature;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation du Systeme Politique. {@link #resoudrePourCellule} est la
 * traduction directe de la regle d'architecture posee des la conception d'A2 :
 * "la Derogation la plus proche dans l'arbre l'emporte toujours."
 */
@ApplicationScoped
public class PolitiqueServiceImpl implements PolitiqueService {

    @Inject
    PolitiqueRepository repository;

    @Inject
    DerogationRepository derogationRepository;

    @Inject
    OrganisationService organisationService;

    @Inject
    CelluleService celluleService;

    @Override
    @Transactional
    public Politique creer(CreerPolitiqueCommand commande) {
        if (commande.contexteNature() == ContexteNature.ORGANISATION) {
            if (organisationService.trouverParId(commande.contexteId()).isEmpty()) {
                throw new OrganisationIntrouvableException(commande.contexteId());
            }
        } else {
            if (celluleService.trouverParId(commande.contexteId()).isEmpty()) {
                throw new CelluleIntrouvableException(commande.contexteId());
            }
        }

        PolitiqueEntity entity = new PolitiqueEntity();
        entity.id = UUID.randomUUID();
        entity.contexteId = commande.contexteId();
        entity.contexteNature = commande.contexteNature();
        entity.domaine = commande.domaine();
        entity.nomRegle = commande.nomRegle();
        entity.valeur = commande.valeur();
        entity.statut = StatutPolitique.ACTIVE;
        entity.dateEntreeVigueur = commande.dateEntreeVigueur();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return PolitiqueMapper.toDomain(entity);
    }

    @Override
    public Optional<Politique> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(PolitiqueMapper::toDomain);
    }

    @Override
    public List<Politique> listerParContexte(UUID contexteId) {
        return repository.listByContexte(contexteId).stream().map(PolitiqueMapper::toDomain).toList();
    }

    @Override
    public ValeurEffective resoudrePourCellule(UUID politiqueId, UUID celluleId) {
        PolitiqueEntity politique = repository.findById(politiqueId);
        if (politique == null) {
            throw new PolitiqueIntrouvableException(politiqueId);
        }

        List<UUID> chaineDeRemontee = new ArrayList<>();
        chaineDeRemontee.add(celluleId);
        for (Cellule ancetre : celluleService.listerAncetres(celluleId)) {
            chaineDeRemontee.add(ancetre.id());
        }

        LocalDate aujourdhui = LocalDate.now();
        for (UUID celluleCourante : chaineDeRemontee) {
            Optional<DerogationEntity> derogation =
                    derogationRepository.findByPolitiqueAndCellule(politiqueId, celluleCourante);
            if (derogation.isPresent() && estEnVigueur(derogation.get(), aujourdhui)) {
                return new ValeurEffective(derogation.get().valeur, derogation.get().id);
            }
        }

        return new ValeurEffective(politique.valeur, null);
    }

    private static boolean estEnVigueur(DerogationEntity derogation, LocalDate date) {
        boolean apresDebut = !date.isBefore(derogation.dateEntreeVigueur);
        boolean avantFin = derogation.dateFin == null || !date.isAfter(derogation.dateFin);
        return apresDebut && avantFin;
    }
}
