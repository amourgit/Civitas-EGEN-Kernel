package africa.civitas.egen.kernel.pluginengine.manifest;

import africa.civitas.egen.kernel.sdk.manifest.ManifestValidationException;
import africa.civitas.egen.kernel.sdk.manifest.ManifesteExtension;

import java.util.List;
import java.util.Map;

/**
 * Lit un {@link ManifestSource} et construit un {@link ManifesteExtension} valide, ou
 * echoue explicitement. Aucune tolerance implicite : un champ obligatoire absent
 * (moduleId, version) leve {@link ManifestReadException} avant meme d'atteindre le
 * constructeur du Manifeste ; un champ present mais mal forme est laisse au
 * constructeur canonique de {@link ManifesteExtension}, qui leve {@link
 * ManifestValidationException} — cette classe ne duplique jamais une regle de
 * validation deja portee par kernel-sdk.
 *
 * <p>Les deux exceptions convergent vers le meme traitement chez l'appelant
 * (PluginLifecycleManager) : un echec de lecture ou de construction du Manifeste
 * declenche systematiquement {@link
 * africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyauQuestion#ECHEC_CONSTRUCTION_MANIFESTE},
 * toujours un refus.
 */
public final class ManifestReader {

    private static final String CLE_MODULE_ID = "moduleId";
    private static final String CLE_VERSION = "version";
    private static final String CLE_EVENTS_EMITTED = "eventsEmitted";
    private static final String CLE_EVENTS_CONSUMED = "eventsConsumed";
    private static final String CLE_RESOURCE_TYPES = "resourceTypesProvided";
    private static final String CLE_DEPENDENCIES = "dependencies";

    /**
     * @throws ManifestReadException si le {@code moduleId} ou la {@code version} sont
     *                                absents des donnees brutes, ou si la source
     *                                elle-meme echoue a produire des donnees
     * @throws ManifestValidationException si les donnees, bien que presentes, ne
     *                                       forment pas un Manifeste valide (voir
     *                                       {@link ManifesteExtension})
     */
    public ManifesteExtension lire(ManifestSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source ne peut pas etre nulle.");
        }

        Map<String, String> donnees = source.donneesBrutes();

        String moduleId = exiger(donnees, CLE_MODULE_ID);
        String version = exiger(donnees, CLE_VERSION);

        return new ManifesteExtension(
                moduleId,
                version,
                listeDe(donnees, CLE_EVENTS_EMITTED),
                listeDe(donnees, CLE_EVENTS_CONSUMED),
                listeDe(donnees, CLE_RESOURCE_TYPES),
                listeDe(donnees, CLE_DEPENDENCIES));
    }

    private static String exiger(Map<String, String> donnees, String cle) {
        String valeur = donnees.get(cle);
        if (valeur == null || valeur.isBlank()) {
            throw new ManifestReadException(
                    "Le Manifeste est incomplet : le champ obligatoire '" + cle + "' est absent.");
        }
        return valeur;
    }

    private static List<String> listeDe(Map<String, String> donnees, String cle) {
        String valeur = donnees.get(cle);
        if (valeur == null || valeur.isBlank()) {
            return List.of();
        }
        return List.of(valeur.split("\\s*,\\s*"));
    }
}
