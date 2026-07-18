package africa.civitas.egen.kernel.referencedata.impl.application;

import africa.civitas.egen.kernel.referencedata.api.command.EnregistrerFuseauHoraireCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.FuseauHoraire;
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
class FuseauHoraireServiceImplTest {

    @Inject
    FuseauHoraireServiceImpl service;

    private static EnregistrerFuseauHoraireCommand commande(String identifiant) {
        return new EnregistrerFuseauHoraireCommand(
                identifiant, "Afrique de l'Ouest", "+01:00",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);
    }

    @Test
    @TestTransaction
    void enregistrerPersistsAndReturnsTheFuseauHoraire() {
        FuseauHoraire f = service.enregistrer(commande("Africa/Libreville"));

        assertEquals("Africa/Libreville", f.identifiantIana());
    }

    @Test
    @TestTransaction
    void enregistrerRejectsADuplicateIdentifiant() {
        service.enregistrer(commande("Africa/Libreville"));

        assertThrows(ReferentielConflitException.class,
                () -> service.enregistrer(commande("Africa/Libreville")));
    }
}
