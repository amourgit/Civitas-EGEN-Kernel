package africa.civitas.egen.modules.business.referencedata.api.command;

import africa.civitas.egen.modules.business.referencedata.api.domain.SensEcriture;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;
import java.util.regex.Pattern;

public record EnregistrerLangueCommand(
        String codeIso639,
        String nomOfficiel,
        String nomNatif,
        SensEcriture sensEcriture,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    private static final Pattern CODE_ISO_639 = Pattern.compile("^[a-z]{2,3}$");

    public EnregistrerLangueCommand {
        if (codeIso639 == null || !CODE_ISO_639.matcher(codeIso639).matches()) {
            throw new IllegalArgumentException(
                    "codeIso639 doit etre 2 ou 3 lettres minuscules, recu : " + codeIso639);
        }
        if (nomOfficiel == null || nomOfficiel.isBlank()) {
            throw new IllegalArgumentException("nomOfficiel ne peut pas etre vide.");
        }
        if (nomNatif == null || nomNatif.isBlank()) {
            throw new IllegalArgumentException("nomNatif ne peut pas etre vide.");
        }
        Objects.requireNonNull(sensEcriture, "sensEcriture ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
