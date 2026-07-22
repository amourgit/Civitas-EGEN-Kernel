package africa.civitas.egen.modules.system.identity.api.command;

import africa.civitas.egen.modules.system.identity.api.domain.MethodeAuthentification;
import africa.civitas.egen.modules.system.identity.api.domain.TypeCompte;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreerCompteCommandTest {

    private static final Acteur ACTEUR = Acteur.systeme("provisioning");

    @Test
    void acceptsAValidStandardCommand() {
        assertDoesNotThrow(() -> new CreerCompteCommand(
                "kc-1", "samuel.mba", TypeCompte.STANDARD, MethodeAuthentification.MFA,
                UUID.randomUUID(), null, ACTEUR, OrigineDonnee.SYNCHRONISEE));
    }

    @Test
    void rejectsADelegueTemporaireWithoutExpiration() {
        assertThrows(IllegalArgumentException.class, () -> new CreerCompteCommand(
                "kc-1", "delegue", TypeCompte.DELEGUE_TEMPORAIRE, MethodeAuthentification.MFA,
                UUID.randomUUID(), null, ACTEUR, OrigineDonnee.SYNCHRONISEE));
    }

    @Test
    void acceptsADelegueTemporaireWithExpiration() {
        assertDoesNotThrow(() -> new CreerCompteCommand(
                "kc-1", "delegue", TypeCompte.DELEGUE_TEMPORAIRE, MethodeAuthentification.MFA,
                UUID.randomUUID(), Instant.now().plusSeconds(3600), ACTEUR, OrigineDonnee.SYNCHRONISEE));
    }

    @Test
    void rejectsABlankKeycloakId() {
        assertThrows(IllegalArgumentException.class, () -> new CreerCompteCommand(
                " ", "samuel.mba", TypeCompte.STANDARD, MethodeAuthentification.MFA,
                UUID.randomUUID(), null, ACTEUR, OrigineDonnee.SYNCHRONISEE));
    }
}
