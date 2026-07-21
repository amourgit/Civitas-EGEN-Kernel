package africa.civitas.egen.kernel.organization.impl.application;

import africa.civitas.egen.kernel.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.kernel.organization.api.domain.Cellule;
import africa.civitas.egen.kernel.organization.api.domain.StatutCellule;
import africa.civitas.egen.kernel.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.kernel.organization.api.exception.OrganisationIntrouvableException;
import africa.civitas.egen.kernel.organization.api.exception.OrganisationnelConflitException;
import africa.civitas.egen.kernel.organization.api.exception.TypeCelluleIntrouvableException;
import africa.civitas.egen.kernel.organization.api.service.CelluleService;
import africa.civitas.egen.kernel.organization.impl.domain.CelluleEntity;
import africa.civitas.egen.kernel.organization.impl.domain.TypeCelluleEntity;
import africa.civitas.egen.kernel.organization.impl.infrastructure.CelluleMapper;
import africa.civitas.egen.kernel.organization.impl.infrastructure.CelluleRepository;
import africa.civitas.egen.kernel.organization.impl.infrastructure.FermetureTransitiveCelluleRepository;
import africa.civitas.egen.kernel.organization.impl.infrastructure.OrganisationRepository;
import africa.civitas.egen.kernel.organization.impl.infrastructure.TypeCelluleRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CelluleServiceImpl implements CelluleService {

    @Inject
    CelluleRepository repository;

    @Inject
    OrganisationRepository organisationRepository;

    @Inject
    TypeCelluleRepository typeCelluleRepository;

    @Inject
    FermetureTransitiveCelluleRepository fermetureTransitiveCelluleRepository;

    @Override
    @Transactional
    public Cellule creer(CreerCelluleCommand commande) {
        if (!organisationRepository.findByIdOptional(commande.organisationId()).isPresent()) {
            throw new OrganisationIntrouvableException(commande.organisationId());
        }
        TypeCelluleEntity typeCellule = typeCelluleRepository.findById(commande.typeCelluleId());
        if (typeCellule == null) {
            throw new TypeCelluleIntrouvableException(commande.typeCelluleId());
        }
        if (repository.findByOrganisationAndCodeInterne(commande.organisationId(), commande.codeInterne())
                .isPresent()) {
            throw new OrganisationnelConflitException(
                    "Une Cellule existe deja pour le codeInterne '" + commande.codeInterne()
                            + "' au sein de cette Organisation.");
        }

        if (commande.celluleParentId() != null) {
            CelluleEntity parent = repository.findById(commande.celluleParentId());
            if (parent == null) {
                throw new CelluleIntrouvableException(commande.celluleParentId());
            }
            if (!parent.organisationId.equals(commande.organisationId())) {
                throw new IllegalArgumentException(
                        "celluleParentId doit appartenir a la meme Organisation que la Cellule creee.");
            }
            if (!typeCellule.typesParentsAutorisesIds.isEmpty()
                    && !typeCellule.typesParentsAutorisesIds.contains(parent.typeCelluleId)) {
                throw new IllegalArgumentException(
                        "Le Type de Cellule du parent n'est pas autorise par le Type de Cellule de l'enfant.");
            }
        }

        CelluleEntity entity = new CelluleEntity();
        entity.id = UUID.randomUUID();
        entity.organisationId = commande.organisationId();
        entity.celluleParentId = commande.celluleParentId();
        entity.typeCelluleId = commande.typeCelluleId();
        entity.nom = commande.nom();
        entity.codeInterne = commande.codeInterne();
        entity.description = commande.description();
        entity.codePaysLocalisation = commande.codePaysLocalisation();
        entity.adressePhysique = commande.adressePhysique();
        entity.statut = StatutCellule.ACTIF;
        entity.validDu = commande.validDu();
        entity.validAu = null;

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        fermetureTransitiveCelluleRepository.enregistrerCreationCellule(entity.id, entity.celluleParentId);

        return CelluleMapper.toDomain(entity);
    }

    @Override
    public Optional<Cellule> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(CelluleMapper::toDomain);
    }

    @Override
    public List<Cellule> listerParOrganisation(UUID organisationId) {
        return repository.listByOrganisation(organisationId).stream().map(CelluleMapper::toDomain).toList();
    }

    @Override
    public List<Cellule> listerEtablissements(UUID organisationId) {
        return repository.listEtablissements(organisationId).stream().map(CelluleMapper::toDomain).toList();
    }

    @Override
    public List<Cellule> listerDescendants(UUID celluleId) {
        List<UUID> descendantIds = fermetureTransitiveCelluleRepository.listerDescendantIds(celluleId);
        if (descendantIds.isEmpty()) {
            return List.of();
        }
        return repository.listByIds(descendantIds).stream().map(CelluleMapper::toDomain).toList();
    }

    @Override
    public List<Cellule> listerAncetres(UUID celluleId) {
        List<UUID> ancetreIdsOrdonnes =
                fermetureTransitiveCelluleRepository.listerAncetreIdsOrdonnesParProximite(celluleId);
        if (ancetreIdsOrdonnes.isEmpty()) {
            return List.of();
        }
        Map<UUID, CelluleEntity> entitesParId = repository.listByIds(ancetreIdsOrdonnes).stream()
                .collect(Collectors.toMap(e -> e.id, e -> e));
        return ancetreIdsOrdonnes.stream()
                .map(entitesParId::get)
                .filter(Objects::nonNull)
                .map(CelluleMapper::toDomain)
                .toList();
    }
}
