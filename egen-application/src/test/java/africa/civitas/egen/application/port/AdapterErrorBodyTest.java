package africa.civitas.egen.application.port;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 1 (docs/architecture/17) : la borne appliquee aux corps d'erreur des systemes externes (constat K8,
 * docs/architecture/24 ; docs/architecture/07, « Messages d'erreur des adapters HTTP »).
 */
class AdapterErrorBodyTest {

    private static final int MAX = AdapterErrorBody.MAX_LENGTH;

    @Test
    void theLimitIsFiveHundredCharactersAsWrittenInTheDocumentation() {
        assertEquals(500, MAX);
        assertEquals(" [tronque]", AdapterErrorBody.TRUNCATION_MARK);
    }

    @Test
    void aShortBodyIsKeptUnchanged() {
        String body = "{\"kind\":\"Status\",\"reason\":\"Forbidden\",\"message\":\"deployments.apps is forbidden\"}";
        assertEquals(body, AdapterErrorBody.bounded(body));
    }

    @Test
    void aBodyOfExactlyTheLimitIsKeptUnchanged() {
        String body = "a".repeat(MAX);
        assertEquals(body, AdapterErrorBody.bounded(body));
    }

    @Test
    void aBodyOneCharacterOverTheLimitIsCutAndMarked() {
        String bounded = AdapterErrorBody.bounded("a".repeat(MAX) + "b");
        assertEquals("a".repeat(MAX) + AdapterErrorBody.TRUNCATION_MARK, bounded);
        assertFalse(bounded.contains("b"), "le caractere au-dela de la limite ne doit pas apparaitre");
    }

    @Test
    void aVeryLongBodyKeepsOnlyItsFirstCharactersPlusTheMark() {
        String bounded = AdapterErrorBody.bounded("x".repeat(1_000_000));
        assertEquals(MAX + AdapterErrorBody.TRUNCATION_MARK.length(), bounded.length());
        assertTrue(bounded.endsWith(AdapterErrorBody.TRUNCATION_MARK));
    }

    @Test
    void anAbsentBodyBecomesAnEmptyStringAndNeverTheWordNull() {
        assertEquals("", AdapterErrorBody.bounded(null));
        assertEquals("", AdapterErrorBody.bounded(""));
    }

    @Test
    void aCharacterOutsideTheBasicPlaneIsNeverCutInTwo() {
        // « 😀 » (U+1F600) occupe deux unites UTF-16 : on place sa premiere moitie en derniere position gardee.
        String emoji = new String(Character.toChars(0x1F600));
        String body = "a".repeat(MAX - 1) + emoji + "fin";
        String bounded = AdapterErrorBody.bounded(body);
        assertEquals("a".repeat(MAX - 1) + AdapterErrorBody.TRUNCATION_MARK, bounded);
        assertFalse(Character.isHighSurrogate(bounded.charAt(MAX - 2)));
    }
}
