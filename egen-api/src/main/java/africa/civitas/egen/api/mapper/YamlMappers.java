package africa.civitas.egen.api.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;

/**
 * Le seul endroit où le Kernel lit un document YAML venu de l'extérieur (manifestes de services, définitions de
 * workflows). Les limites sont <b>explicites</b>, plus serrées que celles par défaut des bibliothèques, et testées
 * ({@code YamlMappersTest}) : constat K10 de docs/architecture/24-revue-de-securite-externe.md.
 *
 * <p><b>Limites</b> (docs/architecture/13-api-et-contrats.md, « Limites d'entrée ») :</p>
 * <ul>
 *   <li>taille : {@link #MAX_DOCUMENT_CODE_POINTS} caractères. Un manifeste ou une définition de workflow fait quelques
 *       Ko ; par défaut SnakeYAML accepte 3 Mo ;</li>
 *   <li>imbrication : {@link #MAX_NESTING_DEPTH} niveaux (Jackson accepte 1000 par défaut) ;</li>
 *   <li>alias de collections : {@link #MAX_ALIASES_FOR_COLLECTIONS} (défense contre la « bombe YAML ») ;</li>
 *   <li>clés dupliquées : <b>refusées</b>, comme les champs inconnus : le schéma est strict, une clé écrite deux fois
 *       est une dérive silencieuse (docs/architecture/06-service-manifest.md).</li>
 * </ul>
 *
 * <p>Toute violation, quelle que soit la couche qui la détecte, ressort en {@link IOException} : les ressources REST
 * répondent {@code 400}.</p>
 */
public final class YamlMappers {

    public static final int MAX_DOCUMENT_CODE_POINTS = 256 * 1024;
    public static final int MAX_NESTING_DEPTH = 32;
    public static final int MAX_ALIASES_FOR_COLLECTIONS = 10;

    private static final ObjectMapper MAPPER = newMapper();

    private YamlMappers() {
    }

    private static ObjectMapper newMapper() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(MAX_DOCUMENT_CODE_POINTS);
        loaderOptions.setNestingDepthLimit(MAX_NESTING_DEPTH);
        loaderOptions.setMaxAliasesForCollections(MAX_ALIASES_FOR_COLLECTIONS);
        loaderOptions.setAllowRecursiveKeys(false);
        // Jackson traduit ce réglage en StreamReadFeature.STRICT_DUPLICATE_DETECTION (YAMLFactoryBuilder.loaderOptions).
        loaderOptions.setAllowDuplicateKeys(false);

        YAMLFactory factory = YAMLFactory.builder()
                .loaderOptions(loaderOptions)
                // YAMLParser hérite de ParserBase, qui valide la profondeur à chaque ouverture d'objet ou de tableau.
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_NESTING_DEPTH).build())
                .build();
        return new YAMLMapper(factory);
    }

    /**
     * Lit un document YAML dans le type demandé, sous les limites ci-dessus.
     *
     * @throws IOException si le document est vide, trop grand, mal formé, trop imbriqué, porte une clé dupliquée, ou ne
     *                     correspond pas au type
     */
    public static <T> T read(String yaml, Class<T> type) throws IOException {
        if (yaml == null || yaml.isBlank()) {
            throw new YamlRejectedException("document YAML vide");
        }
        // Avant toute analyse : on ne passe pas un document énorme à l'analyseur pour le voir échouer ensuite.
        int codePoints = yaml.codePointCount(0, yaml.length());
        if (codePoints > MAX_DOCUMENT_CODE_POINTS) {
            throw new YamlRejectedException("document YAML trop grand : " + codePoints
                    + " caractères, maximum " + MAX_DOCUMENT_CODE_POINTS);
        }
        try {
            return MAPPER.readValue(yaml, type);
        } catch (JsonProcessingException e) {
            throw e; // déjà une IOException lisible : erreur de syntaxe, imbrication, doublon, champ inconnu...
        } catch (RuntimeException e) {
            // Une erreur de SnakeYAML qui ne serait pas enveloppée par Jackson ne doit jamais devenir un 500.
            throw new YamlRejectedException("document YAML refusé : " + e.getMessage(), e);
        }
    }
}
