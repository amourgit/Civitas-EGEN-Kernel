package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerLangueCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.Langue;
import africa.civitas.egen.modules.business.referencedata.api.domain.SensEcriture;
import africa.civitas.egen.modules.business.referencedata.api.exception.ReferentielConflitException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class LangueServiceImplTest {

    @Inject
    LangueServiceImpl service;

    private static EnregistrerLangueCommand commande(String code) {
        return new EnregistrerLangueCommand(
                code, "Francais", "Francais", SensEcriture.GAUCHE_A_DROITE,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);
    }

    @Test
    @TestTransaction
    void enregistrerPersistsAndReturnsTheLangue() {
        Langue l = service.enregistrer(commande("fr"));

        assertEquals("fr", l.codeIso639());
    }

    @Test
    @TestTransaction
    void enregistrerRejectsADuplicateCode() {
        service.enregistrer(commande("fr"));

        assertThrows(ReferentielConflitException.class, () -> service.enregistrer(commande("fr")));
    }
}
