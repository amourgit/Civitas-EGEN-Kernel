package africa.civitas.egen.kernel.identity.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompteTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void anActiveNonExpiredCompteIsUsable() {
        Compte c = new Compte(
                UUID.randomUUID(), "kc-123", "samuel.mba", TypeCompte.STANDARD,
                StatutCompte.ACTIF, null, MethodeAuthentification.MFA,
                UUID.randomUUID(), null, TRACABILITE);

        assertTrue(c.estUtilisable());
    }

    @Test
    void aSuspendedCompteIsNotUsable() {
        Compte c = new Compte(
                UUID.randomUUID(), "kc-123", "samuel.mba", TypeCompte.STANDARD,
                StatutCompte.SUSPENDU, null, MethodeAuthentification.MFA,
                UUID.randomUUID(), null, TRACABILITE);

        assertFalse(c.estUtilisable());
    }

    @Test
    void anExpiredCompteIsNotUsableEvenIfActive() {
        Compte c = new Compte(
                UUID.randomUUID(), "kc-123", "delegue.temp", TypeCompte.DELEGUE_TEMPORAIRE,
                StatutCompte.ACTIF, null, MethodeAuthentification.MOT_DE_PASSE_SEUL,
                UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS), TRACABILITE);

        assertFalse(c.estUtilisable());
    }

    @Test
    void rejectsADelegueTemporaireWithoutExpirationDate() {
        assertThrows(IllegalArgumentException.class, () -> new Compte(
                UUID.randomUUID(), "kc-123", "delegue.temp", TypeCompte.DELEGUE_TEMPORAIRE,
                StatutCompte.ACTIF, null, MethodeAuthentification.MOT_DE_PASSE_SEUL,
                UUID.randomUUID(), null, TRACABILITE));
    }

    @Test
    void rejectsACompteWithoutPersonneId() {
        assertThrows(NullPointerException.class, () -> new Compte(
                UUID.randomUUID(), "kc-123", "samuel.mba", TypeCompte.STANDARD,
                StatutCompte.ACTIF, null, MethodeAuthentification.MFA,
                null, null, TRACABILITE));
    }
}
