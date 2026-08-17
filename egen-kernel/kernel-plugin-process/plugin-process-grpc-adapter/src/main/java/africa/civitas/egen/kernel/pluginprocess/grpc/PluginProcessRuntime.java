package africa.civitas.egen.kernel.pluginprocess.grpc;

import africa.civitas.egen.kernel.pluginprocess.PluginProcessException;
import africa.civitas.egen.kernel.pluginprocess.PluginProcessHandshake;
import africa.civitas.egen.kernel.sdk.extension.Extension;
import africa.civitas.egen.kernel.sdk.extension.ExtensionPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Le point d'entree execute a l'interieur du processus plugin lui-meme — jamais
 * cote hote. Recoit le chemin du JAR plugin en seul argument, decouvre ses
 * extensions, demarre le serveur gRPC mTLS, et n'imprime la ligne de handshake sur
 * stdout qu'une fois pret a recevoir des appels — jamais avant, jamais plus d'une
 * fois. Voir {@link PluginProcessHandshake} pour le contrat exact que
 * {@code PluginProcessLauncher} (plugin-process-api) attend en retour.
 *
 * <p><b>Decouverte des extensions</b> : balayage du JAR volontairement identique a
 * celui de {@code Pf4jPluginLoader} (kernel-plugin-engine) — meme algorithme (classes
 * annotees {@link Extension}, verifiees contre {@link ExtensionPoint}, instanciees
 * via un constructeur sans argument), applique ici directement au classpath du
 * processus plutot qu'a un classloader isole PF4J : l'isolation est desormais au
 * niveau du processus lui-meme, plus besoin d'isolation de classloader en plus.
 *
 * <p><b>Simplification assumee pour cette premiere livraison</b> : une seule
 * implementation par point d'extension et par processus plugin — {@code priority()}
 * n'est pas encore pris en compte ici (a la difference de {@code Pf4jPluginLoader},
 * qui laisse {@code ExtensionRegistry} en decider). Si un plugin declare deux
 * classes {@link Extension} pour le meme point, la seconde balayee ecrase
 * silencieusement la premiere — a corriger avant qu'un module reel n'en ait besoin.
 */
public final class PluginProcessRuntime {

    /**
     * Nom de la variable d'environnement portant le certificat de l'hote (encodage
     * DER, Base64 sur une seule ligne — meme format que {@link
     * PluginProcessHandshake#certificatServeurBase64()}), transmise par {@code
     * PluginProcessLauncher#lancer}. Public : {@code RpcPluginLoader} (increment
     * suivant) doit ecrire exactement ce nom pour que ce runtime la retrouve.
     */
    public static final String VARIABLE_ENV_CERTIFICAT_HOTE = "EGEN_PLUGIN_HOST_CERT_BASE64";

    private PluginProcessRuntime() {
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: PluginProcessRuntime <chemin-du-jar-plugin>");
            System.exit(2);
            return;
        }
        Path cheminPlugin = Path.of(args[0]);

        String certificatHoteBase64 = System.getenv(VARIABLE_ENV_CERTIFICAT_HOTE);
        if (certificatHoteBase64 == null || certificatHoteBase64.isBlank()) {
            System.err.println(
                    "Variable d'environnement " + VARIABLE_ENV_CERTIFICAT_HOTE + " absente ou vide.");
            System.exit(2);
            return;
        }

        Map<String, Object> extensions = decouvrirExtensions(cheminPlugin);
        MaterielTlsEphemere materielPropre = MaterielTlsEphemere.generer();
        ServerCredentials credentials = construireCredentialsServeur(materielPropre, certificatHoteBase64);

        Server serveur = Grpc.newServerBuilderForPort(0, credentials)
                .addService(new ServiceExtensionDistanteImpl(extensions, new ObjectMapper()))
                .build();
        try {
            serveur.start();
        } catch (IOException e) {
            throw new PluginProcessException("Impossible de demarrer le serveur gRPC du processus plugin.", e);
        }

        PluginProcessHandshake handshake =
                new PluginProcessHandshake(serveur.getPort(), materielPropre.certificatBase64());
        PrintStream sortie = System.out;
        sortie.println(handshake.ligne());
        sortie.flush();

        Runtime.getRuntime().addShutdownHook(new Thread(serveur::shutdown, "egen-plugin-process-arret"));
        serveur.awaitTermination();
    }

    private static ServerCredentials construireCredentialsServeur(
            MaterielTlsEphemere materielPropre, String certificatHoteBase64) {
        byte[] certificatHoteDer;
        try {
            certificatHoteDer = Base64.getDecoder().decode(certificatHoteBase64);
        } catch (IllegalArgumentException e) {
            throw new PluginProcessException(
                    "Le certificat de l'hote transmis via " + VARIABLE_ENV_CERTIFICAT_HOTE
                            + " n'est pas du Base64 valide.", e);
        }

        try (InputStream certificatPropre = versFlux(materielPropre.certificatPem());
             InputStream clePriveePropre = versFlux(materielPropre.clePriveePem());
             InputStream certificatHote = versFlux(CodagePem.certificatDerVersPem(certificatHoteDer))) {
            return TlsServerCredentials.newBuilder()
                    .keyManager(certificatPropre, clePriveePropre)
                    .trustManager(certificatHote)
                    .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE)
                    .build();
        } catch (IOException e) {
            throw new PluginProcessException("Echec de construction des identifiants mTLS du serveur.", e);
        }
    }

    private static InputStream versFlux(String pem) {
        return new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII));
    }

    private static Map<String, Object> decouvrirExtensions(Path cheminPlugin) {
        Map<String, Object> extensions = new HashMap<>();
        try (JarFile jar = new JarFile(cheminPlugin.toFile())) {
            Enumeration<JarEntry> entrees = jar.entries();
            while (entrees.hasMoreElements()) {
                JarEntry entree = entrees.nextElement();
                if (entree.isDirectory() || !entree.getName().endsWith(".class")) {
                    continue;
                }
                String nomClasse = entree.getName()
                        .substring(0, entree.getName().length() - ".class".length())
                        .replace('/', '.');

                Class<?> classe;
                try {
                    classe = Class.forName(nomClasse);
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    continue;
                }

                Extension annotation = classe.getAnnotation(Extension.class);
                if (annotation == null) {
                    continue;
                }

                extensions.put(annotation.point().getName(), instancier(classe));
            }
        } catch (IOException e) {
            throw new PluginProcessException(
                    "Impossible de parcourir le JAR du plugin " + cheminPlugin + " : " + e.getMessage(), e);
        }
        return extensions;
    }

    private static Object instancier(Class<?> classe) {
        Object instance;
        try {
            instance = classe.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new PluginProcessException(
                    "Impossible d'instancier l'extension " + classe.getName()
                            + " (constructeur sans argument requis) : " + e.getMessage(), e);
        }
        if (!(instance instanceof ExtensionPoint)) {
            throw new PluginProcessException(
                    "La classe " + classe.getName()
                            + " est annotee @Extension mais n'implemente pas ExtensionPoint.");
        }
        return instance;
    }
}
