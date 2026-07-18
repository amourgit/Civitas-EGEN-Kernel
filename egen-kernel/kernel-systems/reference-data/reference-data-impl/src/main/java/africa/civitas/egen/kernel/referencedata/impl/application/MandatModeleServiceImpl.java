package africa.civitas.egen.kernel.referencedata.impl.application;

import africa.civitas.egen.kernel.referencedata.api.command.AjouterMandatModeleCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.MandatModele;
import africa.civitas.egen.kernel.referencedata.api.exception.ModeleSectorielIntrouvableException;
import africa.civitas.egen.kernel.referencedata.api.service.MandatModeleService;
import africa.civitas.egen.kernel.referencedata.impl.domain.MandatModeleEntity;
import africa.civitas.egen.kernel.referencedata.impl.infrastructure.MandatModeleMapper;
import africa.civitas.egen.kernel.referencedata.impl.infrastructure.MandatModeleRepository;
import africa.civitas.egen.kernel.referencedata.impl.infrastructure.ModeleSectorielRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MandatModeleServiceImpl implements MandatModeleService {

    @Inject
    MandatModeleRepository repository;

    @Inject
    ModeleSectorielRepository modeleSectorielRepository;

    @Override
    @Transactional
    public MandatModele ajouter(AjouterMandatModeleCommand commande) {
        if (!modeleSectorielRepository.findByIdOptional(commande.modeleSectorialId()).isPresent()) {
            throw new ModeleSectorielIntrouvableException(commande.modeleSectorialId());
        }

        MandatModeleEntity entity = new MandatModeleEntity();
        entity.id = UUID.randomUUID();
        entity.modeleSectorialId = commande.modeleSectorialId();
        entity.libelleSuggere = commande.libelleSuggere();
        entity.niveauAutoriteIndicatif = commande.niveauAutoriteIndicatif();
        entity.descriptionResponsabilites = commande.descriptionResponsabilites();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return MandatModeleMapper.toDomain(entity);
    }

    @Override
    public List<MandatModele> listerParModeleSectoriel(UUID modeleSectorialId) {
        return repository.listByModeleSectoriel(modeleSectorialId).stream()
                .map(MandatModeleMapper::toDomain)
                .toList();
    }
}
