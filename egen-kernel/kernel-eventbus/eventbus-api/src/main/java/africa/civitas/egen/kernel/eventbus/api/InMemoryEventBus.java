package africa.civitas.egen.kernel.eventbus.api;

import africa.civitas.egen.kernel.sdk.event.EventEnvelope;
import africa.civitas.egen.kernel.sdk.event.EventType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation de reference d'{@link EventBus}, sans aucune dependance externe —
 * le repli Niveau 0 toujours disponible, avant meme qu'un courtier externe (Kafka,
 * NATS) ne soit joignable ou configure. Publication synchrone, dans le thread
 * appelant : suffisant pour un seul processus, mais {@link
 * africa.civitas.egen.kernel.eventbus.kafka.KafkaEventBusAdapter} reste necessaire
 * des qu'un evenement doit franchir une frontiere de processus.
 *
 * <p>Thread-safe. Un gestionnaire qui leve une exception est isole : logguee (via
 * {@link System#err} faute d'un framework de journalisation impose a ce niveau),
 * jamais propagee — ni a l'emetteur, ni aux autres gestionnaires du meme evenement.
 */
public final class InMemoryEventBus implements EventBus {

    private record SouscriptionInterne(
            Abonnement abonnement, EventType typeExact, String prefixe, EventHandler<Object> gestionnaire) {
    }

    private final Map<UUID, SouscriptionInterne> souscriptions = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public void publier(EventEnvelope<?> evenement) {
        if (evenement == null) {
            throw new IllegalArgumentException("evenement ne peut pas etre nul.");
        }
        for (SouscriptionInterne souscription : souscriptions.values()) {
            boolean correspond = evenement.type().equals(souscription.typeExact())
                    || (souscription.prefixe() != null
                            && evenement.type().systemeOrigine().equals(souscription.prefixe()));
            if (!correspond) {
                continue;
            }
            try {
                ((EventHandler<Object>) souscription.gestionnaire())
                        .traiter((EventEnvelope<Object>) evenement);
            } catch (RuntimeException e) {
                System.err.println("EventBus : le gestionnaire de l'abonnement "
                        + souscription.abonnement().id() + " (module '" + souscription.abonnement().moduleId()
                        + "') a leve une exception en traitant " + evenement.type().name() + " : "
                        + e.getMessage());
            }
        }
    }

    @Override
    public Abonnement souscrire(String moduleId, EventType type, EventHandler<?> gestionnaire) {
        Objects.requireNonNull(type, "type ne peut pas etre nul.");
        Objects.requireNonNull(gestionnaire, "gestionnaire ne peut pas etre nul.");
        Abonnement abonnement = new Abonnement(UUID.randomUUID(), moduleId, "type exact : " + type.name());
        enregistrer(new SouscriptionInterne(abonnement, type, null, castGestionnaire(gestionnaire)));
        return abonnement;
    }

    @Override
    public Abonnement souscrireParPrefixe(String moduleId, String prefixeSystemeOrigine, EventHandler<?> gestionnaire) {
        if (prefixeSystemeOrigine == null || prefixeSystemeOrigine.isBlank()) {
            throw new IllegalArgumentException("prefixeSystemeOrigine ne peut pas etre vide.");
        }
        Objects.requireNonNull(gestionnaire, "gestionnaire ne peut pas etre nul.");
        Abonnement abonnement = new Abonnement(
                UUID.randomUUID(), moduleId, "prefixe : " + prefixeSystemeOrigine + ".*");
        enregistrer(new SouscriptionInterne(abonnement, null, prefixeSystemeOrigine, castGestionnaire(gestionnaire)));
        return abonnement;
    }

    @Override
    public void desabonner(Abonnement abonnement) {
        if (abonnement == null) {
            throw new IllegalArgumentException("abonnement ne peut pas etre nul.");
        }
        souscriptions.remove(abonnement.id());
    }

    @Override
    public int desabonnerToutPour(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        int[] compteur = {0};
        souscriptions.entrySet().removeIf(entree -> {
            boolean estDuModule = entree.getValue().abonnement().moduleId().equals(moduleId);
            if (estDuModule) {
                compteur[0]++;
            }
            return estDuModule;
        });
        return compteur[0];
    }

    private void enregistrer(SouscriptionInterne souscription) {
        souscriptions.put(souscription.abonnement().id(), souscription);
    }

    @SuppressWarnings("unchecked")
    private static EventHandler<Object> castGestionnaire(EventHandler<?> gestionnaire) {
        return (EventHandler<Object>) gestionnaire;
    }
}
