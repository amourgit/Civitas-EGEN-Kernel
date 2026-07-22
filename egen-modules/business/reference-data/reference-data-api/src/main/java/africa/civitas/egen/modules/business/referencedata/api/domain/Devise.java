package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Modele de lecture d'une Devise (B4.3).
 *
 * @param codeIso4217 code ISO 4217 (ex. "XAF")
 * @param nombreDecimales nombre de decimales usuelles (ex. 0 pour XAF, 2 pour USD)
 */
public record Devise(
        UUID id,
        String codeIso4217,
        String symbole,
        String nom,
        int nombreDecimales,
        Tracabilite tracabilite) {

    private static final Pattern CODE_ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    public Devise {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        if (codeIso4217 == null || !CODE_ISO_4217.matcher(codeIso4217).matches()) {
            throw new IllegalArgumentException(
                    "codeIso4217 doit etre 3 lettres majuscules, recu : " + codeIso4217);
        }
        if (symbole == null || symbole.isBlank()) {
            throw new IllegalArgumentException("symbole ne peut pas etre vide.");
        }
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("nom ne peut pas etre vide.");
        }
        if (nombreDecimales < 0 || nombreDecimales > 4) {
            throw new IllegalArgumentException(
                    "nombreDecimales doit etre compris entre 0 et 4, recu : " + nombreDecimales);
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }
}
