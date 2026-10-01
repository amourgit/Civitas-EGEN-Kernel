package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relie le contexte de trace d'un appel HTTP entrant sur l'API de controle
 * (Declare ou Stop, voir egen-api.rest.ServiceResource) au premier passage
 * de reconciliation qui en resulte, jusqu'a ce que ce passage atteigne son
 * premier appel reseau reel — voir
 * docs/architecture/15-observabilite.md, "chaque appel entrant sur l'API de
 * controle propage son traceparent jusqu'au premier appel reseau vers
 * Nomad/Consul/Kafka".
 *
 * <p>Collaborateur partage entre le use case qui declenche un cycle (a
 * l'ecriture, via {@link #record}) et {@link ReconciliationEngine} qui le
 * traite plus tard, potentiellement sur un tout autre thread (a la lecture,
 * via {@link #peek}) — exactement le meme role que {@link WorkQueue} joue
 * deja entre ces deux memes collaborateurs, jamais une dependance directe
 * de l'un vers l'autre (voir docs/architecture/04-moteur-de-reconciliation.md).</p>
 *
 * <p><b>Jamais persiste, deliberement</b> — bookkeeping en memoire au meme
 * titre que {@code ReconciliationEngine.stoppingDeregisteredAt} : un
 * redemarrage du Kernel entre un appel API et la convergence qu'il a
 * declenchee perd simplement ce lien de trace, sans consequence sur la
 * correction de la reconciliation elle-meme (qui reste entierement
 * re-derivable depuis le Registry, comme toujours). Ajouter une colonne
 * persistee pour ce seul confort de debogage serait de la complexite non
 * justifiee par un besoin reel (garde-fou n3,
 * docs/architecture/02-principes-fondamentaux.md).</p>
 *
 * <p><b>Cle par (ServiceId, generation)</b>, jamais par ServiceId seul : un
 * nouveau Declare sur le meme service (nouvelle generation) invalide
 * naturellement, par simple non-correspondance au moment du {@link #peek},
 * tout lien de trace encore en attente pour une generation anterieure —
 * aucune purge explicite requise, aucune fuite entre deux declarations
 * successives.</p>
 */
public final class PendingDeclareTraces {

    private record Entry(long generation, TraceContext context) {
    }

    private final ConcurrentHashMap<ServiceId, Entry> pending = new ConcurrentHashMap<>();

    /**
     * Enregistre le contexte de trace declenchant pour cette generation
     * precise. {@code triggeringTrace} peut etre {@code null} (appelant qui
     * ne propage pas de trace, ou test) : dans ce cas, aucune entree n'est
     * gardee — {@link #peek} restera vide, et le cycle de reconciliation
     * ouvrira simplement son propre span racine independant, comme avant
     * l'introduction de cette classe.
     */
    public void record(ServiceId id, long generation, TraceContext triggeringTrace) {
        if (triggeringTrace != null) {
            pending.put(id, new Entry(generation, triggeringTrace));
        }
    }

    /**
     * Le contexte de trace en attente pour cette generation EXACTE, s'il en
     * existe un — jamais un contexte perime d'une generation anterieure ou
     * posterieure.
     */
    public Optional<TraceContext> peek(ServiceId id, long generation) {
        Entry entry = pending.get(id);
        return (entry != null && entry.generation() == generation)
                ? Optional.of(entry.context())
                : Optional.empty();
    }

    /**
     * Le premier appel reseau reel a eu lieu pour cette generation : le
     * lien de trace a rempli son role, les cycles suivants (convergence,
     * resync periodique) redeviennent des racines independantes — voir
     * docs/architecture/15-observabilite.md, qui ne demande le lien que
     * "jusqu'au premier appel reseau", jamais au-dela.
     */
    public void clear(ServiceId id) {
        pending.remove(id);
    }
}
