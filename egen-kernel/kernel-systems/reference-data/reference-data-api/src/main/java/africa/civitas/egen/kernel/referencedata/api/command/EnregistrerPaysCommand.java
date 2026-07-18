package africa.civitas.egen.kernel.referencedata.api.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.regex.Pattern;

public record EnregistrerPaysCommand(
        String codeAlpha2,
        String codeAlpha3,
        String nomOfficiel,
        String nomUsuel,
        String indicatifTelephonique,
        String codeDeviseParDefaut,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    private static final Pattern ALPHA2 = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern ALPHA3 = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern INDICATIF = Pattern.compile("^\\+[0-9]{1,4}$");
    private static final Pattern DEVISE = Pattern.compile("^[A-Z]{3}$");

    public EnregistrerPaysCommand {
        if (codeAlpha2 == null || !ALPHA2.matcher(codeAlpha2).matches()) {
            throw new IllegalArgumentException("codeAlpha2 doit etre 2 lettres majuscules, recu : " + codeAlpha2);
        }
        if (codeAlpha3 == null || !ALPHA3.matcher(codeAlpha3).matches()) {
            throw new IllegalArgumentException("codeAlpha3 doit etre 3 lettres majuscules, recu : " + codeAlpha3);
        }
        if (nomOfficiel == null || nomOfficiel.isBlank()) {
            throw new IllegalArgumentException("nomOfficiel ne peut pas etre vide.");
        }
        if (nomUsuel == null || nomUsuel.isBlank()) {
            throw new IllegalArgumentException("nomUsuel ne peut pas etre vide.");
        }
        if (indicatifTelephonique == null || !INDICATIF.matcher(indicatifTelephonique).matches()) {
            throw new IllegalArgumentException(
                    "indicatifTelephonique doit suivre le format '+XXX', recu : " + indicatifTelephonique);
        }
        if (codeDeviseParDefaut == null || !DEVISE.matcher(codeDeviseParDefaut).matches()) {
            throw new IllegalArgumentException(
                    "codeDeviseParDefaut doit etre 3 lettres majuscules, recu : " + codeDeviseParDefaut);
        }
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
