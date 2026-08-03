package africa.civitas.egen.kernel.testsupport.postgres;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Ressource de test Postgres explicite, adossee a Testcontainers — pinnee sur
 * {@code postgres:16}, la meme version que {@code docker-compose.yml} a la racine
 * du depot.
 *
 * <p>Aucun module -impl livre a ce jour ne l'utilise : chacun s'appuie sur Quarkus
 * Dev Services, qui provisionne deja un Postgres ephemere automatiquement des que
 * {@code quarkus-jdbc-postgresql} est present et qu'aucune URL n'est configuree —
 * et ca fonctionne tres bien, comme le prouvent les modules deja livres. Cette
 * ressource sert un besoin different, pas encore rencontre dans ce depot : un
 * controle explicite de la version de l'image, ou un conteneur partage entre
 * plusieurs classes de test qui depasserait la portee d'un seul module. Utilisable
 * des aujourd'hui via {@code @QuarkusTestResource(PostgresTestResource.class)},
 * prete pour quand ce besoin se presentera.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String IMAGE = "postgres:16";

    private PostgreSQLContainer<?> conteneur;

    @Override
    public Map<String, String> start() {
        conteneur = new PostgreSQLContainer<>(DockerImageName.parse(IMAGE))
                .withDatabaseName("egen")
                .withUsername("egen")
                .withPassword("egen");
        conteneur.start();

        return Map.of(
                "quarkus.datasource.jdbc.url", conteneur.getJdbcUrl(),
                "quarkus.datasource.username", conteneur.getUsername(),
                "quarkus.datasource.password", conteneur.getPassword());
    }

    @Override
    public void stop() {
        if (conteneur != null) {
            conteneur.stop();
        }
    }
}
