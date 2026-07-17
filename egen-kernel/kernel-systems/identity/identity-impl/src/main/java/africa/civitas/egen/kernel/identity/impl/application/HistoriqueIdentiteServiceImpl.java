package africa.civitas.egen.kernel.identity.impl.application;

import africa.civitas.egen.kernel.identity.api.command.EnregistrerHistoriqueIdentiteCommand;
import africa.civitas.egen.kernel.identity.api.domain.HistoriqueIdentite;
import africa.civitas.egen.kernel.identity.api.exception.PersonneIntrouvableException;
import africa.civitas.egen.kernel.identity.api.service.HistoriqueIdentiteService;
import africa.civitas.egen.kernel.identity.impl.domain.HistoriqueIdentiteEntity;
import africa.civitas.egen.kernel.identity.impl.domain.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.identity.impl.infrastructure.HistoriqueIdentiteMapper;
import africa.civitas.egen.kernel.identity.impl.infrastructure.HistoriqueIdentiteRepository;
import africa.civitas.egen.kernel.identity.impl.infrastructure.PersonneRepository;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class HistoriqueIdentiteServiceImpl implements HistoriqueIdentiteService {

    @Inject
    HistoriqueIdentiteRepository repository;

    @Inject
    PersonneRepository personneRepository;

    @Override
    @Transactional
    public HistoriqueIdentite enregistrer(EnregistrerHistoriqueIdentiteCommand commande) {
        if (!personneRepository.findByIdOptional(commande.personneId()).isPresent()) {
            throw new PersonneIntrouvableException(commande.personneId());
        }

        HistoriqueIdentiteEntity entity = new HistoriqueIdentiteEntity();
        entity.id = UUID.randomUUID();
        entity.personneId = commande.personneId();
        entity.typeChangement = commande.typeChangement();
        entity.valeurPrecedente = commande.valeurPrecedente();
        entity.valeurNouvelle = commande.valeurNouvelle();
        entity.pieceJustificativeRef = commande.pieceJustificativeRef();
        entity.dateEffet = commande.dateEffet();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return HistoriqueIdentiteMapper.toDomain(entity);
    }

    @Override
    public List<HistoriqueIdentite> pourPersonne(UUID personneId) {
        return repository.listByPersonneIdOrdonneParDateEffet(personneId).stream()
                .map(HistoriqueIdentiteMapper::toDomain)
                .toList();
    }
}
