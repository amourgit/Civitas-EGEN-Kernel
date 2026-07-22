package africa.civitas.egen.modules.system.identity.api.service;

import africa.civitas.egen.modules.system.identity.api.command.CreerPersonneCommand;
import africa.civitas.egen.modules.system.identity.api.domain.Personne;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrat de service du Systeme Identite pour la Personne. Cette interface est le seul
 * point d'entree qu'un autre systeme du Kernel — ou, plus tard, un module metier — a le
 * droit d'utiliser pour manipuler des Personnes. Aucun appelant ne doit jamais dependre
 * d'identity-provider-keycloak directement.
 */
public interface PersonneService {

    /**
     * Cree une nouvelle Personne. Si {@code identifiantCivilReference} n'est pas fourni
     * dans la commande, un identifiant technique de reference est genere.
     */
    Personne creer(CreerPersonneCommand commande);

    /** Recherche une Personne par son identifiant technique. */
    Optional<Personne> trouverParId(UUID id);

    /** Recherche une Personne par son identifiant civil de reference. */
    Optional<Personne> trouverParIdentifiantCivil(String identifiantCivilReference);

    /** Liste toutes les Personnes actives (non supprimees logiquement). */
    List<Personne> lister();
}
