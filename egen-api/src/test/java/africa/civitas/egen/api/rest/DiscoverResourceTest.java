package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Contrat de {@code GET /api/v1/discover/{serviceId}} (docs/architecture/13) :
 * les instances ET la politique de resilience EFFECTIVE de la cible, que
 * consomment les SDK et le sidecar (docs/architecture/12).
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = "egen-admin")
class DiscoverResourceTest {

    @Test
    void anUnknownServiceAnswers200WithNoInstancesAndTheDefaultPolicy() {
        given().when().get("/api/v1/discover/it-unknown-service")
                .then().statusCode(200)
                .body("serviceId", is("it-unknown-service"))
                .body("instances", hasSize(0))
                .body("resiliency.timeoutMillis", is(30000))
                .body("resiliency.retry.maxAttempts", is(1))
                .body("resiliency.retry.backoff", is("fixed"))
                .body("resiliency.retry.baseDelayMillis", is(200))
                .body("resiliency.circuitBreaker.failureThreshold", is(5))
                .body("resiliency.circuitBreaker.openDurationMillis", is(30000));
    }

    @Test
    void aMalformedServiceIdIs400() {
        given().when().get("/api/v1/discover/Pas Un Id").then().statusCode(400);
    }
}
