package africa.civitas.egen.kernel.referencedata.impl.application;

import africa.civitas.egen.kernel.referencedata.api.command.EnregistrerDeviseCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.Devise;
import africa.civitas.egen.kernel.referencedata.api.exception.ReferentielConflitException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class DeviseServiceImplTest {

    @Inject
    DeviseServiceImpl service;

    private static EnregistrerDeviseCommand commande(String code) {
        return new EnregistrerDeviseCommand(
                code, "FCFA", "Franc CFA", 0, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);
    }

    @Test
    @TestTransaction
    void enregistrerPersistsAndReturnsTheDevise() {
        Devise d = service.enregistrer(commande("XAF"));

        assertEquals("XAF", d.codeIso4217());
        assertEquals(0, d.nombreDecimales());
    }

    @Test
    @TestTransaction
    void enregistrerRejectsADuplicateCode() {
        service.enregistrer(commande("XAF"));

        assertThrows(ReferentielConflitException.class, () -> service.enregistrer(commande("XAF")));
    }
}
