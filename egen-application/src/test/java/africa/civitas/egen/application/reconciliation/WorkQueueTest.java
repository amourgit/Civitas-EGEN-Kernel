package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        // Une seule entree malgre trois enqueue() successifs : re-enqueuer
        // immediatement doit redonner la meme cle, sans doublon en attente.
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
}
