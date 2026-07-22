package africa.civitas.egen.modules.system.identity.api.command;

import africa.civitas.egen.modules.system.identity.api.domain.TypeChangementIdentite;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Commande d'enregistrement d'une entree dans l'Historique d'Identite d'une Personne. */
public record EnregistrerHistoriqueIdentiteCommand(
        UUID personneId,
        TypeChangementIdentite typeChangement,
        String valeurPrecedente,
        String valeurNouvelle,
        UUID pieceJustificativeRef,
        LocalDate dateEffet,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public EnregistrerHistoriqueIdentiteCommand {
        Objects.requireNonNull(personneId, "personneId ne peut pas etre nul.");
        Objects.requireNonNull(typeChangement, "typeChangement ne peut pas etre nul.");
        if (valeurPrecedente == null || valeurPrecedente.isBlank()) {
            throw new IllegalArgumentException("valeurPrecedente ne peut pas etre vide.");
        }
        if (valeurNouvelle == null || valeurNouvelle.isBlank()) {
            throw new IllegalArgumentException("valeurNouvelle ne peut pas etre vide.");
        }
        if (valeurPrecedente.equals(valeurNouvelle)) {
            throw new IllegalArgumentException(
                    "valeurPrecedente et valeurNouvelle ne peuvent pas etre identiques.");
        }
        Objects.requireNonNull(dateEffet, "dateEffet ne peut pas etre nulle.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
