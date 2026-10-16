package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.ServiceManifestDto;
import africa.civitas.egen.domain.model.ConfigurationSpec;
import africa.civitas.egen.domain.model.EventSpec;
import africa.civitas.egen.domain.model.ServiceInterface;
import africa.civitas.egen.domain.model.ServiceManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les blocs events / configuration / interfaces du manifeste (docs/architecture/06 et 21.3), parses depuis le YAML. */
class ManifestBlocksMapperTest {

    private static final ObjectMapper YAML = new YAMLMapper();

    private static final String HEAD = """
            apiVersion: egen.civitas.africa/v1
            kind: ServiceManifest
            metadata:
              name: news-service
              version: 2.4.0
              team: content-platform
            runtime:
              type: container
              artifact: registry.civitas.africa/news-service:2.4.0
              language: java
            deployment:
              adapter: nomad
              image: registry.civitas.africa/news-service:2.4.0
              resources: { cpu: 500m, memory: 512Mi }
              replicas: { min: 2, max: 6 }
            health:
              http: { endpoint: /health, interval: 10s, timeout: 2s, failuresBeforeUnhealthy: 3 }
            """;

    private static ServiceManifest manifest(String blocks) throws Exception {
        return ServiceManifestMapper.toDomain(YAML.readValue(HEAD + blocks, ServiceManifestDto.class));
    }

    @Test
    void theEventsBlockOfDoc06MapsToTheDomain() throws Exception {
        ServiceManifest manifest = manifest("""
                events:
                  publishes:
                    - type: africa.civitas.news.article.created.v1
                      schemaRef: schemas/article-created.v1.json
                    - type: africa.civitas.news.article.updated.v1
                      schemaRef: schemas/article-updated.v1.json
                  consumes:
                    - type: africa.civitas.notification.delivery-failed.v1
                      subscriptionGroup: news-service-consumers
                      deadLetter:
                        maxRetries: 5
                        strategy: exponential-backoff
                """);

        EventSpec events = manifest.events();
        assertEquals(2, events.publishes().size());
        assertEquals("schemas/article-created.v1.json", events.publishes().get(0).schemaRef());
        assertEquals("news-service-consumers", events.consumes().get(0).subscriptionGroup());
        assertEquals(5, events.consumes().get(0).deadLetter().maxRetries());
        assertEquals("exponential-backoff", events.consumes().get(0).deadLetter().strategy());
    }

    @Test
    void theConfigurationBlockOfDoc06MapsToTheDomainIncludingTheDefaultKeyword() throws Exception {
        ServiceManifest manifest = manifest("""
                configuration:
                  required:
                    - key: FEATURE_FLAG_COMMENTS
                      type: boolean
                      default: "false"
                    - key: PAGE_SIZE
                      type: integer
                  secretsRefs:
                    - key: DB_PASSWORD
                      provider: vault
                      path: secret/data/news-service/db
                """);

        ConfigurationSpec configuration = manifest.configuration();
        assertEquals("false", configuration.required().get(0).defaultValue());
        assertEquals("boolean", configuration.required().get(0).type());
        assertEquals(null, configuration.required().get(1).defaultValue());
        assertEquals("vault", configuration.secretsRefs().get(0).provider());
        assertEquals("secret/data/news-service/db", configuration.secretsRefs().get(0).path());
    }

    @Test
    void anUnquotedYamlBooleanDefaultIsAcceptedAsText() throws Exception {
        ServiceManifest manifest = manifest("""
                configuration:
                  required:
                    - key: FLAG
                      type: boolean
                      default: false
                """);

        assertEquals("false", manifest.configuration().required().get(0).defaultValue());
    }

    @Test
    void theInterfacesBlockOfDoc21MapsToTheDomainAndStaysOpaque() throws Exception {
        ServiceManifest manifest = manifest("""
                interfaces:
                  - type: openapi
                    ref: /internal/openapi.json
                    riskDefault: read
                  - type: grpc
                    ref: grpc://news:9090
                    riskDefault: catastrophic
                  - type: mcp
                    ref: mcp://news
                """);

        assertEquals(List.of(
                new ServiceInterface("openapi", "/internal/openapi.json", "read"),
                new ServiceInterface("grpc", "grpc://news:9090", "catastrophic"),
                new ServiceInterface("mcp", "mcp://news", null)), manifest.interfaces());
    }

    @Test
    void dependenciesResourcesOfDoc06AreReferencedAndCarried() throws Exception {
        ServiceManifest manifest = manifest("""
                dependencies:
                  services:
                    - name: notification-service
                      versionConstraint: ">=1.2.0"
                      required: true
                  resources:
                    - name: primary-db
                      type: postgres
                      params:
                        sizeGb: 20
                    - name: cache
                      type: redis
                """);

        assertEquals(1, manifest.dependencies().size());
        assertEquals(2, manifest.resources().size());
        assertEquals("postgres", manifest.resources().get(0).type());
        assertEquals("20", manifest.resources().get(0).params().get("sizeGb"));
        assertEquals(java.util.Map.of(), manifest.resources().get(1).params());
    }

    @Test
    void invalidOrDuplicatedResourcesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> manifest("""
                dependencies:
                  resources:
                    - name: db
                """));
        assertThrows(IllegalArgumentException.class, () -> manifest("""
                dependencies:
                  resources:
                    - { name: db, type: postgres }
                    - { name: db, type: redis }
                """));
    }

    @Test
    void aManifestWithoutAnyOfTheBlocksIsUnchanged() throws Exception {
        ServiceManifest manifest = manifest("");

        assertEquals(EventSpec.none(), manifest.events());
        assertEquals(ConfigurationSpec.none(), manifest.configuration());
        assertTrue(manifest.interfaces().isEmpty());
    }

    @Test
    void invalidContentIsRejectedWithAMessageNamingTheFaultyField() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> manifest("""
                events:
                  publishes:
                    - type: "africa.*"
                """)).getMessage().contains("type d'evenement invalide"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> manifest("""
                configuration:
                  required:
                    - key: N
                      type: integer
                      default: abc
                """)).getMessage().contains("N"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> manifest("""
                configuration:
                  secretsRefs:
                    - key: S
                      provider: vault
                """)).getMessage().contains("path"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> manifest("""
                interfaces:
                  - type: mcp
                """)).getMessage().contains("interfaces[].ref"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> manifest("""
                interfaces:
                  -
                """)).getMessage().contains("interfaces"));
    }

    @Test
    void theRuntimeArtifactIsOptionalAndDefaultsToTheDeploymentImage() throws Exception {
        // L'exemple annote du doc 06 ne porte pas runtime.artifact.
        String withoutArtifact = HEAD.replace("  artifact: registry.civitas.africa/news-service:2.4.0\n", "");
        ServiceManifest manifest = ServiceManifestMapper.toDomain(YAML.readValue(withoutArtifact, ServiceManifestDto.class));

        assertEquals("registry.civitas.africa/news-service:2.4.0", manifest.runtime().artifact());
        assertEquals("registry.civitas.africa/news-service:2.4.0", manifest.deployment().image());
    }

    @Test
    void anExplicitRuntimeArtifactIsKept() throws Exception {
        String custom = HEAD.replace("  artifact: registry.civitas.africa/news-service:2.4.0\n", "  artifact: registry.civitas.africa/other:1\n");

        assertEquals("registry.civitas.africa/other:1",
                ServiceManifestMapper.toDomain(YAML.readValue(custom, ServiceManifestDto.class)).runtime().artifact());
    }

    @Test
    void unsupportedBlocksOfTheManifestAreStillRejectedExplicitly() {
        // network, deployment.autoscaling... : voir "Blocs pris en charge" du doc 06.
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class, () -> manifest("""
                network:
                  endpoints: []
                """));
    }
}
