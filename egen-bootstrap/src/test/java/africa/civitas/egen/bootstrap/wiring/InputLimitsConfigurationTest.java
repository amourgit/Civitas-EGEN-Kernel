package africa.civitas.egen.bootstrap.wiring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Niveau 1 (docs/architecture/17) : la limite de taille des corps de requête (constat K10 de
 * docs/architecture/24 ; docs/architecture/13, « Limites d'entrée ») est bien posée dans le fichier de
 * configuration réel du Kernel, sans conteneur. Le comportement HTTP (réponse 413) est testé dans egen-api
 * par {@code ServiceResourceInputLimitsTest}, avec la même valeur.
 */
class InputLimitsConfigurationTest {

    @Test
    void theRequestBodyLimitIsOneMebibyteInTheRealConfigurationFile() throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(in, "application.properties doit être sur le classpath de test");
            Properties properties = new Properties();
            properties.load(in);
            assertEquals("1M", properties.getProperty("quarkus.http.limits.max-body-size"),
                    "la limite de corps de requête doit être explicite (K10) : 1M, la valeur écrite dans le document 13");
        }
    }
}
