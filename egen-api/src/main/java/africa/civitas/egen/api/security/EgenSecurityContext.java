package africa.civitas.egen.api.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Point d'entree unique du RBAC de l'API de controle EGEN (voir
 * docs/architecture/14-securite.md, "RBAC de l'API de controle EGEN" et
 * docs/architecture/13-api-et-contrats.md, "Qui peut appeler quoi").
 *
 * <p>Le Kernel ne connait ni utilisateurs, ni roles, ni permissions
 * metier (garde-fou n1, docs/architecture/02-principes-fondamentaux.md) —
 * cette classe ne fait PAS exception : elle applique une regle unique,
 * deliberement pauvre (decision de Samuel, 10 novembre 2026) :</p>
 * <ul>
 *   <li><b>lire</b> : tout appelant authentifie (aucun controle ici) ;</li>
 *   <li><b>modifier</b> : le role administrateur, {@link #assertAdmin()}.</li>
 * </ul>
 *
 * <p>{@code metadata.team} est PUREMENT INFORMATIF (tracabilite) : il n'est jamais lu pour decider, et aucun
 * claim d'equipe du jeton n'est lu non plus. Il n'existe donc ni autorisation par equipe, ni filtre par equipe,
 * ni notion de propriete. Les droits fins relevent d'un systeme exterieur au Kernel (docs/architecture/14 et
 * 21, §21.5). Le role admin est configurable ({@code egen.security.admin-role}) pour rester compatible avec
 * n'importe quel IdP OIDC d'entreprise sans modification de code.</p>
 */
@RequestScoped
public class EgenSecurityContext {

    private final SecurityIdentity identity;
    private final String adminRole;

    @Inject
    public EgenSecurityContext(SecurityIdentity identity,
                                @ConfigProperty(name = "egen.security.admin-role", defaultValue = "egen-admin")
                                String adminRole) {
        this.identity = identity;
        this.adminRole = adminRole;
    }

    public boolean isAdmin() {
        return !identity.isAnonymous() && identity.hasRole(adminRole);
    }

    /**
     * Reserve une modification aux administrateurs EGEN : declarer ou redeclarer un service, l'arreter, publier sa
     * configuration, publier un evenement, creer ou conduire un workflow. A appeler EN PREMIER, avant toute lecture
     * du corps de la requete et avant de chercher la ressource : un non-administrateur recoit toujours 403, jamais
     * 404 (un 404 apprendrait a un appelant non autorise quels services existent).
     */
    public void assertAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException("Cette operation est reservee aux administrateurs EGEN");
        }
    }
}
