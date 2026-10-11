package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.WorkflowDefinitionDto;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Le YAML de reference de docs/architecture/11-moteur-de-workflow.md,
 * recopie tel quel : si le doc et le code divergent, ce test casse.
 */
class WorkflowYamlTest {

    private static final String DOC_11_YAML = """
            apiVersion: egen.civitas.africa/v1
            kind: WorkflowDefinition
            metadata:
              name: publish-article-workflow
            spec:
              steps:
                - id: validate-content
                  service: news-service
                  operation: POST /internal/validate
                  timeout: 10s
                  retry: { maxAttempts: 3, backoff: exponential }
                - id: reserve-notification-slot
                  service: notification-service
                  operation: POST /internal/reserve-slot
                  dependsOn: [validate-content]
                  compensation:
                    operation: POST /internal/release-slot
                - id: publish
                  service: news-service
                  operation: POST /internal/publish
                  dependsOn: [reserve-notification-slot]
                  compensation:
                    operation: POST /internal/unpublish
                - id: notify-subscribers
                  service: notification-service
                  operation: EVENT africa.civitas.news.article.published.v1   # etape asynchrone
                  dependsOn: [publish]
              compensationStrategy: backward
              executionPolicy:
                globalTimeout: 5m
                manualApprovalSteps: []
            """;

    private static final ObjectMapper YAML = new YAMLMapper();

    @Test
    void theYamlOfDoc11ParsesAndMapsToTheExpectedDomainObject() throws Exception {
        WorkflowDefinitionDto dto = YAML.readValue(DOC_11_YAML, WorkflowDefinitionDto.class);
        WorkflowDefinition definition = WorkflowMapper.toDomain(dto);

        assertEquals("publish-article-workflow", definition.id().value());
        assertEquals(4, definition.steps().size());
        assertEquals(Duration.ofSeconds(10), definition.stepById(StepId.of("validate-content")).orElseThrow().timeout());
        assertEquals(3, definition.stepById(StepId.of("validate-content")).orElseThrow().retry().maxAttempts());
        assertEquals("EVENT africa.civitas.news.article.published.v1",
                definition.stepById(StepId.of("notify-subscribers")).orElseThrow().operation().raw());
        assertEquals(Duration.ofMinutes(5), definition.executionPolicy().globalTimeout());
    }

    @Test
    void theServedDefinitionIsReadableByTheSameParser() throws Exception {
        WorkflowDefinition original = WorkflowMapper.toDomain(YAML.readValue(DOC_11_YAML, WorkflowDefinitionDto.class));

        String served = YAML.writeValueAsString(WorkflowMapper.toDto(original));
        WorkflowDefinition reread = WorkflowMapper.toDomain(YAML.readValue(served, WorkflowDefinitionDto.class));

        assertEquals(original.steps().size(), reread.steps().size());
        original.steps().forEach(step -> assertEquals(step, reread.stepById(step.id()).orElseThrow()));
    }
}
