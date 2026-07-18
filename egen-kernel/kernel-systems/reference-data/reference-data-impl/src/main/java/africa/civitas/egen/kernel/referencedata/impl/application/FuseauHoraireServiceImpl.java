package africa.civitas.egen.kernel.referencedata.impl.application;

import africa.civitas.egen.kernel.referencedata.api.command.EnregistrerFuseauHoraireCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.FuseauHoraire;
import africa.civitas.egen.kernel.referencedata.api.exception.ReferentielConflitException;
import africa.civitas.egen.kernel.referencedata.api.service.FuseauHoraireService;
import africa.civitas.egen.kernel.referencedata.impl.domain.FuseauHoraireEntity;
import africa.civitas.egen.kernel.referencedata.impl.infrastructure.FuseauHoraireMapper;
import africa.civitas.egen.kernel.referencedata.impl.infrastructure.FuseauHoraireRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FuseauHoraireServiceImpl implements FuseauHoraireService {

    @Inject
    FuseauHoraireRepository repository;

    @Override
    @Transactional
    public FuseauHoraire enregistrer(EnregistrerFuseauHoraireCommand commande) {
        if (repository.findByIdentifiantIana(commande.identifiantIana()).isPresent()) {
            throw new ReferentielConflitException(
                    "Un Fuseau Horaire existe deja pour l'identifiant : " + commande.identifiantIana());
        }

        FuseauHoraireEntity entity = new FuseauHoraireEntity();
        entity.id = UUID.randomUUID();
        entity.identifiantIana = commande.identifiantIana();
        entity.libelleUsuel = commande.libelleUsuel();
        entity.decalageUtcReference = commande.decalageUtcReference();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return FuseauHoraireMapper.toDomain(entity);
    }

    @Override
    public Optional<FuseauHoraire> trouverParIdentifiantIana(String identifiantIana) {
        return repository.findByIdentifiantIana(identifiantIana).map(FuseauHoraireMapper::toDomain);
    }

    @Override
    public List<FuseauHoraire> lister() {
        return repository.listAll().stream().map(FuseauHoraireMapper::toDomain).toList();
    }
}
