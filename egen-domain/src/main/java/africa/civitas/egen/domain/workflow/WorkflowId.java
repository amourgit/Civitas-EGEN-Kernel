package africa.civitas.egen.domain.workflow;

import java.util.regex.Pattern;

/**
 * Identifiant stable d'une {@link WorkflowDefinition} (ex.
 * "publish-article-workflow" — voir
 * docs/architecture/11-moteur-de-workflow.md, exemple YAML,
 * {@code metadata.name}).
 *
 * <p>Meme convention que {@link africa.civitas.egen.domain.model.ServiceId}
 * (minuscules, chiffres, tirets) — deliberement, puisqu'un WorkflowId sert
 * aussi de segment d'URL dans l'API de controle
 * (docs/architecture/13-api-et-contrats.md, {@code /api/v1/workflows/{id}}).</p>
 */
public record WorkflowId(String value) {

    private static final Pattern VALID_PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    public WorkflowId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WorkflowId ne peut pas etre vide");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "WorkflowId invalide : \"" + value + "\" — attendu : minuscules, "
                            + "chiffres et tirets, sans tiret en tete ou en queue "
                            + "(ex. \"publish-article-workflow\")");
        }
    }

    public static WorkflowId of(String value) {
        return new WorkflowId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
