package africa.civitas.egen.api.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 1 (docs/architecture/17) : les limites d'analyse YAML du constat K10 (docs/architecture/24), sans conteneur.
 *
 * <p>Toute violation doit ressortir en {@link IOException} : c'est le type que les ressources REST attrapent pour
 * répondre {@code 400}. Les documents « d'attaque » viennent de {@link YamlSamples} et sont bien formés : seule
 * la limite visée est violée. Chaque cas d'attaque a son pendant « juste en dessous de la limite », accepté : les
 * limites ne doivent pas être plus strictes que prévu.</p>
 */
class YamlMappersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(String yaml) throws IOException {
        return YamlMappers.read(yaml, Map.class);
    }

    @Test
    void anOrdinaryDocumentIsReadAsBefore() throws IOException {
        Map<String, Object> document = read(YamlSamples.valid());

        assertEquals(Map.of("name", "news-service", "version", "1.0.0"), document.get("metadata"));
        assertEquals(List.of("a", "b"), document.get("items"));
    }

    @Test
    void anEmptyOrBlankDocumentIsRefused() {
        assertThrows(YamlRejectedException.class, () -> read(null));
        assertThrows(YamlRejectedException.class, () -> read(""));
        assertThrows(YamlRejectedException.class, () -> read("   \n  "));
    }

    @Test
    void aDocumentOverTheSizeLimitIsRefusedBeforeBeingParsed() {
        YamlRejectedException e = assertThrows(YamlRejectedException.class,
                () -> read(YamlSamples.ofSize(YamlMappers.MAX_DOCUMENT_CODE_POINTS)));

        assertTrue(e.getMessage().contains("trop grand"), e.getMessage());
    }

    @Test
    void aDocumentJustUnderTheSizeLimitIsAccepted() throws IOException {
        assertEquals(1, read(YamlSamples.ofSize(YamlMappers.MAX_DOCUMENT_CODE_POINTS - 100)).size());
    }

    @Test
    void tooDeepBlockNestingIsRefusedAndAReasonableDepthIsAccepted() throws IOException {
        assertThrows(IOException.class, () -> read(YamlSamples.deepBlock(YamlMappers.MAX_NESTING_DEPTH + 8)));
        assertEquals(1, read(YamlSamples.deepBlock(10)).size());
    }

    @Test
    void tooDeepFlowNestingIsRefusedAndAReasonableDepthIsAccepted() throws IOException {
        assertThrows(IOException.class, () -> read(YamlSamples.deepFlow(200)));
        assertEquals(1, read(YamlSamples.deepFlow(10)).size());
    }

    @Test
    void aDuplicateKeyIsRefusedLikeAnUnknownField() {
        assertThrows(IOException.class, () -> read(YamlSamples.duplicateKey()));
    }

    @Test
    void anAliasBombCannotConsumeTheServer() {
        // Que Jackson refuse le document ou le lise sans développer les alias, il doit répondre vite et sans mémoire folle.
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try {
                read(YamlSamples.aliasBomb());
            } catch (IOException expected) {
                // refus : acceptable
            }
        });
    }

    @Test
    void aSelfReferencingAliasCannotLoopForever() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try {
                read(YamlSamples.selfReferencing());
            } catch (IOException expected) {
                // refus : acceptable
            }
        });
    }
}
