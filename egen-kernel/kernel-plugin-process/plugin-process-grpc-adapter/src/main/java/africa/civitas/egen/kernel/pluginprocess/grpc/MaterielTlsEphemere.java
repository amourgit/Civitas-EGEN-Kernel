package africa.civitas.egen.kernel.pluginprocess.grpc;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Un certificat auto-signe ephemere, genere fraichement pour un seul lancement de
 * processus plugin — jamais reutilise entre deux lancements, jamais persiste sur
 * disque. L'hote en genere un pour lui-meme, transmis au processus plugin via son
 * environnement au lancement (voir {@code PluginProcessLauncher#lancer}) ; le
 * processus plugin en genere un autre pour lui-meme, dont le certificat complet est
 * rapporte a l'hote via {@code PluginProcessHandshake#certificatServeurBase64}.
 * Chaque partie ne fait confiance qu'a exactement l'unique certificat ainsi recu —
 * jamais a une autorite de certification partagee.
 *
 * <p>Genere via Bouncy Castle (licence MIT), jamais via {@code
 * io.netty.handler.ssl.util.SelfSignedCertificate} : sa propre Javadoc officielle
 * est explicite — reserve aux tests, generateur pseudo-aleatoire non securise pour
 * aller plus vite. L'utiliser ici aurait vide le mTLS de son sens.
 *
 * <p>Cle EC (courbe secp256r1, "P-256") plutot que RSA : generee fraichement a
 * chaque lancement de processus, une paire EC se genere nettement plus vite qu'une
 * paire RSA-2048 pour un niveau de securite equivalent — ce qui compte ici, pour un
 * materiel qui ne sert jamais plus d'une session.
 */
public final class MaterielTlsEphemere {

    /** Duree de validite genereuse pour une session de processus plugin, jamais pensee comme un credential durable. */
    private static final Duration DUREE_VALIDITE = Duration.ofHours(24);
    private static final String NOM_DISTINGUE = "CN=egen-plugin-process";
    private static final String ALGORITHME_CLE = "EC";
    private static final String COURBE = "secp256r1";
    private static final String ALGORITHME_SIGNATURE = "SHA256withECDSA";

    private final PrivateKey clePrivee;
    private final X509Certificate certificat;

    private MaterielTlsEphemere(PrivateKey clePrivee, X509Certificate certificat) {
        this.clePrivee = Objects.requireNonNull(clePrivee, "clePrivee ne peut pas etre nulle.");
        this.certificat = Objects.requireNonNull(certificat, "certificat ne peut pas etre nul.");
    }

    /** @throws MaterielTlsException si la generation echoue pour n'importe quelle raison cryptographique */
    public static MaterielTlsEphemere generer() {
        try {
            KeyPair pairesDeCles = genererPaireDeCles();
            X509Certificate certificat = autoSigner(pairesDeCles);
            return new MaterielTlsEphemere(pairesDeCles.getPrivate(), certificat);
        } catch (GeneralSecurityException | OperatorCreationException e) {
            throw new MaterielTlsException(
                    "Echec de la generation du materiel TLS ephemere : " + e.getMessage(), e);
        }
    }

    public PrivateKey clePrivee() {
        return clePrivee;
    }

    public X509Certificate certificat() {
        return certificat;
    }

    /** @return l'empreinte SHA-256 du certificat encode DER, en hexadecimal minuscule. Diagnostic uniquement — le protocole de handshake transporte desormais le certificat complet, jamais seulement cette empreinte. */
    public String empreinteSha256Hex() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] empreinte = digest.digest(certificat.getEncoded());
            return HexFormat.of().formatHex(empreinte);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new MaterielTlsException("Impossible de calculer l'empreinte du certificat.", e);
        }
    }

    /** @return le certificat encode DER puis Base64 sur une seule ligne — exactement le format que {@code PluginProcessHandshake} transporte. */
    public String certificatBase64() {
        try {
            return Base64.getEncoder().encodeToString(certificat.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new MaterielTlsException("Impossible d'encoder le certificat en Base64.", e);
        }
    }

    /**
     * @return le certificat encode au format PEM standard (en-tetes {@code BEGIN
     *         CERTIFICATE}/{@code END CERTIFICATE}, corps multi-lignes) — le format
     *         attendu par les constructeurs {@code keyManager}/{@code trustManager}
     *         bases sur un flux de grpc-java (io.grpc.TlsChannelCredentials,
     *         io.grpc.TlsServerCredentials). A ne jamais confondre avec le Base64
     *         sur une seule ligne, sans en-tetes, que transporte {@code
     *         PluginProcessHandshake} — deux encodages du meme certificat, pour deux
     *         usages distincts.
     */
    public String certificatPem() {
        return versPem(certificat);
    }

    /** @return la cle privee encodee au format PEM standard (PKCS#8), meme remarque que {@link #certificatPem()}. */
    public String clePriveePem() {
        return versPem(clePrivee);
    }

    private static String versPem(Object objetCryptographique) {
        StringWriter tampon = new StringWriter();
        try (JcaPEMWriter ecrivainPem = new JcaPEMWriter(tampon)) {
            ecrivainPem.writeObject(objetCryptographique);
        } catch (IOException e) {
            throw new MaterielTlsException(
                    "Echec d'encodage PEM de " + objetCryptographique.getClass().getSimpleName() + ".", e);
        }
        return tampon.toString();
    }

    private static KeyPair genererPaireDeCles() throws GeneralSecurityException {
        KeyPairGenerator generateur = KeyPairGenerator.getInstance(ALGORITHME_CLE);
        generateur.initialize(new ECGenParameterSpec(COURBE));
        return generateur.generateKeyPair();
    }

    private static X509Certificate autoSigner(KeyPair pairesDeCles)
            throws OperatorCreationException, GeneralSecurityException {
        X500Name sujetEtEmetteur = new X500Name(NOM_DISTINGUE);
        Instant maintenant = Instant.now();
        BigInteger numeroSerie = BigInteger.valueOf(maintenant.toEpochMilli());

        JcaX509v3CertificateBuilder constructeur = new JcaX509v3CertificateBuilder(
                sujetEtEmetteur,
                numeroSerie,
                Date.from(maintenant.minus(Duration.ofMinutes(5))),
                Date.from(maintenant.plus(DUREE_VALIDITE)),
                sujetEtEmetteur,
                pairesDeCles.getPublic());

        ContentSigner signataire = new JcaContentSignerBuilder(ALGORITHME_SIGNATURE)
                .build(pairesDeCles.getPrivate());

        X509CertificateHolder certificatHolder = constructeur.build(signataire);

        try {
            return new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider())
                    .getCertificate(certificatHolder);
        } catch (CertificateException e) {
            throw new MaterielTlsException("Echec de la conversion du certificat genere.", e);
        }
    }
}
