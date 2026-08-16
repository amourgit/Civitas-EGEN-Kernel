package africa.civitas.egen.kernel.pluginprocess;

import africa.civitas.egen.kernel.sdk.extension.ExtensionPoint;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * Construit, pour n'importe quel {@link ExtensionPoint}, un Proxy dynamique qui
 * relaie chaque appel de methode vers un {@link AppelExtensionTransport} — jamais
 * d'execution locale. C'est ce qui permet a {@code ExtensionDecouverte}
 * (kernel-plugin-engine, qui verifie {@code point.isInstance(instance)} a la
 * construction) de recevoir une instance valide sans que cette instance n'existe
 * reellement dans cette JVM.
 *
 * <p>Aucune connaissance de gRPC, de serialisation, ni du processus plugin
 * lui-meme ici : ce pont ne fait que la plomberie {@link Proxy}/{@link
 * InvocationHandler}, generique par construction — voir la description du module
 * {@code kernel-plugin-process} pour la raison de ce choix face au modele "contrats
 * RPC fixes par categorie de plugin" de go-plugin.
 *
 * <p>Propriete utile qui decoule directement de ce choix : l'interface du point
 * d'extension n'a besoin d'exister que du cote hote, pour construire ce Proxy — elle
 * n'a besoin d'aucun equivalent binaire partage avec le processus plugin, seulement
 * d'un contrat logique (nom de methode, types de parametres) que le transport sait
 * router correctement.
 */
public final class PontExtensionDistante {

    private PontExtensionDistante() {
    }

    /**
     * @param point le point d'extension a implementer par le proxy — toujours une
     *              interface, jamais une classe concrete (contrainte de {@link
     *              Proxy})
     * @param transport ce que chaque appel de methode doit reellement declencher
     * @return une instance qui verifie {@code point.isInstance(...)}, prete a etre
     *         enveloppee dans une {@code ExtensionDecouverte}
     * @throws PluginProcessException si {@code point} n'est pas une interface
     */
    @SuppressWarnings("unchecked")
    public static <T extends ExtensionPoint> T creerProxy(Class<T> point, AppelExtensionTransport transport) {
        Objects.requireNonNull(point, "point ne peut pas etre nul.");
        Objects.requireNonNull(transport, "transport ne peut pas etre nul.");
        if (!point.isInterface()) {
            throw new PluginProcessException(
                    "Un point d'extension distant doit etre une interface, recu : " + point.getName());
        }

        InvocationHandler gestionnaire = (proxy, methode, arguments) -> {
            if (methode.getDeclaringClass() == Object.class) {
                return switch (methode.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "PontExtensionDistante[" + point.getName() + "]";
                    default -> throw new PluginProcessException(
                            "Methode Object non geree par le pont d'extension distante : "
                                    + methode.getName());
                };
            }

            Object[] argumentsEffectifs = arguments == null ? new Object[0] : arguments;
            try {
                return transport.invoquer(point, methode, argumentsEffectifs);
            } catch (PluginProcessException e) {
                throw e;
            } catch (Exception e) {
                throw new PluginProcessException(
                        "Echec de l'appel distant '" + methode.getName() + "' sur le point d'extension "
                                + point.getName() + " : " + e.getMessage(), e);
            }
        };

        return (T) Proxy.newProxyInstance(point.getClassLoader(), new Class<?>[] {point}, gestionnaire);
    }
}
