package africa.civitas.egen.kernel.pluginprocess.grpc;

import org.junit.jupiter.api.Test;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodagePemTest {

    @Test
    void certificatDerVersPemProducesAValidReparseableCertificate() throws Exception {
        MaterielTlsEphemere materiel = MaterielTlsEphemere.generer();
        byte[] der = materiel.certificat().getEncoded();

        String pem = CodagePem.certificatDerVersPem(der);

        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----"));
        assertTrue(pem.trim().endsWith("-----END CERTIFICATE-----"));

        X509Certificate relu = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(pem.getBytes("US-ASCII")));
        assertEquals(materiel.certificat(), relu);
    }

    @Test
    void clePriveeDerVersPemUsesThePkcs8Header() {
        String pem = CodagePem.clePriveeDerVersPem(new byte[] {1, 2, 3, 4});

        assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"));
        assertTrue(pem.trim().endsWith("-----END PRIVATE KEY-----"));
    }

    @Test
    void wrapsLinesAtSixtyFourCharacters() throws Exception {
        MaterielTlsEphemere materiel = MaterielTlsEphemere.generer();
        String pem = CodagePem.certificatDerVersPem(materiel.certificat().getEncoded());

        String[] lignes = pem.split("\n");
        // Toutes les lignes du corps (ni en-tete, ni pied, ni la derniere ligne
        // partielle) doivent faire exactement 64 caracteres.
        for (int i = 1; i < lignes.length - 2; i++) {
            assertEquals(64, lignes[i].length(), "ligne " + i + " : '" + lignes[i] + "'");
        }
    }
}
