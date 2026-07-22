package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerLangueCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.Langue;
import africa.civitas.egen.modules.business.referencedata.api.exception.ReferentielConflitException;
import africa.civitas.egen.modules.business.referencedata.api.service.LangueService;
import africa.civitas.egen.modules.business.referencedata.impl.domain.LangueEntity;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.LangueMapper;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.LangueRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LangueServiceImpl implements LangueService {

    @Inject
    LangueRepository repository;

    @Override
    @Transactional
    public Langue enregistrer(EnregistrerLangueCommand commande) {
        if (repository.findByCode(commande.codeIso639()).isPresent()) {
            throw new ReferentielConflitException(
                    "Une Langue existe deja pour le code : " + commande.codeIso639());
        }

        LangueEntity entity = new LangueEntity();
        entity.id = UUID.randomUUID();
        entity.codeIso639 = commande.codeIso639();
        entity.nomOfficiel = commande.nomOfficiel();
        entity.nomNatif = commande.nomNatif();
        entity.sensEcriture = commande.sensEcriture();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return LangueMapper.toDomain(entity);
    }

    @Override
    public Optional<Langue> trouverParCode(String codeIso639) {
        return repository.findByCode(codeIso639).map(LangueMapper::toDomain);
    }

    @Override
    public List<Langue> lister() {
        return repository.listAll().stream().map(LangueMapper::toDomain).toList();
    }
}
