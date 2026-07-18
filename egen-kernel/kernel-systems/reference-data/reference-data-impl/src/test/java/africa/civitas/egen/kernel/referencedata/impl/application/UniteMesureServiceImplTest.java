package africa.civitas.egen.kernel.referencedata.impl.application;

import africa.civitas.egen.kernel.referencedata.api.command.EnregistrerUniteMesureCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.CategorieUniteMesure;
import africa.civitas.egen.kernel.referencedata.api.domain.UniteMesure;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UniteMesureServiceImplTest {

    @Inject
    UniteMesureServiceImpl service;

    @Test
    @TestTransaction
    void enregistrerPersistsAndReturnsTheUniteMesure() {
        UniteMesure u = service.enregistrer(new EnregistrerUniteMesureCommand(
                "kilometre", "km", CategorieUniteMesure.LONGUEUR, new BigDecimal("1000"),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals("kilometre", u.nom());
    }

    @Test
    @TestTransaction
    void listerParCategorieReturnsOnlyMatchingUnits() {
        service.enregistrer(new EnregistrerUniteMesureCommand(
                "kilometre", "km", CategorieUniteMesure.LONGUEUR, new BigDecimal("1000"),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        service.enregistrer(new EnregistrerUniteMesureCommand(
                "kilogramme", "kg", CategorieUniteMesure.POIDS, BigDecimal.ONE,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        List<UniteMesure> longueurs = service.listerParCategorie(CategorieUniteMesure.LONGUEUR);

        assertEquals(1, longueurs.size());
        assertTrue(longueurs.stream().allMatch(u -> u.categorie() == CategorieUniteMesure.LONGUEUR));
    }
}
