package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.ServiceManifestDto;
import africa.civitas.egen.domain.model.ServiceManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lire un manifeste doit rendre exactement ce qui a ete declare (docs/architecture/13,
 * {@code GET /api/v1/services/{id}}) : declarer -> persister -> lire -> redeclarer ne change rien.
 */
class ServiceManifestRoundTripTest {

    private static final ObjectMapper YAML = new YAMLMapper();

    private static final String FULL = """
            apiVersion: egen.civitas.africa/v1
            kind: ServiceManifest
            metadata:
              name: news-service
              version: 2.4.0
              team: content-platform
              labels: { domain: news, tier: backend }
              annotations: { owner-contact: "news@civitas.africa" }
            runtime:
              type: container
              language: java
            deployment:
              adapter: nomad
              image: registry.civitas.africa/news-service:2.4.0
              resources: { cpu: 500m, memory: 512Mi }
              replicas: { min: 2, max: 6 }
            health:
              http: { endpoint: /health, interval: 10s, timeout: 2s, failuresBeforeUnhealthy: 3 }
            dependencies:
              services:
                - { name: notification-service, versionConstraint: ">=1.2.0", required: true }
                - { name: storage-service, versionConstraint: ">=3.0.0", required: false }
              resources:
                - { name: primary-db, type: postgres, params: { sizeGb: 20 } }
                - { name: cache, type: redis }
            events:
              publishes:
                - { type: africa.civitas.news.article.created.v1, schemaRef: schemas/created.json }
              consumes:
                - type: africa.civitas.notification.delivery-failed.v1
                  subscriptionGroup: news-service-consumers
                  deadLetter: { maxRetries: 5, strategy: exponential-backoff }
            configuration:
              required:
                - { key: FEATURE_FLAG_COMMENTS, type: boolean, default: "false" }
                - { key: PAGE_SIZE, type: integer }
              secretsRefs:
                - { key: DB_PASSWORD, provider: vault, path: secret/data/news-service/db }
            resiliency:
              timeout: 10s
              retry: { maxAttempts: 3, backoff: exponential, baseDelay: 200ms }
              circuitBreaker: { failureThreshold: 5, openDuration: 30s }
            interfaces:
              - { type: openapi, ref: /internal/openapi.json, riskDefault: read }
              - { type: grpc, ref: "grpc://news:9090" }
            lifecycle:
              shutdown: { gracePeriod: 30s }
            """;

    private static ServiceManifest parse(String yaml) throws Exception {
        return ServiceManifestMapper.toDomain(YAML.readValue(yaml, ServiceManifestDto.class));
    }

    @Test
    void aFullManifestSurvivesDomainToDtoToYamlAndBack() throws Exception {
        ServiceManifest original = parse(FULL);

        String served = YAML.writeValueAsString(ServiceManifestMapper.toDto(original));
        ServiceManifest reread = parse(served);

        assertEquals(original, reread);
    }

    @Test
    void everyDeclaredFieldIsPresentInTheReadForm() throws Exception {
        ServiceManifestDto dto = ServiceManifestMapper.toDto(parse(FULL));

        assertEquals("ServiceManifest", dto.kind);
        assertEquals("news-service", dto.metadata.name);
        assertEquals("2.4.0", dto.metadata.version);
        assertEquals("content-platform", dto.metadata.team);
        assertEquals("news", dto.metadata.labels.get("domain"));
        assertEquals("news@civitas.africa", dto.metadata.annotations.get("owner-contact"));
        assertEquals("container", dto.runtime.type);
        assertEquals("nomad", dto.deployment.adapter);
        assertEquals(6, dto.deployment.replicas.max);
        assertEquals("10s", dto.health.http.interval);
        assertEquals(2, dto.dependencies.services.size());
        assertEquals("20", dto.dependencies.resources.get(0).params.get("sizeGb"));
        assertEquals("africa.civitas.news.article.created.v1", dto.events.publishes.get(0).type);
        assertEquals(5, dto.events.consumes.get(0).deadLetter.maxRetries);
        assertEquals("false", dto.configuration.required.get(0).defaultValue);
        assertEquals("secret/data/news-service/db", dto.configuration.secretsRefs.get(0).path);
        assertEquals("exponential", dto.resiliency.retry.backoff);
        assertEquals("openapi", dto.interfaces.get(0).type);
        assertEquals("30s", dto.lifecycle.shutdown.gracePeriod);
    }

    @Test
    void anAbsentBlockStaysAbsentInTheReadForm() throws Exception {
        String minimal = FULL.substring(0, FULL.indexOf("dependencies:"))
                .replace("  labels: { domain: news, tier: backend }\n", "")
                .replace("  annotations: { owner-contact: \"news@civitas.africa\" }\n", "");
        ServiceManifestDto dto = ServiceManifestMapper.toDto(parse(minimal));

        assertNull(dto.dependencies);
        assertNull(dto.events);
        assertNull(dto.configuration);
        assertNull(dto.interfaces);
        assertNull(dto.metadata.labels);
        assertNull(dto.metadata.annotations);
        assertTrue(dto.resiliency != null, "la resilience est toujours rendue avec ses valeurs effectives");
    }

    @Test
    void anUnassignedTeamIsNotInventedOnRead() throws Exception {
        ServiceManifestDto dto = ServiceManifestMapper.toDto(parse(FULL.replace("  team: content-platform\n", "")));

        assertNull(dto.metadata.team);
    }

    @Test
    void theReadFormOfTheDefaultsIsStable() throws Exception {
        ServiceManifest original = parse(FULL.substring(0, FULL.indexOf("dependencies:")));

        assertEquals(original, parse(YAML.writeValueAsString(ServiceManifestMapper.toDto(original))));
    }
}
