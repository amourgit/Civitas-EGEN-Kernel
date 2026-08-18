package africa.civitas.egen.kernel.pluginprocess.grpc;

import africa.civitas.egen.kernel.pluginengine.loader.PluginLoadException;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;
import africa.civitas.egen.kernel.pluginprocess.PluginProcessException;
import africa.civitas.egen.kernel.pluginprocess.PluginProcessHandle;
import africa.civitas.egen.kernel.pluginprocess.PluginProcessLauncher;
import africa.civitas.egen.kernel.pluginprocess.PontExtensionDistante;
import africa.civitas.egen.kernel.sdk.extension.ExtensionPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation de {@link PluginLoader} (kernel-plugin-engine) adossee a
 * l'isolation par processus separe, plutot qu'a l'isolation par classloader de
 * {@code Pf4jPluginLoader} — une alternative, pas un remplacement. Le contrat
 * {@code PluginLoader} existe precisement pour que les deux coexistent sans
 * qu'aucun consommateur ({@code PluginLifecycleManager}) n'ait a changer.
 *
 * <p><b>Decision volontairement non prise ici</b> : quel {@code PluginLoader}
 * kernel-bootstrap active reellement en production reste une decision de
 * deploiement, jamais tranchee dans ce commit — cette classe ne modifie ni
 * kernel-bootstrap ni sa configuration CDI. L'isolation par classloader (legere,
 * pas de processus additionnel a superviser) reste la voie par defaut ; celle-ci
 * s'active explicitement, module par module ou deploiement par deploiement,
 * quand l'isolation par processus est reellement necessaire.
 *
 * <p><b>Simplification assumee pour cette premiere livraison</b> : le processus
 * plugin herite integralement du classpath de l'hote (voir {@link
 * #construireCommande}), plutot que d'un classpath minimal qui lui serait propre.
 * Consequence directe : un plugin ne peut pas aujourd'hui apporter sa propre
 * version d'une bibliotheque deja presente cote hote — seule l'isolation memoire
 * et l'isolation de crash (un plugin qui plante n'affecte jamais le processus
 * hote) sont garanties par cette premiere version, pas l'isolation complete des
 * dependances. A verifier egalement en priorite au premier lancement reel : la
 * fiabilite de {@code System.getProperty("java.class.path")} pour refleter le
 * classpath d'execution effectif peut varier selon le mode de packaging Quarkus
 * (jar rapide, jar uber, mode developpement) — non verifiable depuis ce sandbox,
 * ce module n'ayant jamais pu compiler ni s'executer.
 *
 * <p>Classe volontairement simple (pas de bean CDI ici) — la meme convention que
 * {@code PluginLifecycleManager} : instanciable a la main, y compris dans les
 * tests.
 */
public final class RpcPluginLoader implements PluginLoader {

    private static final String BINAIRE_JAVA =
            System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    private static final String CLASSE_RUNTIME_PLUGIN = PluginProcessRuntime.class.getName();
    private static final String HOTE_LOCAL = "127.0.0.1";

    private final PluginProcessLauncher launcher;
    private final ObjectMapper objectMapper;
    private final Duration delaiAppel;
    private final Duration delaiArretPropre;
    private final Map<String, ModuleCharge> modulesCharges = new ConcurrentHashMap<>();

    /**
     * @param delaiHandshake combien de temps attendre le handshake d'un processus
     *                       plugin fraichement lance avant de considerer son
     *                       lancement en echec
     * @param delaiAppel delai borne applique a chaque appel gRPC individuel
     *                    (invocation d'extension, decouverte des points fournis)
     * @param delaiArretPropre combien de temps laisser un processus plugin se
     *                          terminer proprement avant de le forcer, au
     *                          dechargement
     */
    public RpcPluginLoader(
            Duration delaiHandshake, Duration delaiAppel, Duration delaiArretPropre, ObjectMapper objectMapper) {
        this.launcher = new PluginProcessLauncher(delaiHandshake);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper ne peut pas etre nul.");
        this.delaiAppel = requirePositif(delaiAppel, "delaiAppel");
        this.delaiArretPropre = requirePositif(delaiArretPropre, "delaiArretPropre");
    }

    @Override
    public List<ExtensionDecouverte> charger(String moduleId, Path cheminPlugin) {
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(cheminPlugin, "cheminPlugin ne peut pas etre nul.");
        if (modulesCharges.containsKey(moduleId)) {
            throw new PluginLoadException("Le module '" + moduleId + "' est deja charge.");
        }

        MaterielTlsEphemere materielHote = MaterielTlsEphemere.generer();
        PluginProcessHandle handle = lancerProcessus(moduleId, cheminPlugin, materielHote);
        GrpcAppelExtensionTransport transport = connecter(moduleId, handle, materielHote);

        List<ExtensionDecouverte> decouvertes;
        try {
            decouvertes = decouvrirExtensions(moduleId, transport);
        } catch (RuntimeException e) {
            transport.close();
            handle.arreter(delaiArretPropre);
            throw new PluginLoadException(
                    "Echec de la decouverte des extensions du module '" + moduleId + "' : " + e.getMessage(), e);
        }

        modulesCharges.put(moduleId, new ModuleCharge(handle, transport));
        return decouvertes;
    }

    @Override
    public void decharger(String moduleId) {
        ModuleCharge charge = modulesCharges.remove(moduleId);
        if (charge == null) {
            return;
        }
        charge.transport().close();
        charge.handle().arreter(delaiArretPropre);
    }

    @Override
    public boolean estCharge(String moduleId) {
        return modulesCharges.containsKey(moduleId);
    }

    @Override
    public List<String> modulesCharges() {
        return List.copyOf(modulesCharges.keySet());
    }

    private PluginProcessHandle lancerProcessus(String moduleId, Path cheminPlugin, MaterielTlsEphemere materielHote) {
        List<String> commande = construireCommande(cheminPlugin);
        Path repertoireTravail = cheminPlugin.toAbsolutePath().getParent();
        Map<String, String> variablesEnvironnement =
                Map.of(PluginProcessRuntime.VARIABLE_ENV_CERTIFICAT_HOTE, materielHote.certificatBase64());

        try {
            return launcher.lancer(commande, repertoireTravail, variablesEnvironnement);
        } catch (PluginProcessException e) {
            throw new PluginLoadException(
                    "Echec du lancement du processus pour le module '" + moduleId + "' : " + e.getMessage(), e);
        }
    }

    private GrpcAppelExtensionTransport connecter(String moduleId, PluginProcessHandle handle, MaterielTlsEphemere materielHote) {
        try {
            return GrpcAppelExtensionTransport.connecter(
                    HOTE_LOCAL, handle.handshake(), materielHote, objectMapper, delaiAppel);
        } catch (PluginProcessException e) {
            handle.arreter(delaiArretPropre);
            throw new PluginLoadException(
                    "Echec de connexion au processus du module '" + moduleId + "' : " + e.getMessage(), e);
        }
    }

    private static List<String> construireCommande(Path cheminPlugin) {
        String classpathHote = System.getProperty("java.class.path", "");
        String classpathComplet = classpathHote + File.pathSeparator + cheminPlugin;
        return List.of(BINAIRE_JAVA, "-cp", classpathComplet, CLASSE_RUNTIME_PLUGIN, cheminPlugin.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<ExtensionDecouverte> decouvrirExtensions(String moduleId, GrpcAppelExtensionTransport transport) {
        List<ExtensionDecouverte> decouvertes = new ArrayList<>();
        for (String pointExtensionNom : transport.listerPointsExtension()) {
            Class<?> classeBrute = chargerClassePointExtension(moduleId, pointExtensionNom);
            if (!ExtensionPoint.class.isAssignableFrom(classeBrute)) {
                throw new IllegalStateException(
                        "'" + pointExtensionNom + "' rapporte par le module '" + moduleId
                                + "' n'implemente pas ExtensionPoint.");
            }
            Class<? extends ExtensionPoint> point = (Class<? extends ExtensionPoint>) classeBrute;
            ExtensionPoint proxy = PontExtensionDistante.creerProxy(point, transport);
            // priority() n'est pas encore rapporte par le protocole (voir la limite
            // documentee dans PluginProcessRuntime) — valeur par defaut d'Extension#priority().
            decouvertes.add(new ExtensionDecouverte(point, proxy, 100, moduleId));
        }
        return decouvertes;
    }

    private static Class<?> chargerClassePointExtension(String moduleId, String nomBinaire) {
        try {
            return Class.forName(nomBinaire);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Point d'extension '" + nomBinaire + "' rapporte par le module '" + moduleId
                            + "' introuvable dans le classloader de l'hote.", e);
        }
    }

    private static Duration requirePositif(Duration valeur, String nom) {
        if (valeur == null || valeur.isNegative() || valeur.isZero()) {
            throw new IllegalArgumentException(nom + " doit etre strictement positif.");
        }
        return valeur;
    }

    private record ModuleCharge(PluginProcessHandle handle, GrpcAppelExtensionTransport transport) {
    }
}
