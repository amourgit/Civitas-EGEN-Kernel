package africa.civitas.egen.kernel.eventbus.api;

import africa.civitas.egen.kernel.sdk.event.EventEnvelope;
import africa.civitas.egen.kernel.sdk.event.EventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryEventBusTest {

    private static final EventType ACTIVATION_CREEE = new EventType("module-registry.activation.creee");
    private static final EventType PERSONNE_CREEE = new EventType("identite.personne.creee");

    private final InMemoryEventBus bus = new InMemoryEventBus();

    private static EventEnvelope<String> unEvenement(EventType type) {
        return EventEnvelope.of(type, UUID.randomUUID(), "charge-utile-de-test");
    }

    @Test
    void publishingWithNoSubscribersDoesNothing() {
        bus.publier(unEvenement(ACTIVATION_CREEE));
    }

    @Test
    void aHandlerSubscribedToTheExactTypeReceivesAMatchingEvent() {
        List<EventEnvelope<String>> recus = new ArrayList<>();
        bus.souscrire("module-x", ACTIVATION_CREEE, (EventHandler<String>) recus::add);

        EventEnvelope<String> evenement = unEvenement(ACTIVATION_CREEE);
        bus.publier(evenement);

        assertEquals(1, recus.size());
        assertEquals(evenement, recus.get(0));
    }

    @Test
    void aHandlerSubscribedToADifferentExactTypeDoesNotReceiveTheEvent() {
        AtomicInteger appels = new AtomicInteger();
        bus.souscrire("identite", PERSONNE_CREEE, (EventHandler<String>) e -> appels.incrementAndGet());

        bus.publier(unEvenement(ACTIVATION_CREEE));

        assertEquals(0, appels.get());
    }

    @Test
    void aHandlerSubscribedByPrefixReceivesAnyMatchingSystemeOrigine() {
        AtomicInteger appels = new AtomicInteger();
        bus.souscrireParPrefixe("audit", "module-registry", (EventHandler<String>) e -> appels.incrementAndGet());

        bus.publier(unEvenement(ACTIVATION_CREEE));
        bus.publier(unEvenement(new EventType("module-registry.souscription.creee")));

        assertEquals(2, appels.get());
    }

    @Test
    void aHandlerSubscribedByPrefixDoesNotReceiveAnUnrelatedSystemeOrigine() {
        AtomicInteger appels = new AtomicInteger();
        bus.souscrireParPrefixe("audit", "module-registry", (EventHandler<String>) e -> appels.incrementAndGet());

        bus.publier(unEvenement(PERSONNE_CREEE));

        assertEquals(0, appels.get());
    }

    @Test
    void multipleHandlersForTheSameTypeAreAllCalled() {
        AtomicInteger premier = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        bus.souscrire("module-a", ACTIVATION_CREEE, (EventHandler<String>) e -> premier.incrementAndGet());
        bus.souscrire("module-b", ACTIVATION_CREEE, (EventHandler<String>) e -> second.incrementAndGet());

        bus.publier(unEvenement(ACTIVATION_CREEE));

        assertEquals(1, premier.get());
        assertEquals(1, second.get());
    }

    @Test
    void aFailingHandlerNeverPreventsOtherHandlersFromBeingCalled() {
        AtomicInteger appelsHandlerSain = new AtomicInteger();
        bus.souscrire("module-fautif", ACTIVATION_CREEE, (EventHandler<String>) e -> {
            throw new RuntimeException("panne simulee");
        });
        bus.souscrire("module-sain", ACTIVATION_CREEE, (EventHandler<String>) e -> appelsHandlerSain.incrementAndGet());

        bus.publier(unEvenement(ACTIVATION_CREEE));

        assertEquals(1, appelsHandlerSain.get());
    }

    @Test
    void aFailingHandlerNeverPropagatesToThePublisher() {
        bus.souscrire("module-fautif", ACTIVATION_CREEE, (EventHandler<String>) e -> {
            throw new RuntimeException("panne simulee");
        });

        bus.publier(unEvenement(ACTIVATION_CREEE));
        // Aucune exception ne remonte jusqu'ici : c'est l'assertion elle-meme.
    }

    @Test
    void unsubscribingStopsFurtherDelivery() {
        AtomicInteger appels = new AtomicInteger();
        Abonnement abonnement = bus.souscrire("module-x", ACTIVATION_CREEE,
                (EventHandler<String>) e -> appels.incrementAndGet());

        bus.desabonner(abonnement);
        bus.publier(unEvenement(ACTIVATION_CREEE));

        assertEquals(0, appels.get());
    }

    @Test
    void desabonnerToutPourRemovesOnlyThatModulesSubscriptions() {
        AtomicInteger appelsA = new AtomicInteger();
        AtomicInteger appelsB = new AtomicInteger();
        bus.souscrire("module-a", ACTIVATION_CREEE, (EventHandler<String>) e -> appelsA.incrementAndGet());
        bus.souscrire("module-b", ACTIVATION_CREEE, (EventHandler<String>) e -> appelsB.incrementAndGet());

        int retirees = bus.desabonnerToutPour("module-a");
        bus.publier(unEvenement(ACTIVATION_CREEE));

        assertEquals(1, retirees);
        assertEquals(0, appelsA.get());
        assertEquals(1, appelsB.get());
    }

    @Test
    void desabonnerIsSafeToCallForAnAlreadyRemovedSubscription() {
        Abonnement abonnement = bus.souscrire("module-x", ACTIVATION_CREEE, (EventHandler<String>) e -> {
        });

        bus.desabonner(abonnement);
        bus.desabonner(abonnement);
    }

    @Test
    void rejectsANullEventOnPublish() {
        assertThrows(IllegalArgumentException.class, () -> bus.publier(null));
    }

    @Test
    void rejectsANullTypeOnSubscribe() {
        assertThrows(NullPointerException.class,
                () -> bus.souscrire("module-x", null, (EventHandler<String>) e -> {
                }));
    }

    @Test
    void rejectsABlankPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.souscrireParPrefixe("module-x", " ", (EventHandler<String>) e -> {
                }));
    }

    @Test
    void rejectsANullSubscriptionOnUnsubscribe() {
        assertThrows(IllegalArgumentException.class, () -> bus.desabonner(null));
    }

    @Test
    void subscribeReturnsAnAbonnementCarryingTheRequestingModuleId() {
        Abonnement abonnement = bus.souscrire("module-x", ACTIVATION_CREEE, (EventHandler<String>) e -> {
        });

        assertEquals("module-x", abonnement.moduleId());
        assertTrue(abonnement.description().contains(ACTIVATION_CREEE.name()));
    }
}
