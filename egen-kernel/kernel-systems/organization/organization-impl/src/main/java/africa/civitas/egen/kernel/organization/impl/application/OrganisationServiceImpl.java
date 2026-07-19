package africa.civitas.egen.kernel.organization.impl.application;

import africa.civitas.egen.kernel.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.kernel.organization.api.domain.Organisation;
import africa.civitas.egen.kernel.organization.api.domain.StatutOrganisation;
import africa.civitas.egen.kernel.organization.api.exception.OrganisationnelConflitException;
import africa.civitas.egen.kernel.organization.api.service.OrganisationService;
import africa.civitas.egen.kernel.organization.impl.domain.OrganisationEntity;
import africa.civitas.egen.kernel.organization.impl.infrastructure.OrganisationMapper;
import africa.civitas.egen.kernel.organization.impl.infrastructure.OrganisationRepository;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OrganisationServiceImpl implements OrganisationService {

    @Inject
    OrganisationRepository repository;

    @Override
    @Transactional
    public Organisation creer(CreerOrganisationCommand commande) {
        if (repository.findByIdentifiantJuridique(commande.identifiantJuridique()).isPresent()) {
            throw new OrganisationnelConflitException(
                    "Une Organisation existe deja pour l'identifiant juridique : "
                            + commande.identifiantJuridique());
        }

        OrganisationEntity entity = new OrganisationEntity();
        entity.id = UUID.randomUUID();
        entity.raisonSociale = commande.raisonSociale();
        entity.sigle = commande.sigle();
        entity.typeOrganisation = commande.typeOrganisation();
        entity.secteurActivitePrincipal = commande.secteurActivitePrincipal();
        entity.codePaysRattachementJuridique = commande.codePaysRattachementJuridique();
        entity.identifiantJuridique = commande.identifiantJuridique();
        entity.codeDeviseReference = commande.codeDeviseReference();
        entity.codesLanguesOfficielles = List.copyOf(commande.codesLanguesOfficielles());
        entity.identifiantFuseauHoraireReference = commande.identifiantFuseauHoraireReference();
        entity.modeleSectorielOrigineId = commande.modeleSectorielOrigineId();
        entity.statut = StatutOrganisation.ACTIF;
        entity.dateAdhesion = LocalDate.now();
        entity.urlIdentiteVisuelle = commande.urlIdentiteVisuelle();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return OrganisationMapper.toDomain(entity);
    }

    @Override
    public Optional<Organisation> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(OrganisationMapper::toDomain);
    }

    @Override
    public List<Organisation> lister() {
        return repository.listAll().stream().map(OrganisationMapper::toDomain).toList();
    }
}
