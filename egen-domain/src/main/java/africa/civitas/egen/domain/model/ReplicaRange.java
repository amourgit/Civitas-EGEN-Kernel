package africa.civitas.egen.domain.model;

/**
 * Bornes de replication d'un service (voir
 * docs/architecture/06-service-manifest.md, "deployment.replicas").
 */
public record ReplicaRange(int min, int max) {

    public ReplicaRange {
        if (min < 0) {
            throw new IllegalArgumentException("ReplicaRange.min ne peut pas etre negatif");
        }
        if (max < min) {
            throw new IllegalArgumentException(
                    "ReplicaRange.max (" + max + ") ne peut pas etre inferieur a min (" + min + ")");
        }
    }

    public static ReplicaRange fixed(int count) {
        return new ReplicaRange(count, count);
    }
}
