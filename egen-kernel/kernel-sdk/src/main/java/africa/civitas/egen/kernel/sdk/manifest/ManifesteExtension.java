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
 * manifeste declare exhaustivement ce qu'il emet et ecoute sur le Bus d'Evenements,
 * les Types de Ressource qu'il introduit, et les autres modules dont il depend. Le
 * moteur de plugins (kernel-plugin-engine, via PF4J) lit ce manifeste au demarrage ;
 * la decision d'autoriser effectivement l'activation reste entierement du ressort de
 * la logique metier de B2 — ce manifeste ne fait que decrire, jamais autoriser.
 *
 * <p>Ce manifeste ne declare volontairement rien de plus generique que ces quatre
 * categories : tout vocabulaire propre a un domaine metier (types de ressources
 * specifiques mis a part, deja neutres par nature) — taxonomie, lexique, ou toute
 * autre notion de classification — reste entierement la responsabilite du module
 * Niveau 2 qui la definit, jamais une categorie hardcodee ici. Le Kernel ne suppose
 * jamais quel vocabulaire un module metier a besoin d'etendre.
 *
 * <p>Toute instance de cette classe est valide par construction : le constructeur
 * canonique rejette immediatement un identifiant mal forme, une version non
 * semantique, une entree vide dans une liste, un doublon, ou une auto-dependance. Un
 * Manifeste invalide ne peut jamais exister en memoire.
 *
 * @param moduleId identifiant unique du module, en kebab-case
 * @param version version semantique du module (ex. {@code "1.4.0"})
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
        private final List<String> eventsEmitted = new ArrayList<>();
        private final List<String> eventsConsumed = new ArrayList<>();
        private final List<String> resourceTypesProvided = new ArrayList<>();
        private final List<String> dependencies = new ArrayList<>();

        private Builder(String moduleId, String version) {
            this.moduleId = moduleId;
            this.version = version;
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
                    eventsEmitted, eventsConsumed,
                    resourceTypesProvided, dependencies);
        }
    }
}
