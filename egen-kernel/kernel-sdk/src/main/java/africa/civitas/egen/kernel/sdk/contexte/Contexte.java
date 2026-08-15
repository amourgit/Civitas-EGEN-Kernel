package africa.civitas.egen.kernel.sdk.contexte;

import java.util.UUID;

/**
 * Contrat public d'un Contexte — la reference neutre et opaque qu'un systeme du
 * Kernel utilise des qu'il a besoin de designer "un perimetre applicatif donne" sans
 * jamais dependre du module Niveau 2 qui en porte la definition reelle.
 *
 * <p>Tout systeme du Kernel qui a une portee variable (Politique, Souscription,
 * Activation, Ressource, Affectation...) reference un {@code contexteId} unique
 * plutot qu'un type metier concret. Ce module SDK n'expose que ce contrat minimal :
 * l'identifiant, rien de plus. Ce que ce Contexte represente concretement (une entite
 * racine, un sous-ensemble d'un perimetre plus large, ou toute autre notion qu'un
 * module Niveau 2 choisit de porter) n'est jamais visible depuis kernel-sdk, ni
 * depuis aucun systeme Niveau 0/1 : cette richesse-la appartient exclusivement au
 * module Niveau 2 qui la definit.
 *
 * <p>Un module metier qui a besoin de savoir "a quel Contexte suis-je rattache" ne
 * manipule que cette interface — jamais un type concret importe d'un autre module.
 * Le Kernel ne suppose jamais combien de "natures" de Contexte existent, ni comment
 * elles s'articulent entre elles (hierarchie, ensemble plat, graphe...) : cette
 * decision appartient entierement au module Niveau 2 qui modelise son propre domaine.
 */
public interface Contexte {

    /** Identifiant unique du Contexte, stable dans le temps. */
    UUID id();
}
