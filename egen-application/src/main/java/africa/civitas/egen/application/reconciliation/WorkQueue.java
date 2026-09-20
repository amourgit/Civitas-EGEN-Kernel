package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.domain.model.ServiceId;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File de cles a reconcilier — jamais d'evenements complets (voir
 * docs/architecture/04-moteur-de-reconciliation.md, "Composants de la
 * boucle"). Dedupliquee : enqueuer la meme cle plusieurs fois avant qu'elle
 * ne soit traitee ne produit qu'une seule entree.
 *
 * <p>v0 — file simple, un seul worker consommateur (voir
 * docs/architecture/19-feuille-de-route.md, Phase 1). La concurrence bornee
 * multi-worker par type de ressource est un raffinement de phase
 * ulterieure, jamais anticipe ici.</p>
 */
public final class WorkQueue {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    // LinkedList prserve l'ordre FIFO d'arrivee ; le Set garantit la
    // deduplication (une cle deja en file n'est jamais rajoutee).
    private final LinkedList<ServiceId> order = new LinkedList<>();
    private final Set<ServiceId> queued = new HashSet<>();
    private volatile boolean shuttingDown = false;

    /** Ajoute {@code id} a la file s'il n'y est pas deja. Jamais bloquant. */
    public void enqueue(ServiceId id) {
        lock.lock();
        try {
            if (queued.add(id)) {
                order.addLast(id);
                notEmpty.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    public void enqueueAll(Iterable<ServiceId> ids) {
        lock.lock();
        try {
            for (ServiceId id : ids) {
                if (queued.add(id)) {
                    order.addLast(id);
                }
            }
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Bloque jusqu'a ce qu'une cle soit disponible, puis la retire de la
     * file (elle peut etre re-enqueuee des cet instant pour un futur
     * passage).
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
            return id;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Re-enqueue {@code id} apres {@code delay} — utilise pour le retry avec
     * backoff et pour continuer a observer un service en cours de
     * convergence (voir docs/architecture/04-moteur-de-reconciliation.md).
     * Le delai est realise par un thread demon dedie, la file elle-meme
     * reste non bloquante.
     */
    public void requeueAfter(ServiceId id, Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            enqueue(id);
            return;
        }
        Thread delayed = new Thread(() -> {
            try {
                Thread.sleep(delay.toMillis());
                enqueue(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "egen-workqueue-delay-" + id.value());
        delayed.setDaemon(true);
        delayed.start();
    }

    public void shutdown() {
        lock.lock();
        try {
            shuttingDown = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
