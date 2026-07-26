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

    private static final EventType AFFECTATION_TERMINEE = new EventType("organisation.affectation.terminee");
    private static final EventType PERSONNE_CREEE = new EventType("identite.personne.creee");

    private final InMemoryEventBus bus = new InMemoryEventBus();

    private static EventEnvelope<String> unEvenement(EventType type) {
        return EventEnvelope.of(type, UUID.randomUUID(), "charge-utile-de-test");
    }

    @Test
    void publishingWithNoSubscribersDoesNothing() {
        bus.publier(unEvenement(AFFECTATION_TERMINEE));
    }

    @Test
    void aHandlerSubscribedToTheExactTypeReceivesAMatchingEvent() {
        List<EventEnvelope<String>> recus = new ArrayList<>();
        bus.souscrire("organisation", AFFECTATION_TERMINEE, (EventHandler<String>) recus::add);

        EventEnvelope<String> evenement = unEvenement(AFFECTATION_TERMINEE);
        bus.publier(evenement);

        assertEquals(1, recus.size());
        assertEquals(evenement, recus.get(0));
    }

    @Test
    void aHandlerSubscribedToADifferentExactTypeDoesNotReceiveTheEvent() {
        AtomicInteger appels = new AtomicInteger();
        bus.souscrire("identite", PERSONNE_CREEE, (EventHandler<String>) e -> appels.incrementAndGet());

        bus.publier(unEvenement(AFFECTATION_TERMINEE));

        assertEquals(0, appels.get());
    }

    @Test
    void aHandlerSubscribedByPrefixReceivesAnyMatchingSystemeOrigine() {
        AtomicInteger appels = new AtomicInteger();
        bus.souscrireParPrefixe("audit", "organisation", (EventHandler<String>) e -> appels.incrementAndGet());

        bus.publier(unEvenement(AFFECTATION_TERMINEE));
        bus.publier(unEvenement(new EventType("organisation.tutelle.etablie")));

        assertEquals(2, appels.get());
    }

    @Test
    void aHandlerSubscribedByPrefixDoesNotReceiveAnUnrelatedSystemeOrigine() {
        AtomicInteger appels = new AtomicInteger();
        bus.souscrireParPrefixe("audit", "organisation", (EventHandler<String>) e -> appels.incrementAndGet());

        bus.publier(unEvenement(PERSONNE_CREEE));

        assertEquals(0, appels.get());
    }

    @Test
    void multipleHandlersForTheSameTypeAreAllCalled() {
        AtomicInteger premier = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        bus.souscrire("module-a", AFFECTATION_TERMINEE, (EventHandler<String>) e -> premier.incrementAndGet());
        bus.souscrire("module-b", AFFECTATION_TERMINEE, (EventHandler<String>) e -> second.incrementAndGet());

        bus.publier(unEvenement(AFFECTATION_TERMINEE));

        assertEquals(1, premier.get());
        assertEquals(1, second.get());
    }

    @Test
    void aFailingHandlerNeverPreventsOtherHandlersFromBeingCalled() {
        AtomicInteger appelsHandlerSain = new AtomicInteger();
        bus.souscrire("module-fautif", AFFECTATION_TERMINEE, (EventHandler<String>) e -> {
            throw new RuntimeException("panne simulee");
        });
        bus.souscrire("module-sain", AFFECTATION_TERMINEE, (EventHandler<String>) e -> appelsHandlerSain.incrementAndGet());

        bus.publier(unEvenement(AFFECTATION_TERMINEE));

        assertEquals(1, appelsHandlerSain.get());
    }

    @Test
    void aFailingHandlerNeverPropagatesToThePublisher() {
        bus.souscrire("module-fautif", AFFECTATION_TERMINEE, (EventHandler<String>) e -> {
            throw new RuntimeException("panne simulee");
        });

        bus.publier(unEvenement(AFFECTATION_TERMINEE));
        // Aucune exception ne remonte jusqu'ici : c'est l'assertion elle-meme.
    }

    @Test
    void unsubscribingStopsFurtherDelivery() {
        AtomicInteger appels = new AtomicInteger();
        Abonnement abonnement = bus.souscrire("organisation", AFFECTATION_TERMINEE,
                (EventHandler<String>) e -> appels.incrementAndGet());

        bus.desabonner(abonnement);
        bus.publier(unEvenement(AFFECTATION_TERMINEE));

        assertEquals(0, appels.get());
    }

    @Test
    void desabonnerToutPourRemovesOnlyThatModulesSubscriptions() {
        AtomicInteger appelsA = new AtomicInteger();
        AtomicInteger appelsB = new AtomicInteger();
        bus.souscrire("module-a", AFFECTATION_TERMINEE, (EventHandler<String>) e -> appelsA.incrementAndGet());
        bus.souscrire("module-b", AFFECTATION_TERMINEE, (EventHandler<String>) e -> appelsB.incrementAndGet());

        int retirees = bus.desabonnerToutPour("module-a");
        bus.publier(unEvenement(AFFECTATION_TERMINEE));

        assertEquals(1, retirees);
        assertEquals(0, appelsA.get());
        assertEquals(1, appelsB.get());
    }

    @Test
    void desabonnerIsSafeToCallForAnAlreadyRemovedSubscription() {
        Abonnement abonnement = bus.souscrire("organisation", AFFECTATION_TERMINEE, (EventHandler<String>) e -> {
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
                () -> bus.souscrire("organisation", null, (EventHandler<String>) e -> {
                }));
    }

    @Test
    void rejectsABlankPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.souscrireParPrefixe("organisation", " ", (EventHandler<String>) e -> {
                }));
    }

    @Test
    void rejectsANullSubscriptionOnUnsubscribe() {
        assertThrows(IllegalArgumentException.class, () -> bus.desabonner(null));
    }

    @Test
    void subscribeReturnsAnAbonnementCarryingTheRequestingModuleId() {
        Abonnement abonnement = bus.souscrire("organisation", AFFECTATION_TERMINEE, (EventHandler<String>) e -> {
        });

        assertEquals("organisation", abonnement.moduleId());
        assertTrue(abonnement.description().contains(AFFECTATION_TERMINEE.name()));
    }
}
