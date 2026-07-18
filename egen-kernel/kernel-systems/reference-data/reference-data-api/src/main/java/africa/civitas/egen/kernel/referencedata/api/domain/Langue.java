package africa.civitas.egen.kernel.referencedata.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Modele de lecture d'une Langue (B4.2).
 *
 * @param codeIso639 code ISO 639-1 ou 639-2 (ex. "fr")
 */
public record Langue(
        UUID id,
        String codeIso639,
        String nomOfficiel,
        String nomNatif,
        SensEcriture sensEcriture,
        Tracabilite tracabilite) {

    private static final Pattern CODE_ISO_639 = Pattern.compile("^[a-z]{2,3}$");

    public Langue {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        if (codeIso639 == null || !CODE_ISO_639.matcher(codeIso639).matches()) {
            throw new IllegalArgumentException(
                    "codeIso639 doit etre 2 ou 3 lettres minuscules, recu : " + codeIso639);
        }
        requireNonBlank(nomOfficiel, "nomOfficiel");
        requireNonBlank(nomNatif, "nomNatif");
        Objects.requireNonNull(sensEcriture, "sensEcriture ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    private static void requireNonBlank(String value, String champ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(champ + " ne peut pas etre vide.");
        }
    }
}
