package africa.civitas.egen.api.security;

import africa.civitas.egen.domain.model.OwnerTeam;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Point d'entree unique du RBAC de l'API de controle EGEN (voir
 * docs/architecture/14-securite.md, "RBAC de l'API de controle EGEN" et
 * docs/architecture/13-api-et-contrats.md, "Securite de l'API").
 *
 * <p>Le Kernel ne connait ni utilisateurs, ni roles, ni permissions
 * metier (garde-fou n1, docs/architecture/02-principes-fondamentaux.md) —
 * cette classe ne fait PAS exception : elle ne definit aucune notion
 * metier, elle applique une regle unique, deliberement pauvre : "qui a
 * declare un service peut le modifier, ou un administrateur du Kernel
 * peut modifier n'importe quel service". Le sens des equipes, leur
 * hierarchie, leurs habilitations metier fines restent entierement du
 * ressort de l'IAM/Policy Engine externe decrit en
 * docs/architecture/21-systeme-agents-et-gateway.md, §21.5 — le Kernel
 * ne fait ici QUE verifier une egalite de chaine de caracteres entre le
 * claim du jeton et le champ {@code metadata.team} du manifeste.</p>
 *
 * <p>Le claim et le role admin sont configurables ({@code
 * egen.security.team-claim}, {@code egen.security.admin-role}) plutot
 * que codes en dur, pour rester compatible avec n'importe quel IdP OIDC
 * d'entreprise sans modification de code.</p>
 */
@RequestScoped
public class EgenSecurityContext {

    private final SecurityIdentity identity;
    private final JsonWebToken jwt;
    private final String teamClaim;
    private final String adminRole;

    @Inject
    public EgenSecurityContext(SecurityIdentity identity, JsonWebToken jwt,
                                @ConfigProperty(name = "egen.security.team-claim", defaultValue = "team")
                                String teamClaim,
                                @ConfigProperty(name = "egen.security.admin-role", defaultValue = "egen-admin")
                                String adminRole) {
        this.identity = identity;
        this.jwt = jwt;
        this.teamClaim = teamClaim;
        this.adminRole = adminRole;
    }

    /** L'equipe portee par le jeton de l'appelant, ou {@code null} si le claim est absent. */
    public String callerTeam() {
        Object claim = jwt.getClaim(teamClaim);
        return claim == null ? null : claim.toString();
    }

    public boolean isAdmin() {
        return !identity.isAnonymous() && identity.hasRole(adminRole);
    }

    /**
     * Autorise un Declare portant sur l'equipe {@code targetTeam} (extraite
     * du manifeste soumis, avant meme sa construction complete — voir
     * {@code ServiceManifestMapper.teamOf}). Un manifeste qui ne declare
     * aucune equipe est refuse pour toute personne non-admin : accepter un
     * manifeste sans equipe reviendrait a permettre a n'importe quel
     * appelant authentifie de creer un service qu'aucune equipe ne pourra
     * ensuite reclamer en propriete.
     */
    public void assertCanDeclare(String targetTeam) {
        if (isAdmin()) {
            return;
        }
        if (targetTeam == null || targetTeam.isBlank()) {
            throw new ForbiddenException(
                    "metadata.team est obligatoire pour un appelant non-administrateur");
        }
        if (!targetTeam.equals(callerTeam())) {
            throw new ForbiddenException(
                    "Le jeton ne porte pas l'equipe proprietaire declaree (" + targetTeam + ")");
        }
    }

    /**
     * Autorise une action sur un service DEJA enregistre, a partir de
     * l'equipe portee par son manifeste stocke. Un service historique
     * sans equipe assignee ({@link OwnerTeam#isUnassigned()}) reste
     * modifiable par n'importe quel appelant authentifie — c'est un choix
     * de transition deliberement permissif (voir docs/architecture/14-securite.md,
     * note de migration) : EGEN ne bloque jamais retroactivement des
     * services declares avant l'activation du RBAC.
     */
    public void assertOwnsOrAdmin(OwnerTeam ownerTeam) {
        if (isAdmin() || ownerTeam == null || ownerTeam.isUnassigned()) {
            return;
        }
        if (!ownerTeam.value().equals(callerTeam())) {
            throw new ForbiddenException("Ce service appartient a une autre equipe");
        }
    }
}
