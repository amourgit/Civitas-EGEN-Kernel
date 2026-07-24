package africa.civitas.egen.kernel.authorization.exception;

/** Levee quand un octroi actif existe deja pour le meme sujet et la meme capacite. */
public class OctroiDejaActifException extends RuntimeException {

    public OctroiDejaActifException(String message) {
        super(message);
    }
}
