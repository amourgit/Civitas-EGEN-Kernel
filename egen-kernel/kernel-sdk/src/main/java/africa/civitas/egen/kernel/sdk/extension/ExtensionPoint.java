package africa.civitas.egen.kernel.sdk.extension;

/**
 * Marqueur d'un point d'extension du Kernel.
 *
 * <p>Un point d'extension est une interface metier que le Kernel expose et qu'un module
 * (Academie, RH, Finance...) peut choisir d'implementer pour s'accrocher a une capacite
 * precise. Le Kernel ne connait jamais les implementations concretes a la compilation —
 * il les decouvre au demarrage via le moteur de plugins, qui recherche les classes
 * annotees {@link Extension} dont {@link Extension#point()} reference ce point.
 *
 * <p>Cette interface ne declare volontairement aucune methode : elle sert uniquement de
 * marqueur de type, exactement comme {@code java.io.Serializable}. Chaque point
 * d'extension reel est une sous-interface qui, elle, declare les methodes attendues.
 */
public interface ExtensionPoint {
}
