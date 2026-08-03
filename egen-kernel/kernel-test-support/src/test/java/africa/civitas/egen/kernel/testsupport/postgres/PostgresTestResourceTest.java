package africa.civitas.egen.kernel.testsupport.postgres;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie que {@link PostgresTestResource} fournit reellement une connexion Postgres
 * utilisable — pas seulement qu'elle demarre sans exception.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class PostgresTestResourceTest {

    @Inject
    DataSource dataSource;

    @Test
    void providesARealUsablePostgresConnection() throws Exception {
        try (Connection connexion = dataSource.getConnection();
             Statement instruction = connexion.createStatement();
             ResultSet resultat = instruction.executeQuery("SELECT 1")) {

            assertTrue(resultat.next());
            assertEquals(1, resultat.getInt(1));
            assertTrue(connexion.getMetaData().getURL().contains("postgresql"));
        }
    }
}
