package africa.civitas.egen.kernel.organization.impl.application;

import africa.civitas.egen.kernel.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.kernel.organization.api.domain.StatutTypeCellule;
import africa.civitas.egen.kernel.organization.api.domain.TypeCellule;
import africa.civitas.egen.kernel.organization.api.exception.LexiqueIntrouvableException;
import africa.civitas.egen.kernel.organization.api.exception.TypeCelluleIntrouvableException;
import africa.civitas.egen.kernel.organization.api.service.TypeCelluleService;
import africa.civitas.egen.kernel.organization.impl.domain.TypeCelluleEntity;
import africa.civitas.egen.kernel.organization.impl.infrastructure.LexiqueOrganisationnelRepository;
import africa.civitas.egen.kernel.organization.impl.infrastructure.TypeCelluleMapper;
import africa.civitas.egen.kernel.organization.impl.infrastructure.TypeCelluleRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TypeCelluleServiceImpl implements TypeCelluleService {

    @Inject
    TypeCelluleRepository repository;

    @Inject
    LexiqueOrganisationnelRepository lexiqueRepository;

    @Override
    @Transactional
    public TypeCellule creer(CreerTypeCelluleCommand commande) {
        if (!lexiqueRepository.findByIdOptional(commande.lexiqueId()).isPresent()) {
            throw new LexiqueIntrouvableException(commande.lexiqueId());
        }
        for (UUID parentId : commande.typesParentsAutorisesIds()) {
            TypeCelluleEntity parent = repository.findById(parentId);
            if (parent == null || !parent.lexiqueId.equals(commande.lexiqueId())) {
                throw new TypeCelluleIntrouvableException(parentId);
            }
        }

        TypeCelluleEntity entity = new TypeCelluleEntity();
        entity.id = UUID.randomUUID();
        entity.lexiqueId = commande.lexiqueId();
        entity.libelle = commande.libelle();
        entity.description = commande.description();
        entity.niveauIndicatif = commande.niveauIndicatif();
        entity.typesParentsAutorisesIds = List.copyOf(commande.typesParentsAutorisesIds());
        entity.typeCelluleModeleOrigineId = commande.typeCelluleModeleOrigineId();
        entity.statut = StatutTypeCellule.ACTIF;

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return TypeCelluleMapper.toDomain(entity);
    }

    @Override
    public Optional<TypeCellule> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(TypeCelluleMapper::toDomain);
    }

    @Override
    public List<TypeCellule> listerParLexique(UUID lexiqueId) {
        return repository.listByLexique(lexiqueId).stream().map(TypeCelluleMapper::toDomain).toList();
    }
}
