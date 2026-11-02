package africa.civitas.egen.bootstrap.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 4 (docs 17) — regle A4 (docs 22, 22.7) : le Kernel ne porte aucun concept metier de la
 * Plateforme. Le test prouve aussi qu'une violation volontaire echoue : une regle qui n'a jamais echoue
 * ne prouve rien.
 */
class PlatformBoundaryRuleTest {

    private static final String FIXTURES = "africa.civitas.egen.bootstrap.architecture.fixture";

    @Test
    void theKernelCarriesNoPlatformBusinessConcept() {
        JavaClasses kernel = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("africa.civitas.egen");

        List<String> violations = PlatformBoundaryRule.violations(kernel);

        assertTrue(violations.isEmpty(),
                "A4 : un concept metier de la Plateforme a fuite dans le Kernel (docs 22.7) :\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    void aDeliberateViolationMakesTheRuleFail() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES + ".violation");

        List<String> violations = PlatformBoundaryRule.violations(fixtures);

        assertEquals(List.of(
                "champ " + FIXTURES + ".violation.TenantRegistry#cellId",
                "paquet " + FIXTURES + ".violation.catalog",
                "type " + FIXTURES + ".violation.TenantRegistry"), violations);
    }

    @Test
    void namesThatOnlyResembleAForbiddenTermAreAccepted() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES + ".compliant");

        assertTrue(PlatformBoundaryRule.violations(fixtures).isEmpty());
    }

    @Test
    void identifiersAreReadWordByWordCaseInsensitively() {
        for (String forbidden : List.of("Cell", "cellId", "CELL_ID", "TenantId", "Organisation", "organization",
                "assignmentRule", "AffectationService", "Catalogue", "serviceCatalog", "domainContract",
                "DomainContractRegistry", "domain_contract")) {
            assertTrue(PlatformBoundaryRule.matches(forbidden), forbidden + " doit etre refuse");
        }
        for (String allowed : List.of("Cellular", "organizational", "Services", "domainName", "contract",
                "ServiceDependency", "excellent", "Tenancy")) {
            assertFalse(PlatformBoundaryRule.matches(allowed), allowed + " doit etre accepte");
        }
    }
}
