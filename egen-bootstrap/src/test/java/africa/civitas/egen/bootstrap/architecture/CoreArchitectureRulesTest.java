package africa.civitas.egen.bootstrap.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Transforme les garde-fous de dependance de
 * docs/architecture/02-principes-fondamentaux.md et
 * docs/architecture/16-packages-et-stack-technique.md en tests qui
 * ECHOUENT LA CI en cas de violation — pas seulement documentes (voir
 * docs/architecture/17-strategie-de-tests.md, niveau 4).
 *
 * <p>Ce test vit dans egen-bootstrap car c'est le seul module dont le
 * classpath de test reunit egen-domain, egen-application, egen-adapters/*
 * et egen-api simultanement — les classes de chacun sont importees ici
 * depuis leurs JARs respectifs, exactement comme au runtime.</p>
 */
class CoreArchitectureRulesTest {

    private static final String DOMAIN_PACKAGE = "africa.civitas.egen.domain..";
    private static final String APPLICATION_PACKAGE = "africa.civitas.egen.application..";
    private static final String ADAPTER_PACKAGE = "africa.civitas.egen.adapter..";
    private static final String API_PACKAGE = "africa.civitas.egen.api..";
    private static final String BOOTSTRAP_PACKAGE = "africa.civitas.egen.bootstrap..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("africa.civitas.egen");
    }

    @Test
    void domainHasZeroDependencyOutsideItselfAndTheJdk() {
        ArchRule rule = classes()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(DOMAIN_PACKAGE, "java..", "jdk.internal..");
        rule.check(classes);
    }

    @Test
    void domainNeverDependsOnAnAdapterOrOnAFramework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(ADAPTER_PACKAGE, API_PACKAGE, BOOTSTRAP_PACKAGE)
                .orShould().dependOnClassesThat()
                .resideInAnyPackage("io.quarkus..", "jakarta..", "com.fasterxml.jackson..",
                        "com.hashicorp..", "org.apache.kafka..", "io.nats..", "java.sql..",
                        "javax.sql..", "org.postgresql..");
        rule.check(classes);
    }

    @Test
    void applicationNeverDependsOnAConcreteAdapter() {
        // egen-application DEFINIT les ports secondaires (interfaces) ; il ne
        // doit jamais connaitre une implementation concrete
        // (egen-adapters/*) — celle-ci n'est cablee que dans egen-bootstrap.
        ArchRule rule = noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(ADAPTER_PACKAGE);
        rule.check(classes);
    }

    @Test
    void applicationNeverDependsOnAFramework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.quarkus..", "jakarta.enterprise..", "jakarta.ws.rs..",
                        "jakarta.persistence..", "com.fasterxml.jackson..", "io.nats..",
                        "com.hashicorp..", "java.sql..", "javax.sql..", "org.postgresql..");
        rule.check(classes);
    }

    @Test
    void noDomainOrApplicationClassCarriesAFrameworkAnnotation() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGE, APPLICATION_PACKAGE)
                .should().beAnnotatedWith("jakarta.persistence.Entity")
                .orShould().beAnnotatedWith("jakarta.ws.rs.Path")
                .orShould().beAnnotatedWith("jakarta.enterprise.context.ApplicationScoped");
        rule.check(classes);
    }

    @Test
    void apiNeverDependsOnAConcreteAdapterDirectly() {
        // egen-api pilote le domaine via les ports primaires d'egen-application ;
        // le cablage vers un adapter concret n'appartient qu'a egen-bootstrap
        // (garde-fou n6, docs/architecture/02-principes-fondamentaux.md).
        ArchRule rule = noClasses()
                .that().resideInAPackage(API_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(ADAPTER_PACKAGE);
        rule.check(classes);
    }

    @Test
    void noAdapterModuleDependsOnAnotherAdapterModule() {
        // Verifie chaque paire d'adapters presents sur le classpath (nomad,
        // consul, postgres-registry) — vraie par construction pour les
        // technologies pas encore ajoutees au reacteur (nats, kafka, vault,
        // otel) ; ce test devient actif pour elles des qu'elles rejoignent
        // egen-adapters/* (voir docs/architecture/19-feuille-de-route.md).
        String[] existingAdapterPackages = {
                "africa.civitas.egen.adapter.nomad..",
                "africa.civitas.egen.adapter.consul..",
                "africa.civitas.egen.adapter.postgresregistry..",
                "africa.civitas.egen.adapter.nats..",
        };
        String[] futureAdapterPackages = {
                "africa.civitas.egen.adapter.kafka..",
                "africa.civitas.egen.adapter.vault..",
                "africa.civitas.egen.adapter.otel..",
        };
        for (String ownPackage : existingAdapterPackages) {
            for (String otherPackage : existingAdapterPackages) {
                if (ownPackage.equals(otherPackage)) {
                    continue;
                }
                noClasses().that().resideInAPackage(ownPackage)
                        .should().dependOnClassesThat().resideInAPackage(otherPackage)
                        .check(classes);
            }
            noClasses().that().resideInAPackage(ownPackage)
                    .should().dependOnClassesThat().resideInAnyPackage(futureAdapterPackages)
                    .check(classes);
        }
    }
}
