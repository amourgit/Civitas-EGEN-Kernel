package africa.civitas.egen.kernel.referencedata.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Modele de lecture d'un Pays (B4.1) — nomenclature universelle, non specifique a
 * une Organisation.
 *
 * @param id identifiant technique unique
 * @param codeAlpha2 code ISO 3166-1 alpha-2 (ex. "GA"), toujours 2 lettres majuscules
 * @param codeAlpha3 code ISO 3166-1 alpha-3 (ex. "GAB"), toujours 3 lettres majuscules
 * @param nomOfficiel nom officiel complet
 * @param nomUsuel nom usuel court
 * @param indicatifTelephonique indicatif telephonique international (ex. "+241")
 * @param codeDeviseParDefaut code ISO 4217 de la devise par defaut du pays
 * @param tracabilite le Socle de Traçabilite complet
 */
public record Pays(
        UUID id,
        String codeAlpha2,
        String codeAlpha3,
        String nomOfficiel,
        String nomUsuel,
        String indicatifTelephonique,
        String codeDeviseParDefaut,
        Tracabilite tracabilite) {

    private static final Pattern ALPHA2 = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern ALPHA3 = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern INDICATIF = Pattern.compile("^\\+[0-9]{1,4}$");

    public Pays {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        if (codeAlpha2 == null || !ALPHA2.matcher(codeAlpha2).matches()) {
            throw new IllegalArgumentException("codeAlpha2 doit etre 2 lettres majuscules, recu : " + codeAlpha2);
        }
        if (codeAlpha3 == null || !ALPHA3.matcher(codeAlpha3).matches()) {
            throw new IllegalArgumentException("codeAlpha3 doit etre 3 lettres majuscules, recu : " + codeAlpha3);
        }
        requireNonBlank(nomOfficiel, "nomOfficiel");
        requireNonBlank(nomUsuel, "nomUsuel");
        if (indicatifTelephonique == null || !INDICATIF.matcher(indicatifTelephonique).matches()) {
            throw new IllegalArgumentException(
                    "indicatifTelephonique doit suivre le format '+XXX', recu : " + indicatifTelephonique);
        }
        requireNonBlank(codeDeviseParDefaut, "codeDeviseParDefaut");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    private static void requireNonBlank(String value, String champ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(champ + " ne peut pas etre vide.");
        }
    }
}
