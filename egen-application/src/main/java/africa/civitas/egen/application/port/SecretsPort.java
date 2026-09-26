package africa.civitas.egen.application.port;

/**
 * Port secondaire — resolution de references de secrets (Vault en V1, voir
 * docs/architecture/07-ports-et-adapters.md, "Secrets Port"). EGEN ne
 * stocke, ne loggue et ne met JAMAIS en cache un secret au-dela de la
 * duree d'un appel (voir docs/architecture/14-securite.md) : {@link #resolve}
 * ne fait que confirmer l'existence de la reference et retourner de quoi la
 * monter, jamais la valeur elle-meme.
 */
public interface SecretsPort {

    SecretRef resolve(SecretReference reference);

    void rotate(SecretReference reference);
}
