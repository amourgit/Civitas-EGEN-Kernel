package africa.civitas.egen.kernel.pluginprocess;

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
 * PROTOCOL|SERVER-CERT}. Format propre a EGEN ici, pas une reproduction — quatre
 * champs suffisent tant qu'un seul transport (gRPC sur TCP local) et une seule
 * methode d'authentification (mTLS ephemere par lancement) existent :
 *
 * <pre>{@code EGEN-PLUGIN-PROCESS-1|<port>|<sha256-hex-du-certificat-serveur>}</pre>
 *
 * <p>Le premier segment porte a la fois un nom qui ne peut jamais se confondre avec
 * une ligne de log accidentelle et un numero de version de format — {@code
 * depuisLigne} refuse tout ce qui n'est pas exactement {@code EGEN-PLUGIN-PROCESS-1}
 * aujourd'hui, exactement comme le vermagic du noyau Linux refuse de charger un
 * module dont l'interface noyau ne correspond pas plutot que de tenter quand meme et
 * echouer de maniere imprevisible plus tard.
 *
 * @param port le port TCP local sur lequel le serveur RPC du plugin ecoute
 * @param certificatServeurSha256 l'empreinte SHA-256 (hexadecimal, minuscules, 64
 *                                  caracteres) du certificat mTLS ephemere que le
 *                                  plugin presentera — epingle par l'hote avant tout
 *                                  echange, jamais une autorite de certification
 *                                  partagee a etablir
 */
public record PluginProcessHandshake(int port, String certificatServeurSha256) {

    static final String MAGIQUE_ET_VERSION = "EGEN-PLUGIN-PROCESS-1";
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final String SEPARATEUR = "|";

    public PluginProcessHandshake {
        if (port < 1 || port > 65535) {
            throw new PluginProcessException("port doit etre compris entre 1 et 65535, recu : " + port);
        }
        Objects.requireNonNull(certificatServeurSha256, "certificatServeurSha256 ne peut pas etre nul.");
        if (!SHA256_HEX.matcher(certificatServeurSha256).matches()) {
            throw new PluginProcessException(
                    "certificatServeurSha256 doit etre 64 caracteres hexadecimaux minuscules "
                            + "(empreinte SHA-256), recu : " + certificatServeurSha256);
        }
    }

    /** @return la ligne complete a imprimer sur stdout, telle que {@link #depuisLigne} sait la relire. */
    public String ligne() {
        return MAGIQUE_ET_VERSION + SEPARATEUR + port + SEPARATEUR + certificatServeurSha256;
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
                    "Ligne de handshake invalide (attendu 'EGEN-PLUGIN-PROCESS-1|<port>|<sha256>'), recu : "
                            + ligne);
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
}
