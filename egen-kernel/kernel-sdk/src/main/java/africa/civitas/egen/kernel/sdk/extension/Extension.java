package africa.civitas.egen.kernel.sdk.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une classe comme l'implementation concrete d'un {@link ExtensionPoint} donne.
 *
 * <p>Le moteur de plugins decouvre ces classes par reflexion au chargement d'un module
 * et les enregistre aupres du point d'extension qu'elles declarent servir. Une classe
 * annotee doit implementer l'interface reference par {@link #point()} — cette coherence
 * est verifiee au demarrage, pas seulement esperee par convention.
 *
 * <p>{@link #priority()} permet de departager plusieurs implementations enregistrees
 * pour le meme point d'extension : la valeur la plus basse est evaluee en premier.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Extension {

    /** Le point d'extension implemente par cette classe. */
    Class<? extends ExtensionPoint> point();

    /** Ordre d'evaluation lorsque plusieurs extensions servent le meme point (croissant). */
    int priority() default 100;
}
