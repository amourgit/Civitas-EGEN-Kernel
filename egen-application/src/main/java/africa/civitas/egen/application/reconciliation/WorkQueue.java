package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.domain.model.ServiceId;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File de cles a reconcilier — jamais d'evenements complets (voir
 * docs/architecture/04-moteur-de-reconciliation.md, « Composants de la boucle »).
 *
 * <p>Trois garanties, celles d'une work queue de controleur Kubernetes :</p>
 * <ul>
 *   <li><b>Dedupliquee</b> : enqueuer plusieurs fois la meme cle avant qu'elle soit prise ne
 *       produit qu'une seule entree — une reconciliation relit tout l'etat, la derniere lecture
 *       voit chaque changement.</li>
 *   <li><b>Une cle n'est jamais traitee par deux workers a la fois</b> : une cle prise reste « en
 *       traitement » jusqu'a {@link #done(ServiceId)}. Si elle est re-enqueuee entre-temps, elle est
 *       marquee a rejouer et ne redevient disponible qu'a {@code done}. C'est ce qui permet une
 *       concurrence bornee (plusieurs workers) sans jamais reconcilier un meme service en parallele.</li>
 *   <li><b>Une seule re-planification differee par cle</b> : {@link #requeueAfter} garde l'echeance la
 *       plus proche. Sans cela, chaque cycle declenche par ailleurs (declaration, resync) ajouterait
 *       une nouvelle chaine de re-planifications qui ne s'eteindrait jamais.</li>
 * </ul>
 *
 * <p>Les delais sont realises par un unique thread demon ; la file elle-meme ne bloque jamais, sauf
 * {@link #take()}.</p>
 */
public final class WorkQueue {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    // FIFO d'arrivee ; les ensembles garantissent la deduplication.
    private final ArrayDeque<ServiceId> order = new ArrayDeque<>();
    private final Set<ServiceId> queued = new HashSet<>();      // en file, pas encore prise
    private final Set<ServiceId> processing = new HashSet<>();  // prise, pas encore done()
    private final Set<ServiceId> dirty = new HashSet<>();       // re-enqueuee pendant son traitement
    private final Map<ServiceId, PendingDelay> delayed = new HashMap<>();
    private final ScheduledExecutorService timer;
    private boolean shuttingDown = false;

    private record PendingDelay(long dueNanos, ScheduledFuture<?> future) {
    }

    public WorkQueue() {
        // Un thread demon, cree a la premiere echeance : un WorkQueue qui n'est jamais retardee ne coute rien.
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "egen-workqueue-timer");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Ajoute {@code id} a la file s'il n'y est pas deja. Jamais bloquant. */
    public void enqueue(ServiceId id) {
        lock.lock();
        try {
            enqueueLocked(id);
        } finally {
            lock.unlock();
        }
    }

    public void enqueueAll(Iterable<ServiceId> ids) {
        lock.lock();
        try {
            for (ServiceId id : ids) {
                enqueueLocked(id);
            }
        } finally {
            lock.unlock();
        }
    }

    private void enqueueLocked(ServiceId id) {
        if (processing.contains(id)) {
            dirty.add(id); // sera rejouee a done(), jamais en parallele
            return;
        }
        if (queued.add(id)) {
            order.addLast(id);
            notEmpty.signal();
        }
    }

    /**
     * Bloque jusqu'a ce qu'une cle soit disponible, puis la retire de la file et la marque « en
     * traitement ». <b>L'appelant doit appeler {@link #done(ServiceId)}</b> quand il a fini, que le
     * traitement ait reussi ou non.
     *
     * @throws InterruptedException si le worker est interrompu (arret du Kernel)
     */
    public ServiceId take() throws InterruptedException {
        lock.lock();
        try {
            while (order.isEmpty() && !shuttingDown) {
                notEmpty.await();
            }
            if (order.isEmpty()) {
                throw new InterruptedException("WorkQueue en cours d'arret");
            }
            ServiceId id = order.removeFirst();
            queued.remove(id);
            processing.add(id);
            return id;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Termine le traitement de {@code id}. Si la cle a ete re-enqueuee pendant ce temps, elle redevient
     * disponible immediatement : un changement survenu pendant une reconciliation n'est jamais perdu.
     */
    public void done(ServiceId id) {
        lock.lock();
        try {
            processing.remove(id);
            if (dirty.remove(id) && queued.add(id)) {
                order.addLast(id);
                notEmpty.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Re-enqueue {@code id} apres {@code delay} — retry avec backoff, et poursuite de l'observation d'un
     * service (voir 04). Une seule echeance en attente par cle : si une plus proche existe deja, elle est
     * conservee ; sinon la nouvelle la remplace.
     */
    public void requeueAfter(ServiceId id, Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            enqueue(id);
            return;
        }
        long delayNanos = delay.toNanos();
        lock.lock();
        try {
            if (shuttingDown) {
                return;
            }
            long due = System.nanoTime() + delayNanos;
            PendingDelay existing = delayed.get(id);
            if (existing != null && existing.dueNanos() - due <= 0) {
                return; // une echeance egale ou plus proche existe deja
            }
            if (existing != null) {
                existing.future().cancel(false);
            }
            PendingDelay[] self = new PendingDelay[1];
            ScheduledFuture<?> future = timer.schedule(() -> fire(id, self[0]), delayNanos, TimeUnit.NANOSECONDS);
            self[0] = new PendingDelay(due, future);
            delayed.put(id, self[0]);
        } finally {
            lock.unlock();
        }
    }

    private void fire(ServiceId id, PendingDelay expected) {
        lock.lock();
        try {
            if (delayed.get(id) != expected) {
                return; // remplacee ou annulee entre-temps
            }
            delayed.remove(id);
            enqueueLocked(id);
        } finally {
            lock.unlock();
        }
    }

    /** Nombre d'echeances differees en attente — pour les tests et le diagnostic. */
    int pendingDelays() {
        lock.lock();
        try {
            return delayed.size();
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            shuttingDown = true;
            delayed.clear();
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
        timer.shutdownNow();
    }
}
