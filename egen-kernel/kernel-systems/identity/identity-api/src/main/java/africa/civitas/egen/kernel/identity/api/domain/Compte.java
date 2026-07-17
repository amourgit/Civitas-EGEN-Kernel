package africa.civitas.egen.kernel.identity.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Modele de lecture d'un Compte — la materialisation technique de l'identite pour
 * l'authentification. Toujours rattache a exactement une {@link Personne}, jamais
 * l'inverse : une Personne peut, en revanche, n'avoir aucun Compte (cas d'une
 * Personne connue de l'annuaire mais jamais invitee a se connecter).
 *
 * @param id identifiant technique unique
 * @param keycloakId identifiant du sujet cote fournisseur d'identite (Keycloak)
 * @param identifiantConnexion identifiant de connexion (nom d'utilisateur ou email)
 * @param typeCompte type du compte
 * @param statutCompte statut courant du compte
 * @param derniereConnexionReussie horodatage de la derniere connexion reussie (nul si jamais connecte)
 * @param methodeAuthentification methode d'authentification effective
 * @param personneId la Personne rattachee — obligatoire, un Compte ne peut jamais exister orphelin
 * @param dateExpirationPrevue date d'expiration prevue, pertinente pour les comptes temporaires (nul sinon)
 * @param tracabilite le Socle de Traçabilite complet
 */
public record Compte(
        UUID id,
        String keycloakId,
        String identifiantConnexion,
        TypeCompte typeCompte,
        StatutCompte statutCompte,
        Instant derniereConnexionReussie,
        MethodeAuthentification methodeAuthentification,
        UUID personneId,
        Instant dateExpirationPrevue,
        Tracabilite tracabilite) {

    public Compte {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        requireNonBlank(keycloakId, "keycloakId");
        requireNonBlank(identifiantConnexion, "identifiantConnexion");
        Objects.requireNonNull(typeCompte, "typeCompte ne peut pas etre nul.");
        Objects.requireNonNull(statutCompte, "statutCompte ne peut pas etre nul.");
        Objects.requireNonNull(methodeAuthentification, "methodeAuthentification ne peut pas etre nulle.");
        Objects.requireNonNull(personneId, "personneId ne peut pas etre nul : "
                + "un Compte ne peut jamais exister sans Personne rattachee.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
        if (typeCompte == TypeCompte.DELEGUE_TEMPORAIRE && dateExpirationPrevue == null) {
            throw new IllegalArgumentException(
                    "Un Compte de type DELEGUE_TEMPORAIRE doit porter une dateExpirationPrevue.");
        }
    }

    private static void requireNonBlank(String value, String champ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(champ + " ne peut pas etre vide.");
        }
    }

    /** @return vrai si le compte peut etre utilise pour s'authentifier actuellement. */
    public boolean estUtilisable() {
        return statutCompte == StatutCompte.ACTIF
                && (dateExpirationPrevue == null || dateExpirationPrevue.isAfter(Instant.now()));
    }
}
