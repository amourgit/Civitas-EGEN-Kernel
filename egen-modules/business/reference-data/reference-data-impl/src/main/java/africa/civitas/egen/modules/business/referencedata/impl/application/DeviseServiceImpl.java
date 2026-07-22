package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerDeviseCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.Devise;
import africa.civitas.egen.modules.business.referencedata.api.exception.ReferentielConflitException;
import africa.civitas.egen.modules.business.referencedata.api.service.DeviseService;
import africa.civitas.egen.modules.business.referencedata.impl.domain.DeviseEntity;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.DeviseMapper;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.DeviseRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DeviseServiceImpl implements DeviseService {

    @Inject
    DeviseRepository repository;

    @Override
    @Transactional
    public Devise enregistrer(EnregistrerDeviseCommand commande) {
        if (repository.findByCode(commande.codeIso4217()).isPresent()) {
            throw new ReferentielConflitException(
                    "Une Devise existe deja pour le code : " + commande.codeIso4217());
        }

        DeviseEntity entity = new DeviseEntity();
        entity.id = UUID.randomUUID();
        entity.codeIso4217 = commande.codeIso4217();
        entity.symbole = commande.symbole();
        entity.nom = commande.nom();
        entity.nombreDecimales = commande.nombreDecimales();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return DeviseMapper.toDomain(entity);
    }

    @Override
    public Optional<Devise> trouverParCode(String codeIso4217) {
        return repository.findByCode(codeIso4217).map(DeviseMapper::toDomain);
    }

    @Override
    public List<Devise> lister() {
        return repository.listAll().stream().map(DeviseMapper::toDomain).toList();
    }
}
