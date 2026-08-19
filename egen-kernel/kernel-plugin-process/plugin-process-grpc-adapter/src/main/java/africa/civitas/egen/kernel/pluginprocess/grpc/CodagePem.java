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
 *
 * <p><b>Egalement utilisee pour l'encodage des cles privees</b> ({@link
 * #clePriveeDerVersPem}), a la place de {@code
 * org.bouncycastle.openssl.jcajce.JcaPEMWriter#writeObject(PrivateKey)} : verifie par
 * execution reelle que ce dernier ecrit les cles EC au format historique OpenSSL
 * ("EC PRIVATE KEY", SEC1) plutot qu'au format PKCS#8 generique ("PRIVATE KEY") que
 * {@code PrivateKey#getEncoded()} produit deja et que Netty (utilise par grpc-java en
 * repli JDK pur, sans netty-tcnative) exige specifiquement — {@code
 * java.security.spec.InvalidKeySpecException: Neither RSA, DSA nor EC worked}
 * sinon. Encoder nous-memes le DER deja au bon format PKCS#8, sans repasser par le
 * choix de format de Bouncy Castle, elimine le probleme a la racine.
 */
final class CodagePem {

    private static final int LARGEUR_LIGNE = 64;

    private CodagePem() {
    }

    static String certificatDerVersPem(byte[] der) {
        return encoderPem(der, "CERTIFICATE");
    }

    /** @param pkcs8Der les octets tels que retournes par {@code PrivateKey#getEncoded()} — deja au format PKCS#8, jamais SEC1. */
    static String clePriveeDerVersPem(byte[] pkcs8Der) {
        return encoderPem(pkcs8Der, "PRIVATE KEY");
    }

    private static String encoderPem(byte[] der, String typePem) {
        String corps = Base64.getMimeEncoder(LARGEUR_LIGNE, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        return "-----BEGIN " + typePem + "-----\n" + corps + "\n-----END " + typePem + "-----\n";
    }
}
