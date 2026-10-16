package africa.civitas.egen.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evenements que le service publie et consomme (voir
 * docs/architecture/06-service-manifest.md, bloc {@code events}). Le Kernel ne
 * lit jamais le contenu d'un evenement ({@code data} reste opaque,
 * docs/architecture/07-ports-et-adapters.md) : il ne s'interesse qu'au
 * {@code type}, qui sert de sujet de messagerie.
 *
 * <p>Un {@code type} devient un sujet (et un nom de flux) cote broker : il ne
 * doit donc contenir ni espace ni caractere generique ({@code *}, {@code >}),
 * sans quoi un manifeste pourrait capter ou polluer d'autres sujets.</p>
 */
public record EventSpec(List<Published> publishes, List<Consumed> consumes) {

    private static final Pattern TYPE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$");

    /** @param schemaRef reference opaque vers un schema (optionnelle) */
    public record Published(String type, String schemaRef) {
        public Published {
            type = requireType(type);
            schemaRef = blankToNull(schemaRef);
        }
    }

    /**
     * @param subscriptionGroup groupe de consommation (optionnel)
     * @param deadLetter politique d'echec (optionnelle)
     */
    public record Consumed(String type, String subscriptionGroup, DeadLetter deadLetter) {
        public Consumed {
            type = requireType(type);
            subscriptionGroup = blankToNull(subscriptionGroup);
        }
    }

    public record DeadLetter(int maxRetries, String strategy) {
        public DeadLetter {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("events.consumes.deadLetter.maxRetries doit etre >= 0");
            }
            if (strategy == null || strategy.isBlank()) {
                throw new IllegalArgumentException("events.consumes.deadLetter.strategy ne peut pas etre vide");
            }
        }
    }

    public EventSpec {
        publishes = publishes == null ? List.of() : List.copyOf(publishes);
        consumes = consumes == null ? List.of() : List.copyOf(consumes);
        rejectDuplicates(publishes.stream().map(Published::type).toList(), "events.publishes");
        rejectDuplicates(consumes.stream().map(Consumed::type).toList(), "events.consumes");
    }

    public static EventSpec none() {
        return new EventSpec(List.of(), List.of());
    }

    /** Tous les types touches par ce service, publies puis consommes, sans doublon. */
    public List<String> allTypes() {
        Set<String> types = new java.util.LinkedHashSet<>();
        publishes.forEach(p -> types.add(p.type()));
        consumes.forEach(c -> types.add(c.type()));
        return List.copyOf(types);
    }

    private static String requireType(String type) {
        if (type == null || !TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("events : type d'evenement invalide : \"" + type
                    + "\" — attendu des lettres, chiffres, '.', '_' ou '-' (jamais d'espace ni de caractere generique)");
        }
        return type;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void rejectDuplicates(List<String> types, String field) {
        Set<String> seen = new HashSet<>();
        for (String type : types) {
            if (!seen.add(type)) {
                throw new IllegalArgumentException(field + " : type declare deux fois : \"" + type + "\"");
            }
        }
    }
}
