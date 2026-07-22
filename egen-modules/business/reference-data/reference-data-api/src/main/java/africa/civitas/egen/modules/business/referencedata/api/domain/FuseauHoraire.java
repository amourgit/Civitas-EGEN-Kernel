package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Modele de lecture d'un Fuseau Horaire (B4.4).
 *
 * @param identifiantIana identifiant de la base de donnees IANA (ex. "Africa/Libreville")
 * @param decalageUtcReference decalage UTC de reference, format "+HH:MM" ou "-HH:MM"
 */
public record FuseauHoraire(
        UUID id,
        String identifiantIana,
        String libelleUsuel,
        String decalageUtcReference,
        Tracabilite tracabilite) {

    private static final Pattern DECALAGE = Pattern.compile("^[+-][0-9]{2}:[0-9]{2}$");

    public FuseauHoraire {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        if (identifiantIana == null || identifiantIana.isBlank()) {
            throw new IllegalArgumentException("identifiantIana ne peut pas etre vide.");
        }
        if (libelleUsuel == null || libelleUsuel.isBlank()) {
            throw new IllegalArgumentException("libelleUsuel ne peut pas etre vide.");
        }
        if (decalageUtcReference == null || !DECALAGE.matcher(decalageUtcReference).matches()) {
            throw new IllegalArgumentException(
                    "decalageUtcReference doit suivre le format '+HH:MM' ou '-HH:MM', recu : "
                            + decalageUtcReference);
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }
}
