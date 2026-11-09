package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Limites d'entrée de l'API (constat K10, docs/architecture/24 ; docs/architecture/13, « Limites d'entrée ») : ce que
 * l'appelant HTTP voit quand un corps est trop gros ou un YAML hostile. Un corps de plus de 1 Mio est refusé par
 * le serveur (413) ; un YAML sous cette limite mais au-delà des limites d'analyse est refusé par le Kernel (400).
 *
 * <p>Le module {@code egen-api} n'a pas de fichier {@code application.properties} : la limite de 1 Mio est posée
 * dans celui d'{@code egen-bootstrap}, que ce test ne voit pas (sans profil, Quarkus applique alors son défaut de
 * 10 Mo, et un corps de 1,2 Mo arrive jusqu'à l'analyse YAML, qui le refuse en 400). Le profil ci-dessous pose donc
 * la même valeur ; {@code InputLimitsConfigurationTest} (egen-bootstrap) vérifie que le fichier réel la contient.
 */
@QuarkusTest
@TestProfile(ServiceResourceInputLimitsTest.OneMebibyteBodyLimit.class)
@TestSecurity(user = "test-admin", roles = "egen-admin")
class ServiceResourceInputLimitsTest {

    /** Même valeur que {@code quarkus.http.limits.max-body-size} d'egen-bootstrap. */
    public static class OneMebibyteBodyLimit implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.limits.max-body-size", "1M");
        }
    }

    @BeforeAll
    static void configureYamlEncoding() {
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    private static int post(String body) {
        return given()
                .contentType("application/yaml")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body)
                .when().post("/api/v1/services")
                .then().extract().statusCode();
    }

    @Test
    void aBodyOverOneMebibyteIsRefusedByTheServerWith413() {
        org.junit.jupiter.api.Assertions.assertEquals(413, post("x".repeat(1_200_000)));
    }

    @Test
    void aYamlDocumentOverTheYamlSizeLimitButUnderTheBodyLimitIsRefusedWith400() {
        // 300 000 caractères : sous 1 Mio (le serveur l'accepte) mais au-delà de 256 Ko d'analyse YAML.
        org.junit.jupiter.api.Assertions.assertEquals(400, post("key: " + "x".repeat(300_000) + "\n"));
    }

    @Test
    void aDeeplyNestedYamlIsRefusedWith400AndNeverWith500() {
        org.junit.jupiter.api.Assertions.assertEquals(400, post("a: " + "[".repeat(5_000) + "]".repeat(5_000) + "\n"));
    }

    @Test
    void aManifestWithADuplicateKeyIsRefusedWith400() {
        org.junit.jupiter.api.Assertions.assertEquals(400, post("""
                apiVersion: egen.civitas.africa/v1
                kind: ServiceManifest
                metadata:
                  name: dup-service
                  name: dup-service-bis
                  version: 1.0.0
                """));
    }
}
