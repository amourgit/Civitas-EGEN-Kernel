package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerPaysCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.Pays;
import africa.civitas.egen.modules.business.referencedata.api.exception.ReferentielConflitException;
import africa.civitas.egen.modules.business.referencedata.api.service.PaysService;
import africa.civitas.egen.modules.business.referencedata.impl.domain.PaysEntity;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.PaysMapper;
import africa.civitas.egen.modules.business.referencedata.impl.infrastructure.PaysRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PaysServiceImpl implements PaysService {

    @Inject
    PaysRepository repository;

    @Override
    @Transactional
    public Pays enregistrer(EnregistrerPaysCommand commande) {
        if (repository.findByCodeAlpha2(commande.codeAlpha2()).isPresent()) {
            throw new ReferentielConflitException(
                    "Un Pays existe deja pour le code : " + commande.codeAlpha2());
        }

        PaysEntity entity = new PaysEntity();
        entity.id = UUID.randomUUID();
        entity.codeAlpha2 = commande.codeAlpha2();
        entity.codeAlpha3 = commande.codeAlpha3();
        entity.nomOfficiel = commande.nomOfficiel();
        entity.nomUsuel = commande.nomUsuel();
        entity.indicatifTelephonique = commande.indicatifTelephonique();
        entity.codeDeviseParDefaut = commande.codeDeviseParDefaut();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return PaysMapper.toDomain(entity);
    }

    @Override
    public Optional<Pays> trouverParCodeAlpha2(String codeAlpha2) {
        return repository.findByCodeAlpha2(codeAlpha2).map(PaysMapper::toDomain);
    }

    @Override
    public List<Pays> lister() {
        return repository.listAll().stream().map(PaysMapper::toDomain).toList();
    }
}
