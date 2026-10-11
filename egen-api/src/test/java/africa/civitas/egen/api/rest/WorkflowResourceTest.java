package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Contrat HTTP de {@link WorkflowResource} (voir
 * docs/architecture/13-api-et-contrats.md) avec le cablage de test de
 * {@link TestUseCaseProducers} : le moteur y est cable avec un executor qui
 * ne pilote jamais, donc une execution reste {@code RUNNING} apres
 * {@code POST .../executions}. On verifie ici le contrat (202 + Location,
 * codes d'erreur, transitions pause/resume/terminate, RBAC) ; l'orchestration
 * des etapes est couverte par WorkflowEngineTest (egen-application).
 *
 * <p>Chaque test utilise son propre nom de workflow ({@link #uniqueName()})
 * : le store de test est partage par toute la classe.</p>
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = "egen-admin")
class WorkflowResourceTest {

    @BeforeAll
    static void configureYamlEncoding() {
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    private static String uniqueName() {
        return "it-wf-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String workflowYaml(String name) {
        return """
                apiVersion: egen.civitas.africa/v1
                kind: WorkflowDefinition
                metadata:
                  name: %s
                spec:
                  steps:
                    - id: validate-content
                      service: news-service
                      operation: POST /internal/validate
                      timeout: 10s
                      retry: { maxAttempts: 3, backoff: exponential }
                    - id: publish
                      service: news-service
                      operation: POST /internal/publish
                      dependsOn: [validate-content]
                      compensation:
                        operation: POST /internal/unpublish
                  compensationStrategy: backward
                  executionPolicy:
                    globalTimeout: 5m
                    manualApprovalSteps: []
                """.formatted(name);
    }

    private static RequestSpecification mutation() {
        return given().header("Idempotency-Key", UUID.randomUUID().toString());
    }

    private static String define(String name) {
        return mutation().contentType("application/yaml").body(workflowYaml(name))
                .when().post("/api/v1/workflows")
                .then().statusCode(202)
                .extract().header("Location");
    }

    /** @return l'URL de suivi de l'execution creee */
    private static String run(String name, String jsonBody) {
        RequestSpecification request = mutation();
        if (jsonBody != null) {
            request = request.contentType(ContentType.JSON).body(jsonBody);
        }
        return request.when().post("/api/v1/workflows/" + name + "/executions")
                .then().statusCode(202)
                .extract().header("Location");
    }

    // ------------------------------------------------------------------ definitions

    @Test
    void defineAnswers202WithLocationAndTheDefinitionCanBeReadBack() {
        String name = uniqueName();
        String location = define(name);

        given().when().get(location)
                .then().statusCode(200)
                .body("metadata.name", is(name))
                .body("kind", is("WorkflowDefinition"))
                .body("spec.steps", hasSize(2))
                .body("spec.steps[0].id", is("validate-content"))
                .body("spec.steps[0].timeout", is("10s"))
                .body("spec.steps[1].compensation.operation", is("POST /internal/unpublish"))
                .body("spec.executionPolicy.globalTimeout", is("5m"));
    }

    @Test
    void defineWithoutIdempotencyKeyIsRejected() {
        given().contentType("application/yaml").body(workflowYaml(uniqueName()))
                .when().post("/api/v1/workflows")
                .then().statusCode(400);
    }

    @Test
    void defineRejectsMalformedYamlAndInvalidWorkflowsWith400() {
        mutation().contentType("application/yaml").body("ceci: [n'est: pas du yaml valide")
                .when().post("/api/v1/workflows").then().statusCode(400);

        String selfDependent = workflowYaml(uniqueName()).replace("dependsOn: [validate-content]", "dependsOn: [publish]");
        mutation().contentType("application/yaml").body(selfDependent)
                .when().post("/api/v1/workflows").then().statusCode(400);

        String wrongKind = workflowYaml(uniqueName()).replace("WorkflowDefinition", "ServiceManifest");
        mutation().contentType("application/yaml").body(wrongKind)
                .when().post("/api/v1/workflows").then().statusCode(400);
    }

    @Test
    void getOfAnUnknownWorkflowIs404() {
        given().when().get("/api/v1/workflows/it-wf-inconnu").then().statusCode(404);
    }

    // ------------------------------------------------------------------ executions

    @Test
    void runAnswers202WithALocationPointingAtARunningExecutionCarryingItsInput() {
        String name = uniqueName();
        define(name);

        String location = run(name, "{\"input\": {\"articleId\": \"42\"}}");

        given().when().get(location)
                .then().statusCode(200)
                .body("executionId", notNullValue())
                .body("workflowId", is(name))
                .body("status", is("RUNNING"))
                .body("input.articleId", is("42"))
                .body("stepResults", hasSize(0));
    }

    @Test
    void runAcceptsARequestWithoutBodyAsAnEmptyInput() {
        String name = uniqueName();
        define(name);

        String location = run(name, null);

        given().when().get(location).then().statusCode(200).body("status", is("RUNNING"));
    }

    @Test
    void runRejectsAMalformedBodyWith400() {
        String name = uniqueName();
        define(name);

        mutation().contentType(ContentType.JSON).body("{pas du json")
                .when().post("/api/v1/workflows/" + name + "/executions")
                .then().statusCode(400);
    }

    @Test
    void runOfAnUnknownWorkflowIs404() {
        mutation().when().post("/api/v1/workflows/it-wf-inconnu/executions").then().statusCode(404);
    }

    @Test
    void anExecutionIsOnlyAddressableUnderItsOwnWorkflow() {
        String first = uniqueName();
        String second = uniqueName();
        define(first);
        define(second);
        String location = run(first, null);
        String executionId = location.substring(location.lastIndexOf('/') + 1);

        given().when().get("/api/v1/workflows/" + second + "/executions/" + executionId)
                .then().statusCode(404);
        given().when().get("/api/v1/workflows/" + first + "/executions/inconnue").then().statusCode(404);
    }

    // ------------------------------------------------------------------ actions

    @Test
    void pauseResumeAndTerminateDriveTheExecutionThroughItsStates() {
        String name = uniqueName();
        define(name);
        String location = run(name, null);

        mutation().when().post(location + "/actions/pause").then().statusCode(202);
        given().when().get(location).then().body("status", is("PAUSED"));

        mutation().when().post(location + "/actions/resume").then().statusCode(202);
        given().when().get(location).then().body("status", is("RUNNING"));

        mutation().contentType(ContentType.JSON).body("{\"reason\": \"test\"}")
                .when().post(location + "/actions/terminate").then().statusCode(202);
        given().when().get(location).then().body("status", is("FAILED")); // rien a compenser
    }

    @Test
    void resumingATerminatedExecutionIsAConflict() {
        String name = uniqueName();
        define(name);
        String location = run(name, null);
        mutation().when().post(location + "/actions/terminate").then().statusCode(202);

        mutation().when().post(location + "/actions/resume")
                .then().statusCode(409);
    }

    @Test
    void actionsOnAnUnknownExecutionAre404() {
        String name = uniqueName();
        define(name);

        mutation().when().post("/api/v1/workflows/" + name + "/executions/inconnue/actions/pause")
                .then().statusCode(404);
    }

    // ------------------------------------------------------------------ securite

    @Test
    @TestSecurity(user = "test-dev", roles = "developer")
    void aNonAdminCanReadButNeverMutateWorkflows() {
        given().when().get("/api/v1/workflows/it-wf-inconnu").then().statusCode(404); // lecture autorisee

        mutation().contentType("application/yaml").body(workflowYaml(uniqueName()))
                .when().post("/api/v1/workflows").then().statusCode(403);
        mutation().when().post("/api/v1/workflows/it-wf-inconnu/executions").then().statusCode(403);
        mutation().when().post("/api/v1/workflows/x/executions/y/actions/pause").then().statusCode(403);
    }
}
