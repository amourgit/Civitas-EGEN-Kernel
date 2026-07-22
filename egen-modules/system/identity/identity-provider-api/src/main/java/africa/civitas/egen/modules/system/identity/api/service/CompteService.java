package africa.civitas.egen.modules.system.identity.api.service;

import africa.civitas.egen.modules.system.identity.api.command.CreerCompteCommand;
import africa.civitas.egen.modules.system.identity.api.domain.Compte;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Contrat de service du Systeme Identite pour le Compte. */
public interface CompteService {

    /** Cree un nouveau Compte, rattache a une Personne existante. */
    Compte creer(CreerCompteCommand commande);

    /** Recherche un Compte par son identifiant technique. */
    Optional<Compte> trouverParId(UUID id);

    /** Recherche un Compte par son identifiant de connexion. */
    Optional<Compte> trouverParIdentifiantConnexion(String identifiantConnexion);

    /** Liste tous les Comptes rattaches a une Personne donnee. */
    List<Compte> listerParPersonne(UUID personneId);
}
