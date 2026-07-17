package africa.civitas.egen.kernel.sdk.contexte;

/**
 * Nature concrete d'un {@link Contexte}.
 *
 * <p>Aujourd'hui, seuls deux sous-types existent : l'Organisation (racine souveraine)
 * et la Cellule (noeud recursif de sa hierarchie interne, dont l'Etablissement est une
 * simple convention de nommage — une Cellule racine). Cette enumeration est
 * volontairement fermee au niveau du Kernel : un troisieme type de Contexte (par
 * exemple un Projet transverse traversant plusieurs Cellules) est une extension
 * legitime mais qui doit etre tranchee explicitement au niveau du Systeme
 * Organisationnel (A2), jamais ajoutee au coup par coup par un module metier.
 */
public enum ContexteNature {
    ORGANISATION,
    CELLULE
}
