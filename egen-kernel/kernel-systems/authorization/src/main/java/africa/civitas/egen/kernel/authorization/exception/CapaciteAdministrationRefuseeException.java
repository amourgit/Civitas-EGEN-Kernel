package africa.civitas.egen.kernel.authorization.exception;

/**
 * Levee quand le sujet demandeur d'un {@code AccorderCapaciteCommand} ou d'un {@code
 * RevoquerCapaciteCommand} n'est ni le sujet bootstrap, ni detenteur d'un octroi
 * actif de {@code ADMINISTRER_CAPACITES_NOYAU}. C'est la traduction concrete du
 * caractere fail-closed de ce systeme : administrer les capacites d'autrui n'est
 * jamais une action par defaut.
 */
public class CapaciteAdministrationRefuseeException extends RuntimeException {

    public CapaciteAdministrationRefuseeException(String message) {
        super(message);
    }
}
