package africa.civitas.egen.domain.model;

/**
 * Comment un service doit etre execute (voir
 * docs/architecture/06-service-manifest.md, section "deployment"). Traduit par
 * le DeploymentPort concerne vers le moteur specialise reel (Nomad en V1) — le
 * domaine ne connait jamais ce mapping, voir
 * docs/architecture/07-ports-et-adapters.md.
 *
 * <p>{@code cpu} et {@code memory} restent des chaines brutes telles que
 * declarees dans le manifeste (ex. "500m", "512Mi") : leur interpretation en
 * unites du moteur cible est une responsabilite d'adapter, jamais du domaine.</p>
 *
 * <p>{@code security} est facultatif ({@code null} = le socle de l'adapter, rien de plus strict ;
 * docs/architecture/06, « deployment.security »).</p>
 */
public record DeploymentSpec(String adapter, String image, String cpu, String memory,
                              ReplicaRange replicas, SecuritySpec security) {

    /** Sans durcissement demande : le socle de l'adapter s'applique, rien de plus strict. */
    public DeploymentSpec(String adapter, String image, String cpu, String memory, ReplicaRange replicas) {
        this(adapter, image, cpu, memory, replicas, null);
    }

    public DeploymentSpec {
        if (security != null && security.isEmpty()) {
            security = null; // un bloc vide equivaut a son absence : deux manifestes equivalents sont egaux
        }
        if (adapter == null || adapter.isBlank()) {
            throw new IllegalArgumentException(
                    "DeploymentSpec.adapter est obligatoire (ex. \"nomad\")");
        }
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("DeploymentSpec.image ne peut pas etre vide");
        }
        if (cpu == null || cpu.isBlank()) {
            throw new IllegalArgumentException("DeploymentSpec.cpu ne peut pas etre vide");
        }
        if (memory == null || memory.isBlank()) {
            throw new IllegalArgumentException("DeploymentSpec.memory ne peut pas etre vide");
        }
        if (replicas == null) {
            throw new IllegalArgumentException("DeploymentSpec.replicas est obligatoire");
        }
    }
}
