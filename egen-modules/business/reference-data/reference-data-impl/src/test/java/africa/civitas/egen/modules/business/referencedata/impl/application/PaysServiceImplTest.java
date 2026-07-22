package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerPaysCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.Pays;
import africa.civitas.egen.modules.business.referencedata.api.exception.ReferentielConflitException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PaysServiceImplTest {

    @Inject
    PaysServiceImpl service;

    private static EnregistrerPaysCommand commande(String codeAlpha2, String codeAlpha3) {
        return new EnregistrerPaysCommand(
                codeAlpha2, codeAlpha3, "Republique Gabonaise", "Gabon", "+241", "XAF",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);
    }

    @Test
    @TestTransaction
    void enregistrerPersistsAndReturnsThePays() {
        Pays p = service.enregistrer(commande("GA", "GAB"));

        assertEquals("GA", p.codeAlpha2());
        assertEquals(1L, p.tracabilite().version());
    }

    @Test
    @TestTransaction
    void enregistrerRejectsADuplicateCode() {
        service.enregistrer(commande("GA", "GAB"));

        assertThrows(ReferentielConflitException.class, () -> service.enregistrer(commande("GA", "GAB")));
    }

    @Test
    @TestTransaction
    void trouverParCodeAlpha2FindsThePersistedPays() {
        service.enregistrer(commande("SN", "SEN"));

        Optional<Pays> trouve = service.trouverParCodeAlpha2("SN");

        assertTrue(trouve.isPresent());
        assertEquals("SEN", trouve.get().codeAlpha3());
    }
}
