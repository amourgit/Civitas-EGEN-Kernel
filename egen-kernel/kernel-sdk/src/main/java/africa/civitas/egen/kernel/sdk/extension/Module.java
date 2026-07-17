package africa.civitas.egen.kernel.sdk.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque la classe descripteur d'un module metier — le point d'entree que le moteur de
 * plugins (PF4J, cote kernel-plugin-engine) instancie au demarrage pour piloter le
 * cycle de vie du module (chargement, demarrage, arret).
 *
 * <p>L'identifiant declare ici doit correspondre exactement au {@code moduleId} du
 * {@link africa.civitas.egen.kernel.sdk.manifest.ManifesteExtension} publie par le
 * module — c'est cette correspondance que le Kernel verifie avant d'activer quoi que
 * ce soit. Une seule classe par module doit porter cette annotation.
 *
 * <p>Note de nommage : cette annotation est distincte de {@code java.lang.Module} (le
 * module JPMS). Les deux concepts ne se recouvrent pas — un module EGEN peut tres bien
 * etre livre sans etre lui-meme un module JPMS nomme, tant qu'il respecte ce contrat.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Module {

    /**
     * Identifiant unique du module, en kebab-case (ex. {@code "academie"},
     * {@code "reconnaissance-faciale"}). Doit correspondre au {@code moduleId} du
     * Manifeste d'Extension.
     */
    String id();

    /** Nom lisible du module, destine a l'affichage administrateur. */
    String name() default "";

    /** Version du module, au format semantique (ex. {@code "1.4.0"}). */
    String version();
}
