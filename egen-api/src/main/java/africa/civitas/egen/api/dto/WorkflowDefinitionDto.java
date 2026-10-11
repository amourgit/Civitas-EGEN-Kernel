package africa.civitas.egen.api.dto;

/**
 * Transport YAML/JSON d'une WorkflowDefinition (voir
 * docs/architecture/11-moteur-de-workflow.md, "Definition declarative d'un
 * workflow"). Jamais expose au domaine : voir
 * africa.civitas.egen.api.mapper.WorkflowMapper.
 */
public class WorkflowDefinitionDto {
    public String apiVersion;
    public String kind;
    public MetadataDto metadata;
    public WorkflowSpecDto spec;
}
