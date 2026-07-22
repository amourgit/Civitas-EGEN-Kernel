package africa.civitas.egen.modules.business.referencedata.impl.application;

import africa.civitas.egen.modules.business.referencedata.api.command.CreerModeleSectorielCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.ModeleSectoriel;
import africa.civitas.egen.modules.business.referencedata.api.domain.StatutModeleSectoriel;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ModeleSectorielServiceImplTest {

    @Inject
    ModeleSectorielServiceImpl service;

    @Test
    @TestTransaction
    void creerPersistsAsActifByDefault() {
        ModeleSectoriel m = service.creer(new CreerModeleSectorielCommand(
                "Education superieure", "Gabarit pour universites et grandes ecoles", "1.0.0",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(StatutModeleSectoriel.ACTIF, m.statut());
    }

    @Test
    @TestTransaction
    void listerActifsIncludesTheNewlyCreatedModele() {
        ModeleSectoriel m = service.creer(new CreerModeleSectorielCommand(
                "Sante", "Gabarit pour etablissements de sante", "1.0.0",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        List<ModeleSectoriel> actifs = service.listerActifs();

        assertTrue(actifs.stream().anyMatch(x -> x.id().equals(m.id())));
    }
}
