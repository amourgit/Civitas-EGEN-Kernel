package africa.civitas.egen.api.security;

/**
 * Clé de cache d'une réponse rejouable par {@link IdempotencyKeyFilter}.
 *
 * <p><b>La clé inclut l'identité de l'appelant.</b> La valeur de l'en-tête {@code Idempotency-Key} est
 * choisie par le client : sans l'appelant dans la clé, deux appelants authentifiés différents qui envoient
 * la même valeur sur le même chemin partageraient une entrée. Le second recevrait la réponse du premier, et sa
 * propre requête ne serait jamais exécutée (voir docs/architecture/14-securite.md, « zero trust »,
 * et docs/architecture/13-api-et-contrats.md, « Idempotency-Key »).</p>
 *
 * <p>Chaque composant est préfixé par sa longueur : {@code ("a|b", "c")} et {@code ("a", "b|c")} ne
 * peuvent jamais produire la même clé, quel que soit le contenu des chaînes.</p>
 *
 * <p>Classe volontairement sans aucune dépendance JAX-RS : sa règle se teste sans conteneur.</p>
 */
final class IdempotencyCacheKey {

    /** Nom utilisé si la requête n'a aucun principal ; les chemins protégés exigent un appelant authentifié. */
    static final String ANONYMOUS = "<anonymous>";

    private IdempotencyCacheKey() {
    }

    static String of(String callerName, String method, String path, String idempotencyKey) {
        String caller = callerName == null || callerName.isBlank() ? ANONYMOUS : callerName;
        return part(caller) + part(method) + part(path) + part(idempotencyKey);
    }

    private static String part(String value) {
        return value.length() + ":" + value + ";";
    }
}
