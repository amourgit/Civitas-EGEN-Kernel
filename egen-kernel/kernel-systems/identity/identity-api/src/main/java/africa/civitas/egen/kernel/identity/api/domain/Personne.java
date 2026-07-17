package africa.civitas.egen.kernel.identity.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Modele de lecture d'une Personne — l'identite unique, racine de tout rattachement
 * humain dans EGEN, independante de toute Organisation ou Cellule.
 *
 * <p>Contrairement a la plupart des entites du Kernel, une Personne n'a pas de fin de
 * validite temporelle : seul {@link #statutVital()} evolue. C'est la Tracabilite qui
 * porte, elle, la suppression logique eventuelle (fermeture administrative d'un
 * dossier, jamais la disparition de l'identite elle-meme).
 *
 * @param id identifiant technique unique et immuable
 * @param identifiantCivilReference numero national d'identite, passeport, ou identifiant
 *                                   genere si aucune piece n'a ete fournie
 * @param nomNaissance nom porte a la naissance
 * @param nomUsage nom d'usage, si different du nom de naissance (nul sinon)
 * @param prenoms prenom(s), dans l'ordre declare, au moins un
 * @param dateNaissance date de naissance, jamais dans le futur
 * @param lieuNaissance lieu de naissance (nul si inconnu)
 * @param genreDeclare genre declare (nul si non renseigne)
 * @param codesPaysNationalite codes ISO des pays de nationalite, au moins un
 * @param codesLanguePreferee codes ISO des langues preferees, peut etre vide
 * @param statutVital statut vital courant
 * @param telephonePrincipal coordonnee de contact principale (nul si absente)
 * @param emailSecours email de secours (nul si absent)
 * @param photoReferenceUrl lien vers la photo de reference (nul si absente)
 * @param statutVerificationIdentite statut de verification de l'identite declaree
 * @param tracabilite le Socle de Traçabilite complet
 */
public record Personne(
        UUID id,
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
        StatutVerificationIdentite statutVerificationIdentite,
        Tracabilite tracabilite) {

    public Personne {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        requireNonBlank(identifiantCivilReference, "identifiantCivilReference");
        requireNonBlank(nomNaissance, "nomNaissance");
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
        Objects.requireNonNull(statutVerificationIdentite, "statutVerificationIdentite ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    private static void requireNonBlank(String value, String champ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(champ + " ne peut pas etre vide.");
        }
    }

    /** @return le nom a afficher : le nom d'usage s'il existe, sinon le nom de naissance. */
    public String nomAffiche() {
        return (nomUsage != null && !nomUsage.isBlank()) ? nomUsage : nomNaissance;
    }
}
