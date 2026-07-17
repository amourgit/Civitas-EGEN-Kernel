package africa.civitas.egen.kernel.sdk.tracabilite;

import java.time.Instant;

/**
 * Le Socle de Traçabilite — porte par toute entite du Kernel, sans exception, tel que
 * pose des le debut de la modelisation : "rien ne doit etre fait sans traçabilite
 * multiple et reelle."
 *
 * <p>Cette classe n'est jamais mutee sur place : chaque evolution ({@link
 * #avecModification(Acteur, String)}, {@link #avecSuppressionLogique(Acteur, String)})
 * produit une nouvelle instance, incremente {@link #version()}, et exige un motif des
 * qu'il ne s'agit plus de la creation initiale — exactement la regle "Motif de la
 * derniere modification — obligatoire des qu'il ne s'agit pas d'une creation."
 *
 * @param creeLe horodatage de creation
 * @param creePar auteur de la creation
 * @param modifieLe horodatage de la derniere modification
 * @param modifiePar auteur de la derniere modification
 * @param version compteur incremente a chaque modification, a partir de 1
 * @param origineDonnee provenance de la donnee
 * @param motifDerniereModification obligatoire des que {@code version > 1}, sinon nul
 * @param supprimeLe horodatage de suppression logique, nul si l'entite n'est pas supprimee
 * @param supprimePar auteur de la suppression logique, nul si l'entite n'est pas supprimee
 */
public record Tracabilite(
        Instant creeLe,
        Acteur creePar,
        Instant modifieLe,
        Acteur modifiePar,
        long version,
        OrigineDonnee origineDonnee,
        String motifDerniereModification,
        Instant supprimeLe,
        Acteur supprimePar) {

    public Tracabilite {
        if (creeLe == null || creePar == null || modifieLe == null || modifiePar == null
                || origineDonnee == null) {
            throw new IllegalArgumentException(
                    "creeLe, creePar, modifieLe, modifiePar et origineDonnee sont obligatoires.");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version doit demarrer a 1, recu : " + version);
        }
        if (version > 1 && (motifDerniereModification == null || motifDerniereModification.isBlank())) {
            throw new IllegalArgumentException(
                    "motifDerniereModification est obligatoire des que version > 1 "
                            + "(ce n'est plus la creation initiale).");
        }
        boolean suppressionCoherente = (supprimeLe == null) == (supprimePar == null);
        if (!suppressionCoherente) {
            throw new IllegalArgumentException(
                    "supprimeLe et supprimePar doivent etre tous les deux nuls, ou tous les deux renseignes.");
        }
    }

    /** Trace la creation initiale d'une entite — version 1, aucun motif requis. */
    public static Tracabilite initiale(Acteur creePar, OrigineDonnee origineDonnee) {
        Instant maintenant = Instant.now();
        return new Tracabilite(maintenant, creePar, maintenant, creePar, 1, origineDonnee, null, null, null);
    }

    /** Produit une nouvelle Tracabilite refletant une modification, avec motif obligatoire. */
    public Tracabilite avecModification(Acteur modifiePar, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException(
                    "Un motif est obligatoire pour toute modification posterieure a la creation.");
        }
        return new Tracabilite(creeLe, creePar, Instant.now(), modifiePar, version + 1,
                origineDonnee, motif, supprimeLe, supprimePar);
    }

    /** Produit une nouvelle Tracabilite refletant une suppression logique. La donnee reste en base. */
    public Tracabilite avecSuppressionLogique(Acteur supprimePar, String motif) {
        Tracabilite apresModification = avecModification(supprimePar, motif);
        return new Tracabilite(apresModification.creeLe(), apresModification.creePar(),
                apresModification.modifieLe(), apresModification.modifiePar(),
                apresModification.version(), apresModification.origineDonnee(),
                apresModification.motifDerniereModification(), Instant.now(), supprimePar);
    }

    /** @return vrai si cette entite a fait l'objet d'une suppression logique. */
    public boolean estSupprimee() {
        return supprimeLe != null;
    }
}
