package africa.civitas.egen.kernel.pluginprocess.grpc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Conversion minimale DER vers PEM, pure JDK — pour les cas ou seuls des octets DER
 * bruts sont disponibles (le certificat distant recu par handshake, ou par variable
 * d'environnement), jamais un objet {@code X509Certificate} deja construit. La ou
 * {@code MaterielTlsEphemere#certificatPem()} ne s'applique pas, puisqu'il opere sur
 * son propre certificat, deja un objet Java.
 *
 * <p>Les constructeurs bases sur un flux de grpc-java ({@code
 * TlsChannelCredentials}/{@code TlsServerCredentials}) attendent systematiquement du
 * PEM, y compris pour {@code trustManager} — jamais du DER brut, meme si {@link
 * java.security.cert.CertificateFactory} sait techniquement lire les deux formats
 * indifferemment. Uniformiser sur PEM partout evite d'avoir a se souvenir, cas par
 * cas, lequel des deux formats un appel donne accepte reellement.
 */
final class CodagePem {

    private static final String EN_TETE = "-----BEGIN CERTIFICATE-----";
    private static final String PIED = "-----END CERTIFICATE-----";
    private static final int LARGEUR_LIGNE = 64;

    private CodagePem() {
    }

    static String certificatDerVersPem(byte[] der) {
        String corps = Base64.getMimeEncoder(LARGEUR_LIGNE, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        return EN_TETE + "\n" + corps + "\n" + PIED + "\n";
    }
}
