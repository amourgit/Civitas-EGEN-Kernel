package africa.civitas.egen.kernel.referencedata.api.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.regex.Pattern;

public record EnregistrerDeviseCommand(
        String codeIso4217,
        String symbole,
        String nom,
        int nombreDecimales,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    private static final Pattern CODE_ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    public EnregistrerDeviseCommand {
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
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
