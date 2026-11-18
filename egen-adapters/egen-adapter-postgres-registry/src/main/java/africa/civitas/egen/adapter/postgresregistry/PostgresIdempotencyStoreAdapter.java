package africa.civitas.egen.adapter.postgresregistry;

import africa.civitas.egen.application.port.IdempotencyStoreException;
import africa.civitas.egen.application.port.IdempotencyStorePort;
import africa.civitas.egen.application.port.StoredResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stockage PostgreSQL des reponses rejouables : plusieurs exemplaires du Kernel qui partagent la meme base
 * partagent aussi leurs cles d'idempotence (docs/architecture/13, « Idempotence » ; phase P0, etape P0-3).
 *
 * <p>La cle de cache (appelant, methode, chemin, valeur de l'en-tete) peut etre longue : elle est reduite a son
 * empreinte SHA-256 (64 caracteres hexadecimaux) avant d'etre utilisee comme cle primaire, car un index PostgreSQL
 * refuse les valeurs trop grandes. Le module ne connait que JDBC standard, jamais Quarkus (garde-fou n6,
 * docs/architecture/02).</p>
 */
public final class PostgresIdempotencyStoreAdapter implements IdempotencyStorePort {

    /** Nombre maximal de reponses gardees par defaut (docs/architecture/13, « Idempotence »). */
    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    private static final TypeReference<Map<String, List<String>>> HEADERS_TYPE = new TypeReference<>() { };

    private final DataSource dataSource;
    private final int maxEntries;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PostgresIdempotencyStoreAdapter(DataSource dataSource) {
        this(dataSource, DEFAULT_MAX_ENTRIES);
    }

    public PostgresIdempotencyStoreAdapter(DataSource dataSource, int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries doit etre superieur ou egal a 1 (recu : " + maxEntries + ")");
        }
        this.dataSource = dataSource;
        this.maxEntries = maxEntries;
        createSchemaIfAbsent();
    }

    @Override
    public Optional<StoredResponse> find(String cacheKey, Instant now) {
        String sql = "SELECT status, media_type, body, headers_json FROM egen_idempotency_response "
                + "WHERE key_hash = ? AND expires_at > ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash(cacheKey));
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Map<String, List<String>> headers = objectMapper.readValue(rs.getString("headers_json"), HEADERS_TYPE);
                return Optional.of(new StoredResponse(rs.getInt("status"), rs.getString("media_type"),
                        rs.getString("body"), headers));
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new IdempotencyStoreException("Lecture de la reponse rejouable impossible : " + e.getMessage(), e);
        }
    }

    @Override
    public boolean save(String cacheKey, StoredResponse response, Instant now, Instant expiresAt) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement purge = connection.prepareStatement(
                    "DELETE FROM egen_idempotency_response WHERE expires_at <= ?")) {
                purge.setTimestamp(1, Timestamp.from(now));
                purge.executeUpdate();
            }
            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT count(*) FROM egen_idempotency_response");
                 ResultSet rs = count.executeQuery()) {
                rs.next();
                if (rs.getLong(1) >= maxEntries) {
                    return false;
                }
            }
            String sql = "INSERT INTO egen_idempotency_response "
                    + "(key_hash, status, media_type, body, headers_json, stored_at, expires_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (key_hash) DO NOTHING";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                insert.setString(1, hash(cacheKey));
                insert.setInt(2, response.status());
                insert.setString(3, response.mediaType());
                insert.setString(4, response.body());
                insert.setString(5, objectMapper.writeValueAsString(response.headers()));
                insert.setTimestamp(6, Timestamp.from(now));
                insert.setTimestamp(7, Timestamp.from(expiresAt));
                return insert.executeUpdate() == 1;
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new IdempotencyStoreException("Ecriture de la reponse rejouable impossible : " + e.getMessage(), e);
        }
    }

    private static String hash(String cacheKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cacheKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 est garanti par toute machine virtuelle Java", e);
        }
    }

    private void createSchemaIfAbsent() {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS egen_idempotency_response ("
                        + "key_hash VARCHAR(64) PRIMARY KEY, "
                        + "status INT NOT NULL, "
                        + "media_type VARCHAR(255), "
                        + "body TEXT, "
                        + "headers_json TEXT NOT NULL, "
                        + "stored_at TIMESTAMPTZ NOT NULL, "
                        + "expires_at TIMESTAMPTZ NOT NULL)",
                "CREATE INDEX IF NOT EXISTS egen_idempotency_response_expires_idx "
                        + "ON egen_idempotency_response (expires_at)"
        };
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IdempotencyStoreException("Creation du schema d'idempotence impossible : " + e.getMessage(), e);
        }
    }
}
