package africa.civitas.egen.modules.system.identity.api.command;

import africa.civitas.egen.modules.system.identity.api.domain.StatutVital;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Commande de creation d'une Personne. {@code identifiantCivilReference} peut etre nul :
 * dans ce cas, le service genere un identifiant technique de reference, conformement
 * a la regle "numero national d'identite, passeport, ou identifiant genere si absent".
 *
 * @param identifiantCivilReference peut etre nul (genere automatiquement dans ce cas)
 * @param demandePar l'Acteur a l'origine de la creation
 * @param origineDonnee la provenance de cette donnee
 */
public record CreerPersonneCommand(
        String identifiantCivilReference,
        String nomNaissance,
        String nomUsage,
        List<String> prenoms,
        LocalDate dateNaissance,
        String lieuNaissance,
        String genreDeclare,
        List<String> codesPaysNationalite,
        List<String> codesLanguePreferee,
        StatutVital statutVital,
        String telephonePrincipal,
        String emailSecours,
        String photoReferenceUrl,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerPersonneCommand {
        if (nomNaissance == null || nomNaissance.isBlank()) {
            throw new IllegalArgumentException("nomNaissance ne peut pas etre vide.");
        }
        if (prenoms == null || prenoms.isEmpty()) {
            throw new IllegalArgumentException("prenoms doit contenir au moins un element.");
        }
        prenoms = List.copyOf(prenoms);
        Objects.requireNonNull(dateNaissance, "dateNaissance ne peut pas etre nulle.");
        if (dateNaissance.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("dateNaissance ne peut pas etre dans le futur.");
        }
        if (codesPaysNationalite == null || codesPaysNationalite.isEmpty()) {
            throw new IllegalArgumentException("codesPaysNationalite doit contenir au moins un element.");
        }
        codesPaysNationalite = List.copyOf(codesPaysNationalite);
        codesLanguePreferee = codesLanguePreferee == null ? List.of() : List.copyOf(codesLanguePreferee);
        Objects.requireNonNull(statutVital, "statutVital ne peut pas etre nul.");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
