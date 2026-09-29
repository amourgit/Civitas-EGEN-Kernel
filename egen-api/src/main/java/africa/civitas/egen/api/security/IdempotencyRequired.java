package africa.civitas.egen.api.security;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Liaison JAX-RS (Name Binding — voir la specification Jakarta RESTful Web
 * Services, section "Filters and Interceptors") entre {@link IdempotencyKeyFilter}
 * et les methodes de ressource qui exigent l'en-tete {@code Idempotency-Key}
 * (voir docs/architecture/13-api-et-contrats.md, "obligatoire sur toutes
 * les mutations").
 *
 * <p>Choix delibere plutot qu'un filtre global testant le chemin/la methode
 * HTTP a la main : {@link jakarta.ws.rs.core.UriInfo#getPath()} n'a PAS un
 * comportement stable a travers les versions de la specification (JAX-RS
 * 2.1 le retourne SANS slash initial, Jakarta REST 3.0+ — celui que Quarkus
 * REST implemente — le retourne AVEC un slash initial), et un filtre qui en
 * depend silencieusement se desactive sans jamais lever d'erreur si cette
 * hypothese est fausse (exactement le bug corrige ici — voir l'historique
 * git de {@link IdempotencyKeyFilter}). Le Name Binding elimine ce risque en
 * laissant le conteneur JAX-RS lui-meme decider quelles methodes sont
 * concernees, de facon declarative et verifiee a la compilation.</p>
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface IdempotencyRequired {
}
