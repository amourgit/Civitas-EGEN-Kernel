package africa.civitas.egen.modules.system.identity.keycloak.application;

import africa.civitas.egen.modules.system.identity.api.command.CreerPersonneCommand;
import africa.civitas.egen.modules.system.identity.api.domain.Personne;
import africa.civitas.egen.modules.system.identity.api.exception.IdentiteConflitException;
import africa.civitas.egen.modules.system.identity.api.service.PersonneService;
import africa.civitas.egen.modules.system.identity.keycloak.domain.PersonneEntity;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.system.identity.keycloak.infrastructure.PersonneMapper;
import africa.civitas.egen.modules.system.identity.keycloak.infrastructure.PersonneRepository;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PersonneServiceImpl implements PersonneService {

    @Inject
    PersonneRepository repository;

    @Override
    @Transactional
    public Personne creer(CreerPersonneCommand commande) {
        String identifiantCivilReference = commande.identifiantCivilReference();
        if (identifiantCivilReference == null || identifiantCivilReference.isBlank()) {
            identifiantCivilReference = "GEN-" + UUID.randomUUID();
        } else if (repository.findByIdentifiantCivilReference(identifiantCivilReference).isPresent()) {
            throw new IdentiteConflitException(
                    "Une Personne existe deja avec l'identifiant civil de reference : "
                            + identifiantCivilReference);
        }

        PersonneEntity entity = new PersonneEntity();
        entity.id = UUID.randomUUID();
        entity.identifiantCivilReference = identifiantCivilReference;
        entity.nomNaissance = commande.nomNaissance();
        entity.nomUsage = commande.nomUsage();
        entity.prenoms = List.copyOf(commande.prenoms());
        entity.dateNaissance = commande.dateNaissance();
        entity.lieuNaissance = commande.lieuNaissance();
        entity.genreDeclare = commande.genreDeclare();
        entity.codesPaysNationalite = List.copyOf(commande.codesPaysNationalite());
        entity.codesLanguePreferee = commande.codesLanguePreferee() == null
                ? List.of() : List.copyOf(commande.codesLanguePreferee());
        entity.statutVital = commande.statutVital();
        entity.telephonePrincipal = commande.telephonePrincipal();
        entity.emailSecours = commande.emailSecours();
        entity.photoReferenceUrl = commande.photoReferenceUrl();
        entity.statutVerificationIdentite =
                africa.civitas.egen.modules.system.identity.api.domain.StatutVerificationIdentite.NON_VERIFIEE;

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return PersonneMapper.toDomain(entity);
    }

    @Override
    public Optional<Personne> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(PersonneMapper::toDomain);
    }

    @Override
    public Optional<Personne> trouverParIdentifiantCivil(String identifiantCivilReference) {
        return repository.findByIdentifiantCivilReference(identifiantCivilReference)
                .map(PersonneMapper::toDomain);
    }

    @Override
    public List<Personne> lister() {
        return repository.listActives().stream().map(PersonneMapper::toDomain).toList();
    }
}
