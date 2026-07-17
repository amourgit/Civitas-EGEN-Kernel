package africa.civitas.egen.kernel.sdk.tracabilite;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracabiliteTest {

    private static final Acteur SAMUEL = Acteur.personne(UUID.randomUUID());
    private static final Acteur SYNCHRO = Acteur.systeme("synchronisation-keycloak");

    @Test
    void initialeStartsAtVersionOneWithNoMotifRequired() {
        Tracabilite t = Tracabilite.initiale(SAMUEL, OrigineDonnee.SAISIE_MANUELLE);

        assertEquals(1L, t.version());
        assertEquals(SAMUEL, t.creePar());
        assertEquals(SAMUEL, t.modifiePar());
        assertEquals(null, t.motifDerniereModification());
        assertFalse(t.estSupprimee());
    }

    @Test
    void constructingVersionAboveOneWithoutMotifIsRejected() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Tracabilite(now, SAMUEL, now, SAMUEL, 2, OrigineDonnee.SAISIE_MANUELLE,
                        null, null, null));
    }

    @Test
    void avecModificationIncrementsVersionAndRequiresAMotif() {
        Tracabilite initiale = Tracabilite.initiale(SAMUEL, OrigineDonnee.SAISIE_MANUELLE);

        Tracabilite modifiee = initiale.avecModification(SYNCHRO, "Correction du nom d'usage");

        assertEquals(2L, modifiee.version());
        assertEquals(SYNCHRO, modifiee.modifiePar());
        assertEquals(SAMUEL, modifiee.creePar(), "Le createur original ne doit jamais changer.");
        assertEquals("Correction du nom d'usage", modifiee.motifDerniereModification());
    }

    @Test
    void avecModificationRejectsABlankMotif() {
        Tracabilite initiale = Tracabilite.initiale(SAMUEL, OrigineDonnee.SAISIE_MANUELLE);

        assertThrows(IllegalArgumentException.class, () -> initiale.avecModification(SYNCHRO, "  "));
    }

    @Test
    void avecSuppressionLogiqueMarksTheRecordDeletedWithoutLosingHistory() {
        Tracabilite initiale = Tracabilite.initiale(SAMUEL, OrigineDonnee.SAISIE_MANUELLE);

        Tracabilite supprimee = initiale.avecSuppressionLogique(SYNCHRO, "Fermeture administrative");

        assertTrue(supprimee.estSupprimee());
        assertEquals(SYNCHRO, supprimee.supprimePar());
        assertEquals(2L, supprimee.version());
        assertEquals(SAMUEL, supprimee.creePar(), "La suppression logique ne doit jamais effacer la creation.");
    }

    @Test
    void deletionMustSetBothTimestampAndActorTogether() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Tracabilite(now, SAMUEL, now, SAMUEL, 1, OrigineDonnee.SAISIE_MANUELLE,
                        null, now, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Tracabilite(now, SAMUEL, now, SAMUEL, 1, OrigineDonnee.SAISIE_MANUELLE,
                        null, null, SAMUEL));
    }

    @Test
    void versionBelowOneIsRejected() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Tracabilite(now, SAMUEL, now, SAMUEL, 0, OrigineDonnee.SAISIE_MANUELLE,
                        null, null, null));
    }
}
