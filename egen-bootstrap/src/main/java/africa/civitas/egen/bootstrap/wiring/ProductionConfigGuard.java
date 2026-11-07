package africa.civitas.egen.bootstrap.wiring;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Fait échouer le DÉMARRAGE du Kernel en production si sa configuration porte un identifiant par défaut (constat K6 de
 * docs/architecture/24-revue-de-securite-externe.md). Sans cette garde, un oubli de variable d'environnement laissait
 * le mot de passe de développement atteindre la base de production, sans aucune erreur.
 *
 * <p>Actif uniquement en mode normal (production) : les modes de développement et de test gardent leurs valeurs
 * par défaut. La priorité fait passer cette garde avant les autres observateurs de démarrage, pour que l'erreur
 * affichée soit celle-ci et non un échec de connexion à la base.</p>
 */
@ApplicationScoped
public class ProductionConfigGuard {

    void onStart(@Observes @Priority(100) StartupEvent event,
                 @ConfigProperty(name = "quarkus.datasource.password", defaultValue = "") String databasePassword) {
        if (LaunchMode.current() == LaunchMode.NORMAL) {
            ProductionConfigurationRules.assertDatabasePasswordIsExplicit(databasePassword);
        }
    }
}
