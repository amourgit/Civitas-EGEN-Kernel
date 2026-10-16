package africa.civitas.egen.domain.model;

/**
 * Interface qu'un service annonce a la couche agentique (voir
 * docs/architecture/21-systeme-agents-et-gateway.md, 21.3). Bloc OPTIONNEL et
 * additif du manifeste.
 *
 * <p><b>Opaque pour le Kernel, comme {@code deployment.image}</b> : il stocke et
 * restitue ces trois chaines, il n'ouvre jamais {@code ref}, n'interprete
 * jamais {@code type} ni {@code riskDefault}, et ne valide aucune valeur
 * contre une liste fermee — c'est ce qui rend le bloc rapidement
 * extensible ({@code grpc}, un nouveau niveau de risque...) sans migrer le
 * Kernel, et ignorable par tout consommateur qui ne le comprend pas. Seule la
 * forme est verifiee (cles presentes et non vides).</p>
 *
 * @param type nature du descripteur ({@code mcp}, {@code openapi}, ...)
 * @param ref reference du descripteur (opaque)
 * @param riskDefault niveau de risque par defaut ({@code read}, {@code write}, {@code sensitive}) ; optionnel
 */
public record ServiceInterface(String type, String ref, String riskDefault) {

    public ServiceInterface {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("interfaces[].type ne peut pas etre vide");
        }
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("interfaces[].ref ne peut pas etre vide");
        }
        riskDefault = riskDefault == null || riskDefault.isBlank() ? null : riskDefault;
    }
}
