package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerUniteMesureCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.CategorieUniteMesure;
import africa.civitas.egen.modules.business.referencedata.api.domain.UniteMesure;
import africa.civitas.egen.modules.business.referencedata.api.exception.ReferentielConflitException;
import africa.civitas.egen.modules.business.referencedata.api.service.UniteMesureService;
import africa.civitas.egen.modules.business.referencedata.impl.domain.UniteMesureEntity;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.UniteMesureMapper;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.UniteMesureRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UniteMesureServiceImpl implements UniteMesureService {

    @Inject
    UniteMesureRepository repository;

    @Override
    @Transactional
    public UniteMesure enregistrer(EnregistrerUniteMesureCommand commande) {
        if (repository.findByNom(commande.nom()).isPresent()) {
            throw new ReferentielConflitException(
                    "Une Unite de Mesure existe deja pour le nom : " + commande.nom());
        }

        UniteMesureEntity entity = new UniteMesureEntity();
        entity.id = UUID.randomUUID();
        entity.nom = commande.nom();
        entity.symbole = commande.symbole();
        entity.categorie = commande.categorie();
        entity.facteurConversion = commande.facteurConversion();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return UniteMesureMapper.toDomain(entity);
    }

    @Override
    public List<UniteMesure> listerParCategorie(CategorieUniteMesure categorie) {
        return repository.listByCategorie(categorie).stream().map(UniteMesureMapper::toDomain).toList();
    }

    @Override
    public List<UniteMesure> lister() {
        return repository.listAll().stream().map(UniteMesureMapper::toDomain).toList();
    }
}
