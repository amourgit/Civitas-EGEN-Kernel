package africa.civitas.egen.kernel.pluginprocess.grpc;

import org.junit.jupiter.api.Test;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterielTlsEphemereTest {

    @Test
    void generatesAValidSelfSignedCertificate() throws Exception {
        MaterielTlsEphemere materiel = MaterielTlsEphemere.generer();

        materiel.certificat().checkValidity();
        materiel.certificat().verify(materiel.certificat().getPublicKey());
    }

    @Test
    void twoSuccessiveGenerationsProduceDistinctCertificates() {
        MaterielTlsEphemere premier = MaterielTlsEphemere.generer();
        MaterielTlsEphemere second = MaterielTlsEphemere.generer();

        assertNotEquals(premier.certificatBase64(), second.certificatBase64());
    }

    @Test
    void certificatBase64RoundTripsToTheSameCertificate() throws Exception {
        MaterielTlsEphemere materiel = MaterielTlsEphemere.generer();

        byte[] der = Base64.getDecoder().decode(materiel.certificatBase64());
        X509Certificate relu = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(der));

        assertEquals(materiel.certificat(), relu);
    }

    @Test
    void certificatPemIsValidAndRoundTripsToTheSameCertificate() throws Exception {
        MaterielTlsEphemere materiel = MaterielTlsEphemere.generer();

        String pem = materiel.certificatPem();
        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----"));
        assertTrue(pem.trim().endsWith("-----END CERTIFICATE-----"));

        X509Certificate relu = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(pem.getBytes("US-ASCII")));
        assertEquals(materiel.certificat(), relu);
    }

    /**
     * Le test qui aurait attrape le bug reel trouve en integration : {@code
     * clePriveePem()} doit produire du PKCS#8 ("PRIVATE KEY"), jamais le format
     * legacy SEC1 ("EC PRIVATE KEY") que {@code JcaPEMWriter#writeObject(PrivateKey)}
     * produit silencieusement pour une cle EC — confirme par execution reelle avant
     * correction (Netty echouait au chargement avec "algid parse error, not a
     * sequence"), confirme absent apres correction par ce test.
     */
    @Test
    void clePriveePemUsesPkcs8FormatNeverLegacySec1Format() {
        MaterielTlsEphemere materiel = MaterielTlsEphemere.generer();

        String pem = materiel.clePriveePem();

        assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"),
                "attendu le format PKCS#8 generique, jamais 'EC PRIVATE KEY' (SEC1) : " + pem);
        assertTrue(pem.trim().endsWith("-----END PRIVATE KEY-----"));
    }

    @Test
    void clePriveePemBytesMatchGetEncodedExactly() {
        MaterielTlsEphemere materiel = MaterielTlsEphemere.generer();

        String corpsBase64 = materiel.clePriveePem()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decode = Base64.getDecoder().decode(corpsBase64);

        assertTrue(java.util.Arrays.equals(materiel.clePrivee().getEncoded(), decode));
    }

    @Test
    void empreinteSha256HexIsSixtyFourLowercaseHexCharacters() {
        MaterielTlsEphemere materiel = MaterielTlsEphemere.generer();

        assertTrue(materiel.empreinteSha256Hex().matches("^[0-9a-f]{64}$"));
    }

    @Test
    void generatingDoesNotThrow() {
        assertDoesNotThrow(MaterielTlsEphemere::generer);
    }
}
