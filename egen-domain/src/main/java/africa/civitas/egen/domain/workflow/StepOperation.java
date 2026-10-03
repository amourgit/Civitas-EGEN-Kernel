package africa.civitas.egen.domain.workflow;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ce qu'une {@link Step} (ou sa compensation) invoque reellement — voir
 * docs/architecture/11-moteur-de-workflow.md, "chaque etape reference une
 * operation exposee par le service... jamais une logique embarquee dans le
 * workflow lui-meme". Toujours l'une de deux formes, jamais une troisieme
 * (type scelle plutot qu'un champ {@code kind} + chaine libre — ensemble
 * ferme et deliberement exhaustif, conforme aux deux seules formes montrees
 * par la syntaxe YAML de reference) :
 *
 * <ul>
 *   <li>{@link Http} — {@code operation: "POST /internal/validate"}</li>
 *   <li>{@link Event} — {@code operation: "EVENT africa.civitas.news.article.published.v1"}</li>
 * </ul>
 *
 * <p>EGEN orchestre l'enchainement et jamais le contenu : {@link Http#path()}
 * et {@link Event#eventType()} restent des chaines opaques pour le moteur,
 * interpretees uniquement par le service cible (meme principe que
 * {@code TechnicalEvent.data}, docs/architecture/05-modele-de-domaine.md).</p>
 */
public sealed interface StepOperation {

    /** Reconstruit la forme canonique {@code "METHODE /chemin"} ou {@code "EVENT type"} (voir {@link #parse}). */
    String raw();

    record Http(String method, String path) implements StepOperation {

        private static final Set<String> VALID_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

        public Http {
            if (method == null || !VALID_METHODS.contains(method.toUpperCase())) {
                throw new IllegalArgumentException(
                        "StepOperation.Http.method invalide : \"" + method + "\" — attendu l'un de " + VALID_METHODS);
            }
            method = method.toUpperCase();
            if (path == null || path.isBlank() || !path.startsWith("/")) {
                throw new IllegalArgumentException(
                        "StepOperation.Http.path doit commencer par \"/\" (recu : \"" + path + "\")");
            }
        }

        @Override
        public String raw() {
            return method + " " + path;
        }
    }

    record Event(String eventType) implements StepOperation {

        public Event {
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("StepOperation.Event.eventType ne peut pas etre vide");
            }
        }

        @Override
        public String raw() {
            return "EVENT " + eventType;
        }
    }

    Pattern HTTP_PATTERN = Pattern.compile("^(GET|POST|PUT|PATCH|DELETE)\\s+(/\\S*)$", Pattern.CASE_INSENSITIVE);
    Pattern EVENT_PATTERN = Pattern.compile("^EVENT\\s+(\\S+)$");

    /**
     * Parse la forme textuelle du champ YAML {@code operation} — voir la
     * javadoc de l'interface pour les deux formes acceptees.
     *
     * @throws IllegalArgumentException si la chaine ne correspond a aucune
     *         des deux formes reconnues
     */
    static StepOperation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("StepOperation ne peut pas etre vide");
        }
        var eventMatch = EVENT_PATTERN.matcher(raw.trim());
        if (eventMatch.matches()) {
            return new Event(eventMatch.group(1));
        }
        var httpMatch = HTTP_PATTERN.matcher(raw.trim());
        if (httpMatch.matches()) {
            return new Http(httpMatch.group(1), httpMatch.group(2));
        }
        throw new IllegalArgumentException(
                "StepOperation illisible : \"" + raw + "\" — attendu \"METHODE /chemin\" ou \"EVENT type\"");
    }
}
