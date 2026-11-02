package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkQueueTest {

    private static final ServiceId ID = ServiceId.of("news-service");

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void deduplicatesRepeatedEnqueuesOfTheSameKey() throws InterruptedException {
        WorkQueue queue = new WorkQueue();

        queue.enqueue(ID);
        queue.enqueue(ID);
        queue.enqueue(ID);

        assertEquals(ID, queue.take());
        queue.done(ID);
        // Une seule entree malgre trois enqueue() successifs : re-enqueuer
        // apres traitement doit redonner la meme cle, sans doublon en attente.
        queue.enqueue(ID);
        assertEquals(ID, queue.take());
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void takeBlocksUntilAKeyIsEnqueued() throws InterruptedException {
        WorkQueue queue = new WorkQueue();
        Thread producer = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            queue.enqueue(ID);
        });
        producer.start();

        assertEquals(ID, queue.take());
        producer.join();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void aKeyBeingProcessedIsNeverHandedToASecondWorker() throws Exception {
        WorkQueue queue = new WorkQueue();
        queue.enqueue(ID);
        assertEquals(ID, queue.take()); // en traitement

        queue.enqueue(ID); // changement survenu pendant la reconciliation

        CompletableFuture<ServiceId> secondWorker = CompletableFuture.supplyAsync(() -> {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThrows(TimeoutException.class, () -> secondWorker.get(300, TimeUnit.MILLISECONDS),
                "la cle est en traitement : elle ne doit pas etre servie en parallele");

        queue.done(ID); // le changement n'est pas perdu : la cle redevient disponible, une seule fois

        assertEquals(ID, secondWorker.get(2, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void otherKeysFlowWhileOneIsBeingProcessed() throws InterruptedException {
        WorkQueue queue = new WorkQueue();
        ServiceId other = ServiceId.of("storage-service");
        queue.enqueue(ID);
        assertEquals(ID, queue.take());

        queue.enqueue(ID);
        queue.enqueue(other);

        assertEquals(other, queue.take());
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void aKeyThatWasNotReenqueuedWhileProcessedDoesNotComeBackOnDone() throws InterruptedException {
        WorkQueue queue = new WorkQueue();
        queue.enqueue(ID);
        queue.take();
        queue.done(ID);

        queue.enqueue(ServiceId.of("storage-service"));

        assertEquals(ServiceId.of("storage-service"), queue.take());
    }

    @Test
    void repeatedRequeuesOfTheSameKeyKeepASingleTimer() {
        WorkQueue queue = new WorkQueue();

        queue.requeueAfter(ID, Duration.ofSeconds(30));
        queue.requeueAfter(ID, Duration.ofSeconds(30));
        queue.requeueAfter(ID, Duration.ofSeconds(40));

        assertEquals(1, queue.pendingDelays(), "une chaine de re-planifications par cle, jamais plus");
        queue.shutdown();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void theClosestDeadlineWinsWhetherItComesFirstOrLast() throws InterruptedException {
        WorkQueue queue = new WorkQueue();
        queue.requeueAfter(ID, Duration.ofSeconds(30));
        queue.requeueAfter(ID, Duration.ofMillis(50)); // plus proche : remplace

        assertEquals(ID, queue.take());
        queue.done(ID);
        assertEquals(0, queue.pendingDelays());

        queue.requeueAfter(ID, Duration.ofMillis(50));
        queue.requeueAfter(ID, Duration.ofSeconds(30)); // plus lointaine : ignoree
        assertEquals(1, queue.pendingDelays());
        assertEquals(ID, queue.take());
        queue.shutdown();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void aNonPositiveDelayEnqueuesImmediately() throws InterruptedException {
        WorkQueue queue = new WorkQueue();

        queue.requeueAfter(ID, Duration.ZERO);

        assertEquals(ID, queue.take());
        assertEquals(0, queue.pendingDelays());
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void shutdownDropsPendingTimersAndReleasesWaitingWorkers() throws Exception {
        WorkQueue queue = new WorkQueue();
        queue.requeueAfter(ID, Duration.ofSeconds(30));
        CompletableFuture<Void> waiting = CompletableFuture.runAsync(() -> {
            try {
                queue.take();
            } catch (InterruptedException expected) {
                // arret du Kernel : le worker est libere
            }
        });
        Thread.sleep(50);

        queue.shutdown();

        waiting.get(1, TimeUnit.SECONDS);
        assertEquals(0, queue.pendingDelays());
    }
}
