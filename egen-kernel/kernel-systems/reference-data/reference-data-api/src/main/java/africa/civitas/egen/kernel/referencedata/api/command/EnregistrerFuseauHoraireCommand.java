package africa.civitas.egen.kernel.referencedata.api.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.regex.Pattern;

public record EnregistrerFuseauHoraireCommand(
        String identifiantIana,
        String libelleUsuel,
        String decalageUtcReference,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    private static final Pattern DECALAGE = Pattern.compile("^[+-][0-9]{2}:[0-9]{2}$");

    public EnregistrerFuseauHoraireCommand {
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
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
