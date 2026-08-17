package africa.civitas.egen.kernel.pluginprocess;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * La ligne que tout processus plugin doit imprimer sur sa sortie standard, une fois
 * et une seule, des que son serveur RPC est pret a recevoir des appels — jamais
 * avant, jamais deux fois. {@link PluginProcessLauncher} lit exactement cette ligne
 * pour savoir ou et comment se connecter ; tout ce que le plugin ecrit sur stdout
 * avant ou apres n'est jamais interprete comme le handshake.
 *
 * <p>Inspire du protocole de handshake de go-plugin (HashiCorp — Terraform, Vault,
 * Nomad), qui imprime une ligne au format
 * {@code CORE-PROTOCOL-VERSION|APP-PROTOCOL-VERSION|NETWORK-TYPE|NETWORK-ADDR|
 * PROTOCOL|SERVER-CERT}. Format propre a EGEN ici, pas une reproduction — trois
 * champs suffisent tant qu'un seul transport (gRPC sur TCP local) et une seule
 * methode d'authentification (mTLS ephemere par lancement) existent :
 *
 * <pre>{@code EGEN-PLUGIN-PROCESS-1|<port>|<certificat-DER-en-base64-sans-saut-de-ligne>}</pre>
 *
 * <p>Le certificat complet, pas seulement son empreinte : l'hote en a besoin tel
 * quel pour construire son gestionnaire de confiance gRPC (epingler exactement ce
 * certificat, jamais une autorite de certification partagee a etablir) — une
 * empreinte seule aurait exige un aller-retour supplementaire pour recuperer le
 * certificat qu'elle designe. Encode en Base64 brut (RFC 4648, pas le format PEM
 * multi-lignes) precisement parce que ce protocole tient sur une seule ligne.
 *
 * <p>Le premier segment porte a la fois un nom qui ne peut jamais se confondre avec
 * une ligne de log accidentelle et un numero de version de format — {@code
 * depuisLigne} refuse tout ce qui n'est pas exactement {@code EGEN-PLUGIN-PROCESS-1}
 * aujourd'hui, exactement comme le vermagic du noyau Linux refuse de charger un
 * module dont l'interface noyau ne correspond pas plutot que de tenter quand meme et
 * echouer de maniere imprevisible plus tard.
 *
 * @param port le port TCP local sur lequel le serveur RPC du plugin ecoute
 * @param certificatServeurBase64 le certificat X.509 (encodage DER) du plugin,
 *                                  encode en Base64 sans saut de ligne — jamais
 *                                  seulement son empreinte
 */
public record PluginProcessHandshake(int port, String certificatServeurBase64) {

    static final String MAGIQUE_ET_VERSION = "EGEN-PLUGIN-PROCESS-1";
    private static final String SEPARATEUR = "|";

    public PluginProcessHandshake {
        if (port < 1 || port > 65535) {
            throw new PluginProcessException("port doit etre compris entre 1 et 65535, recu : " + port);
        }
        Objects.requireNonNull(certificatServeurBase64, "certificatServeurBase64 ne peut pas etre nul.");
        byte[] certificatDer = decoderBase64(certificatServeurBase64);
        validerCertificatX509(certificatDer);
    }

    /** @return la ligne complete a imprimer sur stdout, telle que {@link #depuisLigne} sait la relire. */
    public String ligne() {
        return MAGIQUE_ET_VERSION + SEPARATEUR + port + SEPARATEUR + certificatServeurBase64;
    }

    /** @return le certificat decode en octets DER bruts, pret pour {@link CertificateFactory}. */
    public byte[] certificatServeurDer() {
        return decoderBase64(certificatServeurBase64);
    }

    /**
     * @throws PluginProcessException si {@code ligne} n'est pas un handshake EGEN
     *                                  valide pour le format de version actuellement
     *                                  supporte — jamais une tentative de deviner ou
     *                                  de degrader silencieusement vers un format
     *                                  plus ancien
     */
    public static PluginProcessHandshake depuisLigne(String ligne) {
        if (ligne == null) {
            throw new PluginProcessException("La ligne de handshake ne peut pas etre nulle.");
        }
        String[] segments = ligne.split(Pattern.quote(SEPARATEUR), -1);
        if (segments.length != 3 || !MAGIQUE_ET_VERSION.equals(segments[0])) {
            throw new PluginProcessException(
                    "Ligne de handshake invalide (attendu "
                            + "'EGEN-PLUGIN-PROCESS-1|<port>|<certificat-base64>'), recu : " + ligne);
        }

        int port;
        try {
            port = Integer.parseInt(segments[1]);
        } catch (NumberFormatException e) {
            throw new PluginProcessException(
                    "Port de handshake invalide (attendu un entier), recu : " + segments[1], e);
        }

        return new PluginProcessHandshake(port, segments[2]);
    }

    private static byte[] decoderBase64(String valeur) {
        try {
            return Base64.getDecoder().decode(valeur);
        } catch (IllegalArgumentException e) {
            throw new PluginProcessException(
                    "certificatServeurBase64 doit etre un Base64 valide (RFC 4648, sans saut de ligne) : "
                            + e.getMessage(), e);
        }
    }

    private static void validerCertificatX509(byte[] certificatDer) {
        try {
            CertificateFactory fabrique = CertificateFactory.getInstance("X.509");
            fabrique.generateCertificate(new java.io.ByteArrayInputStream(certificatDer));
        } catch (CertificateException e) {
            throw new PluginProcessException(
                    "certificatServeurBase64 ne decode pas vers un certificat X.509 valide : " + e.getMessage(), e);
        }
    }
}
