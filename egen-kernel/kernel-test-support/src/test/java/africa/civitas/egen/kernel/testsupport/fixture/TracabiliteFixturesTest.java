package africa.civitas.egen.kernel.testsupport.fixture;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.ActeurType;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TracabiliteFixturesTest {

    @Test
    void initialeUsesTheConventionalTestActorAndManualEntryOrigin() {
        Tracabilite tracabilite = TracabiliteFixtures.initiale();

        assertEquals(TracabiliteFixtures.ACTEUR_TEST, tracabilite.creePar());
        assertEquals(OrigineDonnee.SAISIE_MANUELLE, tracabilite.origineDonnee());
        assertFalse(tracabilite.estSupprimee());
    }

    @Test
    void initialeWithAnActorUsesItInsteadOfTheDefault() {
        Acteur acteur = Acteur.personne(UUID.randomUUID());

        Tracabilite tracabilite = TracabiliteFixtures.initiale(acteur);

        assertEquals(acteur, tracabilite.creePar());
        assertEquals(ActeurType.PERSONNE, tracabilite.creePar().type());
    }

    @Test
    void initialeWithAnOriginUsesItInsteadOfTheDefault() {
        Tracabilite tracabilite = TracabiliteFixtures.initiale(OrigineDonnee.IMPORTEE);

        assertEquals(TracabiliteFixtures.ACTEUR_TEST, tracabilite.creePar());
        assertEquals(OrigineDonnee.IMPORTEE, tracabilite.origineDonnee());
    }
}
