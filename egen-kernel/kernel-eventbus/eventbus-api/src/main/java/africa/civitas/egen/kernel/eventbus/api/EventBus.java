package africa.civitas.egen.kernel.eventbus.api;

import africa.civitas.egen.kernel.sdk.event.EventEnvelope;
import africa.civitas.egen.kernel.sdk.event.EventType;

/**
 * Le Bus d'Evenements de la plateforme (anatomie du Kernel, §4) — neutre de tout
 * protocole concret. Le Kernel ne connait que ce contrat ; {@link
 * africa.civitas.egen.kernel.eventbus.kafka.KafkaEventBusAdapter} (Kafka, Niveau 2,
 * vivant dans ce meme module par decision de la Charte v3 §A.6 — c'est une
 * infrastructure coeur que le Kernel demarre lui-meme, pas un plugin metier
 * optionnel) et {@link InMemoryEventBus} (le repli par defaut, Niveau 0, sans aucune
 * dependance externe) en sont deux implementations completement interchangeables :
 * aucun code qui publie ou souscrit ne change selon laquelle est active.
 *
 * <p>Principe directeur (anatomie du Kernel, §4) : le Kernel ne sait jamais qui va
 * lire un evenement, ni ce qu'il va en faire — il se contente de la diffusion et de
 * la trace de l'emission. Publier n'est jamais bloquant sur le traitement d'un
 * souscripteur, et l'echec d'un gestionnaire n'affecte jamais les autres ni
 * l'emetteur.
 */
public interface EventBus {

    /**
     * Publie {@code evenement}. La verite metier doit deja avoir ete ecrite (en
     * base, par le module emetteur) avant cet appel — ce bus ne porte jamais la
     * verite lui-meme, seulement son annonce (anatomie du Kernel, §4 : "il ecrit
     * d'abord la verite en PostgreSQL... ensuite, et seulement ensuite, il publie").
     *
     * @throws EventPublishException si le transport lui-meme echoue (courtier
     *                                 injoignable...) — jamais pour une raison liee a
     *                                 un souscripteur
     */
    void publier(EventEnvelope<?> evenement);

    /** Souscrit {@code moduleId} a un {@link EventType} exact. */
    Abonnement souscrire(String moduleId, EventType type, EventHandler<?> gestionnaire);

    /**
     * Souscrit {@code moduleId} a tous les types d'evenements dont le systeme
     * d'origine (voir {@link EventType#systemeOrigine()}) correspond a {@code
     * prefixeSystemeOrigine} — la souscription par prefixe que la convention de
     * nommage hierarchique des types d'evenements rend possible.
     */
    Abonnement souscrireParPrefixe(String moduleId, String prefixeSystemeOrigine, EventHandler<?> gestionnaire);

    /** Retire une souscription precise. Sans effet si elle n'existe plus. */
    void desabonner(Abonnement abonnement);

    /**
     * Retire toutes les souscriptions de {@code moduleId} — appele systematiquement
     * au dechargement d'un module (voir {@code
     * africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager}),
     * pour qu'aucun module absent ne continue de recevoir des evenements.
     *
     * @return le nombre de souscriptions retirees
     */
    int desabonnerToutPour(String moduleId);
}
