package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.CreerModeleSectorielCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.ModeleSectoriel;
import africa.civitas.egen.modules.business.referencedata.api.domain.StatutModeleSectoriel;
import africa.civitas.egen.modules.business.referencedata.api.service.ModeleSectorielService;
import africa.civitas.egen.modules.business.referencedata.impl.domain.ModeleSectorielEntity;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.ModeleSectorielMapper;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.ModeleSectorielRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ModeleSectorielServiceImpl implements ModeleSectorielService {

    @Inject
    ModeleSectorielRepository repository;

    @Override
    @Transactional
    public ModeleSectoriel creer(CreerModeleSectorielCommand commande) {
        ModeleSectorielEntity entity = new ModeleSectorielEntity();
        entity.id = UUID.randomUUID();
        entity.nomSecteur = commande.nomSecteur();
        entity.description = commande.description();
        entity.versionModele = commande.versionModele();
        entity.statut = StatutModeleSectoriel.ACTIF;

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return ModeleSectorielMapper.toDomain(entity);
    }

    @Override
    public Optional<ModeleSectoriel> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(ModeleSectorielMapper::toDomain);
    }

    @Override
    public List<ModeleSectoriel> listerActifs() {
        return repository.listByStatut(StatutModeleSectoriel.ACTIF).stream()
                .map(ModeleSectorielMapper::toDomain)
                .toList();
    }
}
