package africa.civitas.egen.modules.system.identity.keycloak.application;

import africa.civitas.egen.modules.system.identity.api.command.CreerCompteCommand;
import africa.civitas.egen.modules.system.identity.api.domain.Compte;
import africa.civitas.egen.modules.system.identity.api.exception.IdentiteConflitException;
import africa.civitas.egen.modules.system.identity.api.exception.PersonneIntrouvableException;
import africa.civitas.egen.modules.system.identity.api.service.CompteService;
import africa.civitas.egen.modules.system.identity.keycloak.domain.CompteEntity;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.system.identity.keycloak.infrastructure.CompteMapper;
import africa.civitas.egen.modules.system.identity.keycloak.infrastructure.CompteRepository;
import africa.civitas.egen.modules.system.identity.keycloak.infrastructure.PersonneRepository;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CompteServiceImpl implements CompteService {

    @Inject
    CompteRepository repository;

    @Inject
    PersonneRepository personneRepository;

    @Override
    @Transactional
    public Compte creer(CreerCompteCommand commande) {
        if (!personneRepository.findByIdOptional(commande.personneId()).isPresent()) {
            throw new PersonneIntrouvableException(commande.personneId());
        }
        if (repository.findByKeycloakId(commande.keycloakId()).isPresent()) {
            throw new IdentiteConflitException(
                    "Un Compte existe deja pour le keycloakId : " + commande.keycloakId());
        }
        if (repository.findByIdentifiantConnexion(commande.identifiantConnexion()).isPresent()) {
            throw new IdentiteConflitException(
                    "Un Compte existe deja pour l'identifiantConnexion : " + commande.identifiantConnexion());
        }

        CompteEntity entity = new CompteEntity();
        entity.id = UUID.randomUUID();
        entity.keycloakId = commande.keycloakId();
        entity.identifiantConnexion = commande.identifiantConnexion();
        entity.typeCompte = commande.typeCompte();
        entity.statutCompte = africa.civitas.egen.modules.system.identity.api.domain.StatutCompte.ACTIF;
        entity.methodeAuthentification = commande.methodeAuthentification();
        entity.personneId = commande.personneId();
        entity.dateExpirationPrevue = commande.dateExpirationPrevue();

        Tracabilite tracabilite = Tracabilite.initiale(commande.demandePar(), commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return CompteMapper.toDomain(entity);
    }

    @Override
    public Optional<Compte> trouverParId(UUID id) {
        return repository.findByIdOptional(id).map(CompteMapper::toDomain);
    }

    @Override
    public Optional<Compte> trouverParIdentifiantConnexion(String identifiantConnexion) {
        return repository.findByIdentifiantConnexion(identifiantConnexion).map(CompteMapper::toDomain);
    }

    @Override
    public List<Compte> listerParPersonne(UUID personneId) {
        return repository.listByPersonneId(personneId).stream().map(CompteMapper::toDomain).toList();
    }
}
