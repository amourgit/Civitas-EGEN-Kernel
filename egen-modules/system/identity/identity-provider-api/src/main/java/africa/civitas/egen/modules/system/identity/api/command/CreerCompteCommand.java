package africa.civitas.egen.modules.system.identity.api.command;

import africa.civitas.egen.modules.system.identity.api.domain.MethodeAuthentification;
import africa.civitas.egen.modules.system.identity.api.domain.TypeCompte;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Commande de creation d'un Compte, toujours rattache a une Personne existante. */
public record CreerCompteCommand(
        String keycloakId,
        String identifiantConnexion,
        TypeCompte typeCompte,
        MethodeAuthentification methodeAuthentification,
        UUID personneId,
        Instant dateExpirationPrevue,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerCompteCommand {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalArgumentException("keycloakId ne peut pas etre vide.");
        }
        if (identifiantConnexion == null || identifiantConnexion.isBlank()) {
            throw new IllegalArgumentException("identifiantConnexion ne peut pas etre vide.");
        }
        Objects.requireNonNull(typeCompte, "typeCompte ne peut pas etre nul.");
        Objects.requireNonNull(methodeAuthentification, "methodeAuthentification ne peut pas etre nulle.");
        Objects.requireNonNull(personneId, "personneId ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
        if (typeCompte == TypeCompte.DELEGUE_TEMPORAIRE && dateExpirationPrevue == null) {
            throw new IllegalArgumentException(
                    "Un Compte de type DELEGUE_TEMPORAIRE doit porter une dateExpirationPrevue.");
        }
    }
}
