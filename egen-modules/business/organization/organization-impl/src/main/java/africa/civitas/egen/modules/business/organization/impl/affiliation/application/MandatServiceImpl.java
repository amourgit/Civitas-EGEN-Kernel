package africa.civitas.egen.modules.business.organization.impl.affiliation.application;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerMandatCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Mandat;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.StatutMandat;
import africa.civitas.egen.modules.business.organization.api.affiliation.exception.LexiqueDesMandatsIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.affiliation.exception.MandatIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.affiliation.service.MandatService;
import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.MandatEntity;
import africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure.LexiqueDesMandatsRepository;
import africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure.MandatMapper;
import africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure.MandatRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MandatServiceImpl implements MandatService {

    @Inject
    MandatRepository repository;

    @Inject
    LexiqueDesMandatsRepository lexiqueRepository;

    @Override
    @Transactional
    public Mandat creer(CreerMandatCommand commande) {
        if (!lexiqueRepository.findByIdOptional(commande.lexiqueMandatsId()).isPresent()) {
            throw new LexiqueDesMandatsIntrouvableException(commande.lexiqueMandatsId());
        }
        for (UUID superviseId : commande.mandatsSupervisesIds()) {
            MandatEntity supervise = repository.findById(superviseId);
            if (supervise == null || !supervise.lexiqueMandatsId.equals(commande.lexiqueMandatsId())) {
                throw new MandatIntrouvableException(superviseId);
            }
        }

        MandatEntity entity = new MandatEntity();
        entity.id = UUID.randomUUID();
        entity.lexiqueMandatsId = commande.lexiqueMandatsId();
        entity.libelle = commande.libelle();
        entity.description = commande.description();
        entity.niveauAutoriteIndicatif = commande.niveauAutoriteIndicatif();
        entity.mandatsSupervisesIds = List.copyOf(commande.mandatsSupervisesIds());
        entity.mandatModeleOrigineId = commande.mandatModeleOrigineId();
        entity.statut = StatutMandat.ACTIF;

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return MandatMapper.toDomain(entity);
    }

    @Override
    public Optional<Mandat> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(MandatMapper::toDomain);
    }

    @Override
    public List<Mandat> listerParLexique(UUID lexiqueMandatsId) {
        return repository.listByLexique(lexiqueMandatsId).stream().map(MandatMapper::toDomain).toList();
    }
}
