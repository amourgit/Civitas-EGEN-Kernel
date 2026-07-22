package africa.civitas.egen.modules.system.identity.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'une entree de l'Historique d'Identite — la trace d'un
 * changement d'etat civil ou administratif d'une Personne. Une fois cree, une entree
 * n'est jamais modifiee : c'est une entree d'historique, immuable par nature (seule
 * une suppression logique via la Tracabilite reste possible, en cas d'erreur de
 * saisie averee).
 *
 * @param id identifiant technique unique
 * @param personneId la Personne concernee par ce changement
 * @param typeChangement nature du changement
 * @param valeurPrecedente valeur avant le changement
 * @param valeurNouvelle valeur apres le changement
 * @param pieceJustificativeRef reference vers la piece justificative associee (absente si non fournie)
 * @param dateEffet date a laquelle le changement prend effet
 * @param tracabilite le Socle de Traçabilite complet
 */
public record HistoriqueIdentite(
        UUID id,
        UUID personneId,
        TypeChangementIdentite typeChangement,
        String valeurPrecedente,
        String valeurNouvelle,
        UUID pieceJustificativeRef,
        LocalDate dateEffet,
        Tracabilite tracabilite) {

    public HistoriqueIdentite {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(personneId, "personneId ne peut pas etre nul.");
        Objects.requireNonNull(typeChangement, "typeChangement ne peut pas etre nul.");
        requireNonBlank(valeurPrecedente, "valeurPrecedente");
        requireNonBlank(valeurNouvelle, "valeurNouvelle");
        Objects.requireNonNull(dateEffet, "dateEffet ne peut pas etre nulle.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
        if (valeurPrecedente.equals(valeurNouvelle)) {
            throw new IllegalArgumentException(
                    "valeurPrecedente et valeurNouvelle ne peuvent pas etre identiques : "
                            + "ce ne serait pas un changement.");
        }
    }

    private static void requireNonBlank(String value, String champ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(champ + " ne peut pas etre vide.");
        }
    }

    /** @return la reference de piece justificative, si elle a ete fournie. */
    public Optional<UUID> pieceJustificative() {
        return Optional.ofNullable(pieceJustificativeRef);
    }
}
