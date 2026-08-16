package africa.civitas.egen.kernel.pluginprocess;

import java.lang.reflect.Method;

/**
 * Ce que doit reellement declencher un appel sur un point d'extension distant — le
 * seul point de contact entre {@link PontExtensionDistante} (pur JDK, generique) et
 * un transport concret (gRPC + mTLS dans plugin-process-grpc-adapter, ou tout autre
 * transport futur qui voudrait servir ce meme contrat sans qu'aucun consommateur
 * n'ait a changer — exactement la garantie que {@code PluginLoader}
 * (kernel-plugin-engine) offre deja pour le mecanisme de chargement lui-meme).
 *
 * <p>Ne remonte jamais une exception distante telle quelle : son type reel n'existe
 * generalement pas dans le classloader de l'appelant, puisqu'elle vient d'un autre
 * processus. Une implementation serieuse la reconstruit au mieux (message, nom de
 * type distant) dans une exception locale — jamais une tentative de deserialiser un
 * type Java arbitraire depuis le processus distant, ce qui rouvrirait exactement le
 * risque de deserialisation non fiable que le choix de gRPC/Protobuf plutot que Java
 * RMI visait a eviter.
 */
@FunctionalInterface
public interface AppelExtensionTransport {

    /**
     * @param pointExtension le point d'extension declare — utile a un transport qui
     *                       doit router vers la bonne extension distante quand
     *                       plusieurs sont enregistrees chez le meme processus
     * @param methode la methode appelee sur le proxy
     * @param arguments les arguments de l'appel, jamais nul (tableau vide si la
     *                  methode n'en prend aucun)
     * @return la valeur de retour, deja du type attendu par {@code methode}
     */
    Object invoquer(Class<?> pointExtension, Method methode, Object[] arguments) throws Exception;
}
