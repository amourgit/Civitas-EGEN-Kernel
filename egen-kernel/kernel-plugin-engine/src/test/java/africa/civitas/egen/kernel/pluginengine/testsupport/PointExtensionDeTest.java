package africa.civitas.egen.kernel.pluginengine.testsupport;

import africa.civitas.egen.kernel.sdk.extension.ExtensionPoint;

/** Point d'extension de test, sans equivalent metier reel — sert uniquement les tests du registre. */
public interface PointExtensionDeTest extends ExtensionPoint {

    String nom();
}
