package africa.civitas.egen.kernel.sdk.manifest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Le Manifeste d'Extension qu'un module metier publie pour s'accrocher au Kernel.
 *
 * <p>Rattache conceptuellement au Catalogue (Systeme B2, Souscription/Activation), ce
 * manifeste declare exhaustivement ce que le module ajoute au Lexique (Types de
 * Cellule, Mandats), ce qu'il emet et ecoute sur le Bus d'Evenements, les Types de
 * Ressource qu'il introduit, et les autres modules dont il depend. Le moteur de
 * plugins (kernel-plugin-engine, via PF4J) lit ce manifeste au demarrage ; la decision
 * d'autoriser effectivement l'activation reste entierement du ressort de la logique
 * metier de B2 — ce manifeste ne fait que decrire, jamais autoriser.
 *
 * <p>Toute instance de cette classe est valide par construction : le constructeur
 * canonique rejette immediatement un identifiant mal forme, une version non
 * semantique, une entree vide dans une liste, un doublon, ou une auto-dependance. Un
 * Manifeste invalide ne peut jamais exister en memoire.
 *
 * @param moduleId identifiant unique du module, en kebab-case
 * @param version version semantique du module (ex. {@code "1.4.0"})
 * @param cellTypesProvided Types de Cellule que ce module propose d'ajouter au Lexique
 * @param mandatesProvided Mandats que ce module propose d'ajouter au Lexique des Mandats
 * @param eventsEmitted noms de {@link africa.civitas.egen.kernel.sdk.event.EventType}
 *                       que ce module publie sur le Bus d'Evenements
 * @param eventsConsumed noms de {@link africa.civitas.egen.kernel.sdk.event.EventType}
 *                        que ce module ecoute
 * @param resourceTypesProvided Types de Ressource que ce module declare
 * @param dependencies identifiants d'autres modules requis pour que celui-ci fonctionne
 */
public record ManifesteExtension(
        String moduleId,
        String version,
        List<String> cellTypesProvided,
        List<String> mandatesProvided,
        List<String> eventsEmitted,
        List<String> eventsConsumed,
        List<String> resourceTypesProvided,
        List<String> dependencies) {

    private static final Pattern MODULE_ID_PATTERN =
            Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");
    private static final Pattern SEMVER_PATTERN =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z][0-9A-Za-z.-]*)?$");

    public ManifesteExtension {
        if (moduleId == null || moduleId.isBlank()) {
            throw new ManifestValidationException("moduleId ne peut pas etre vide.");
        }
        if (!MODULE_ID_PATTERN.matcher(moduleId).matches()) {
            throw new ManifestValidationException(
                    "moduleId doit etre en kebab-case (ex. 'reconnaissance-faciale'), recu : "
                            + moduleId);
        }
        if (version == null || !SEMVER_PATTERN.matcher(version).matches()) {
            throw new ManifestValidationException(
                    "version doit suivre le format semantique X.Y.Z (ex. '1.4.0'), recu : "
                            + version);
        }

        cellTypesProvided = normalize("cellTypesProvided", cellTypesProvided);
        mandatesProvided = normalize("mandatesProvided", mandatesProvided);
        eventsEmitted = normalize("eventsEmitted", eventsEmitted);
        eventsConsumed = normalize("eventsConsumed", eventsConsumed);
        resourceTypesProvided = normalize("resourceTypesProvided", resourceTypesProvided);
        dependencies = normalize("dependencies", dependencies);

        if (dependencies.contains(moduleId)) {
            throw new ManifestValidationException(
                    "Un module ne peut pas se declarer dependant de lui-meme : " + moduleId);
        }
    }

    private static List<String> normalize(String fieldName, List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new ManifestValidationException(
                        "La liste '" + fieldName + "' ne peut pas contenir d'entree vide.");
            }
            if (!seen.add(value)) {
                throw new ManifestValidationException(
                        "La liste '" + fieldName + "' contient un doublon : " + value);
            }
        }
        return List.copyOf(seen);
    }

    /** Point d'entree du constructeur fluide, la facon recommandee de construire un Manifeste. */
    public static Builder builder(String moduleId, String version) {
        return new Builder(moduleId, version);
    }

    /**
     * Constructeur fluide pour {@link ManifesteExtension} — plus lisible que l'appel
     * direct au constructeur canonique des que plusieurs listes doivent etre
     * renseignees. La validation complete a lieu dans {@link #build()}, au moment ou
     * le constructeur canonique du record est invoque.
     */
    public static final class Builder {
        private final String moduleId;
        private final String version;
        private final List<String> cellTypesProvided = new ArrayList<>();
        private final List<String> mandatesProvided = new ArrayList<>();
        private final List<String> eventsEmitted = new ArrayList<>();
        private final List<String> eventsConsumed = new ArrayList<>();
        private final List<String> resourceTypesProvided = new ArrayList<>();
        private final List<String> dependencies = new ArrayList<>();

        private Builder(String moduleId, String version) {
            this.moduleId = moduleId;
            this.version = version;
        }

        public Builder cellType(String cellType) {
            this.cellTypesProvided.add(cellType);
            return this;
        }

        public Builder mandate(String mandate) {
            this.mandatesProvided.add(mandate);
            return this;
        }

        public Builder emits(String eventTypeName) {
            this.eventsEmitted.add(eventTypeName);
            return this;
        }

        public Builder consumes(String eventTypeName) {
            this.eventsConsumed.add(eventTypeName);
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceTypesProvided.add(resourceType);
            return this;
        }

        public Builder dependsOn(String moduleId) {
            this.dependencies.add(moduleId);
            return this;
        }

        public ManifesteExtension build() {
            return new ManifesteExtension(
                    moduleId, version,
                    cellTypesProvided, mandatesProvided,
                    eventsEmitted, eventsConsumed,
                    resourceTypesProvided, dependencies);
        }
    }
}
