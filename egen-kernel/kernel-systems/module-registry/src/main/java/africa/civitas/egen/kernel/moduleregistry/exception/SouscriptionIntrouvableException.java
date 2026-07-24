package africa.civitas.egen.kernel.moduleregistry.exception;

/** Leve quand la Souscription ciblee par une resiliation est introuvable ou deja resiliee. */
public class SouscriptionIntrouvableException extends RuntimeException {

    public SouscriptionIntrouvableException(String message) {
        super(message);
    }
}
