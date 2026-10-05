package africa.civitas.egen.application.port;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Port secondaire — voir docs/architecture/07-ports-et-adapters.md,
 * "Service Invocation Port" pour le raisonnement complet (pourquoi ce port
 * existe, pourquoi il ne fait jamais de decouverte lui-meme, pourquoi
 * {@code payload}/{@code body} restent neutres).
 *
 * <p>Unique appelant prevu : le Moteur de Workflow
 * (egen-application.workflow, voir
 * docs/architecture/11-moteur-de-workflow.md) pour les etapes
 * {@link africa.civitas.egen.domain.workflow.StepOperation.Http} — jamais
 * pour une operation {@link africa.civitas.egen.domain.workflow.StepOperation.Event},
 * qui passe par {@link MessagingPort#publish}.</p>
 */
public interface ServiceInvocationPort {

    /**
     * @param endpoint URI DEJA resolue (voir {@link DiscoveryPort#resolve}) —
     *                 ce port ne resout jamais lui-meme un {@code ServiceId}.
     * @param method   une methode HTTP parmi GET/POST/PUT/PATCH/DELETE (voir
     *                 {@link africa.civitas.egen.domain.workflow.StepOperation.Http#method()})
     * @param payload  corps de la requete, neutre — vide pour un GET/DELETE
     *                 sans corps
     * @param timeout  propre a l'etape appelante ({@code Step.timeout()}) —
     *                 jamais un defaut implicite cote adapter
     * @throws java.io.UncheckedIOException en cas d'erreur reseau (DNS,
     *         connexion refusee, timeout ecoule) — jamais pour un statut
     *         HTTP 4xx/5xx, qui est une reponse valide (voir
     *         {@link InvocationResult})
     */
    InvocationResult invoke(URI endpoint, String method, Map<String, String> payload, Duration timeout);
}
