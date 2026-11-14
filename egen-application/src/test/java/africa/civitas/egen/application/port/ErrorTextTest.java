package africa.civitas.egen.application.port;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Niveau 1 (docs/architecture/17) : le texte d'une exception d'adapter ne contient jamais « null »
 * (ecart I11, docs/architecture/07, « Erreurs reseau »).
 */
class ErrorTextTest {

    @Test
    void aMessageIsKeptUnchanged() {
        assertEquals("Connection refused", ErrorText.of(new ConnectException("Connection refused")));
    }

    @Test
    void anExceptionWithoutMessageGivesItsClassName() {
        assertEquals("java.net.ConnectException", ErrorText.of(new ConnectException()));
    }

    @Test
    void aBlankMessageIsTreatedAsMissing() {
        assertEquals("java.io.IOException", ErrorText.of(new IOException("   ")));
    }

    @Test
    void anEmptyMessageWithACauseGivesTheNameThenTheCause() {
        IOException error = new IOException((String) null, new ConnectException("Connection refused"));
        assertEquals("java.io.IOException (cause : Connection refused)", ErrorText.of(error));
    }

    @Test
    void emptyCausesAreFollowedAtMostThreeLevels() {
        Throwable chain = new RuntimeException();
        for (int i = 0; i < 6; i++) {
            chain = new RuntimeException((String) null, chain);
        }
        String text = ErrorText.of(chain);
        assertEquals(4, text.split("java.lang.RuntimeException", -1).length - 1);
    }

    @Test
    void aMissingExceptionGivesAFixedText() {
        assertEquals("cause inconnue", ErrorText.of(null));
    }

    @Test
    void theWordNullNeverAppears() {
        assertFalse(ErrorText.of(new ConnectException()).contains("null"));
        assertFalse(ErrorText.of(new IOException((String) null, new ConnectException())).contains("null"));
    }
}
