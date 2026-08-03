package africa.civitas.egen.kernel.testsupport.fixture;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

/**
 * Fabrique un {@link Tracabilite} pour les tests, sans repeter partout le meme
 * appel {@code Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.
 * SAISIE_MANUELLE)} — deja duplique, verbatim, dans des dizaines de fichiers de
 * test a travers tout le depot (kernel-sdk, kernel-systems/authorization,
 * kernel-systems/module-registry, kernel-domain/module-domain...).
 *
 * <p>Aucun test deja livre n'est retouche pour utiliser cette fixture : chacun
 * fonctionne deja et sa modification n'apporterait qu'un gain cosmetique pour un
 * risque reel de regression. Cette fixture existe pour tout code de test ecrit a
 * partir de maintenant.
 */
public final class TracabiliteFixtures {

    /** L'Acteur systeme conventionnel pour tout test qui n'a pas besoin d'un Acteur precis. */
    public static final Acteur ACTEUR_TEST = Acteur.systeme("test");

    private TracabiliteFixtures() {
    }

    /** @return une Tracabilite initiale, avec {@link #ACTEUR_TEST} et {@link OrigineDonnee#SAISIE_MANUELLE}. */
    public static Tracabilite initiale() {
        return Tracabilite.initiale(ACTEUR_TEST, OrigineDonnee.SAISIE_MANUELLE);
    }

    /** @return une Tracabilite initiale, avec {@code acteur} et {@link OrigineDonnee#SAISIE_MANUELLE}. */
    public static Tracabilite initiale(Acteur acteur) {
        return Tracabilite.initiale(acteur, OrigineDonnee.SAISIE_MANUELLE);
    }

    /** @return une Tracabilite initiale, avec {@link #ACTEUR_TEST} et {@code origineDonnee}. */
    public static Tracabilite initiale(OrigineDonnee origineDonnee) {
        return Tracabilite.initiale(ACTEUR_TEST, origineDonnee);
    }
}
