package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.AjouterTypeCelluleModeleCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.TypeCelluleModele;
import africa.civitas.egen.modules.business.referencedata.api.exception.ModeleSectorielIntrouvableException;
import africa.civitas.egen.modules.business.referencedata.api.service.TypeCelluleModeleService;
import africa.civitas.egen.modules.business.referencedata.impl.domain.TypeCelluleModeleEntity;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.ModeleSectorielRepository;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.TypeCelluleModeleMapper;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.TypeCelluleModeleRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TypeCelluleModeleServiceImpl implements TypeCelluleModeleService {

    @Inject
    TypeCelluleModeleRepository repository;

    @Inject
    ModeleSectorielRepository modeleSectorielRepository;

    @Override
    @Transactional
    public TypeCelluleModele ajouter(AjouterTypeCelluleModeleCommand commande) {
        if (!modeleSectorielRepository.findByIdOptional(commande.modeleSectorialId()).isPresent()) {
            throw new ModeleSectorielIntrouvableException(commande.modeleSectorialId());
        }
        if (commande.typeParentSuggereId() != null) {
            TypeCelluleModeleEntity parent = repository.findById(commande.typeParentSuggereId());
            if (parent == null || !parent.modeleSectorialId.equals(commande.modeleSectorialId())) {
                throw new IllegalArgumentException(
                        "typeParentSuggereId doit referencer un Type de Cellule Modele existant "
                                + "du meme Modele Sectoriel.");
            }
        }

        TypeCelluleModeleEntity entity = new TypeCelluleModeleEntity();
        entity.id = UUID.randomUUID();
        entity.modeleSectorialId = commande.modeleSectorialId();
        entity.libelleMetierSuggere = commande.libelleMetierSuggere();
        entity.niveauIndicatifSuggere = commande.niveauIndicatifSuggere();
        entity.typeParentSuggereId = commande.typeParentSuggereId();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return TypeCelluleModeleMapper.toDomain(entity);
    }

    @Override
    public List<TypeCelluleModele> listerParModeleSectoriel(UUID modeleSectorialId) {
        return repository.listByModeleSectoriel(modeleSectorialId).stream()
                .map(TypeCelluleModeleMapper::toDomain)
                .toList();
    }
}
