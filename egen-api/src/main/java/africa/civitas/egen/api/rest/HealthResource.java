package africa.civitas.egen.api.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Sante du Kernel lui-meme (voir docs/architecture/13-api-et-contrats.md,
 * "/api/v1/health"). Une installation EGEN vide (zero service metier) doit
 * demarrer et repondre ici — garde-fou n4,
 * docs/architecture/02-principes-fondamentaux.md.
 */
@Path("/api/v1/health")
public class HealthResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
