package africa.civitas.egen.kernel.pluginengine.lifecycle;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.moduleregistry.service.ModuleActivationResolver;
import africa.civitas.egen.kernel.pluginengine.loader.CandidatModule;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoadException;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoader;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReadException;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionRegistry;
import africa.civitas.egen.kernel.sdk.manifest.ManifestValidationException;
import africa.civitas.egen.kernel.sdk.manifest.ManifesteExtension;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelPermissionCheck;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyau;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyauQuestion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'orchestrateur du moteur de plugins — le seul point d'entree pour charger ou
 * decharger un module. Jamais appele directement par un systeme A1-E3 (voir
 * l'anatomie du Kernel, §6) : uniquement depuis kernel-bootstrap, au demarrage, ou
 * depuis une future interface d'administration.
 *
 * <p>Le flux exact de {@link #charger}, dans cet ordre strict, jamais permute :
 * <ol>
 *   <li>{@link KernelPermissionCheck} — le sujet demandeur a-t-il le droit de
 *       declencher un chargement (capacite administrative, independante du module
 *       cible) ?</li>
 *   <li>{@link ManifestReader} — le Manifeste du candidat se lit-il et se
 *       construit-il valablement ? Un echec ici consulte {@link PolitiqueNoyau}
 *       ({@link PolitiqueNoyauQuestion#ECHEC_CONSTRUCTION_MANIFESTE}), toujours un
 *       refus.</li>
 *   <li>{@link ModuleActivationResolver} — ce module precis doit-il tourner dans
 *       cette Cellule precise (donnee de Souscription/Activation, independante de
 *       la capacite du sujet) ?</li>
 *   <li>Dependances — chaque module que le Manifeste declare requerir est-il deja
 *       charge ?</li>
 *   <li>Seulement alors, {@link PluginLoader#charger} — le chargement physique.</li>
 * </ol>
 *
 * <p>Cette classe ne prend jamais de dependance directe sur PF4J, Quarkus/CDI mis a
 * part pour son propre cycle de vie de bean : {@link PluginLoader} est une
 * interface, injectee comme les autres. Elle est deliberement instanciable a la main
 * (constructeur simple, sans conteneur) — voir les tests, qui n'ont besoin ni de
 * Quarkus ni de Docker pour couvrir l'intégralite de cette logique de decision.
 */
@ApplicationScoped
public class PluginLifecycleManager {

    private final KernelPermissionCheck kernelPermissionCheck;
    private final ModuleActivationResolver moduleActivationResolver;
    private final PolitiqueNoyau politiqueNoyau;
    private final ManifestReader manifestReader;
    private final ExtensionRegistry extensionRegistry;
    private final PluginLoader pluginLoader;

    private final Map<String, ManifesteExtension> manifestesCharges = new ConcurrentHashMap<>();

    @Inject
    public PluginLifecycleManager(
            KernelPermissionCheck kernelPermissionCheck,
            ModuleActivationResolver moduleActivationResolver,
            PolitiqueNoyau politiqueNoyau,
            ManifestReader manifestReader,
            ExtensionRegistry extensionRegistry,
            PluginLoader pluginLoader) {
        this.kernelPermissionCheck = requireNonNull(kernelPermissionCheck, "kernelPermissionCheck");
        this.moduleActivationResolver = requireNonNull(moduleActivationResolver, "moduleActivationResolver");
        this.politiqueNoyau = requireNonNull(politiqueNoyau, "politiqueNoyau");
        this.manifestReader = requireNonNull(manifestReader, "manifestReader");
        this.extensionRegistry = requireNonNull(extensionRegistry, "extensionRegistry");
        this.pluginLoader = requireNonNull(pluginLoader, "pluginLoader");
    }

    /**
     * Tente de charger {@code candidat} dans la Cellule {@code celluleCible}, pour le
     * compte de {@code sujetDemandeur}.
     *
     * @throws PluginLoadException uniquement pour un echec technique imprevu du
     *                               chargement physique lui-meme — jamais pour un
     *                               refus de gouvernance attendu, toujours restitue
     *                               comme {@link ResultatChargement.Echec}
     */
    public ResultatChargement charger(CandidatModule candidat, KernelSubject sujetDemandeur, UUID celluleCible) {
        if (candidat == null) {
            throw new IllegalArgumentException("candidat ne peut pas etre nul.");
        }
        if (sujetDemandeur == null) {
            throw new IllegalArgumentException("sujetDemandeur ne peut pas etre nul.");
        }
        if (celluleCible == null) {
            throw new IllegalArgumentException("celluleCible ne peut pas etre nul.");
        }

        DecisionNoyau droitDeCharger = kernelPermissionCheck.verifier(sujetDemandeur, KernelCapability.CHARGER_MODULE);
        if (!droitDeCharger.autorise()) {
            return new ResultatChargement.Echec(droitDeCharger.motif());
        }

        ManifesteExtension manifeste;
        try {
            manifeste = manifestReader.lire(candidat.manifestSource());
        } catch (ManifestReadException | ManifestValidationException e) {
            DecisionNoyau refusPolitique = politiqueNoyau.resoudre(PolitiqueNoyauQuestion.ECHEC_CONSTRUCTION_MANIFESTE);
            return new ResultatChargement.Echec(refusPolitique.motif() + " Cause : " + e.getMessage());
        }

        if (manifestesCharges.containsKey(manifeste.moduleId())) {
            return new ResultatChargement.Echec(
                    "Le module '" + manifeste.moduleId() + "' est deja charge.");
        }

        DecisionNoyau doitTourner = moduleActivationResolver.estActifPour(celluleCible, new ModuleId(manifeste.moduleId()));
        if (!doitTourner.autorise()) {
            return new ResultatChargement.Echec(doitTourner.motif());
        }

        List<String> dependancesManquantes = manifeste.dependencies().stream()
                .filter(dep -> !pluginLoader.estCharge(dep))
                .toList();
        if (!dependancesManquantes.isEmpty()) {
            return new ResultatChargement.Echec(
                    "Le module '" + manifeste.moduleId() + "' requiert les modules suivants, "
                            + "non charges : " + String.join(", ", dependancesManquantes) + ".");
        }

        List<ExtensionDecouverte> decouvertes = pluginLoader.charger(manifeste.moduleId(), candidat.cheminPlugin());
        extensionRegistry.enregistrerToutes(decouvertes);
        manifestesCharges.put(manifeste.moduleId(), manifeste);

        return new ResultatChargement.Succes(manifeste, decouvertes.size());
    }

    /**
     * Tente de decharger le module {@code moduleId}, pour le compte de {@code
     * sujetDemandeur}. Refuse tant qu'un autre module charge declare en dependre —
     * jamais de dechargement en cascade implicite : chaque dechargement reste un
     * acte explicite et delibere.
     */
    public ResultatDechargement decharger(String moduleId, KernelSubject sujetDemandeur) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        if (sujetDemandeur == null) {
            throw new IllegalArgumentException("sujetDemandeur ne peut pas etre nul.");
        }

        DecisionNoyau droitDeDecharger = kernelPermissionCheck.verifier(sujetDemandeur, KernelCapability.DECHARGER_MODULE);
        if (!droitDeDecharger.autorise()) {
            return new ResultatDechargement.Echec(droitDeDecharger.motif());
        }

        if (!manifestesCharges.containsKey(moduleId)) {
            return new ResultatDechargement.Echec("Le module '" + moduleId + "' n'est pas charge.");
        }

        List<String> dependants = manifestesCharges.values().stream()
                .filter(m -> m.dependencies().contains(moduleId))
                .map(ManifesteExtension::moduleId)
                .toList();
        if (!dependants.isEmpty()) {
            return new ResultatDechargement.Echec(
                    "Impossible de decharger '" + moduleId + "' : les modules suivants en dependent "
                            + "encore : " + String.join(", ", dependants) + ".");
        }

        int extensionsRetirees = extensionRegistry.desenregistrerToutPour(moduleId);
        pluginLoader.decharger(moduleId);
        manifestesCharges.remove(moduleId);

        return new ResultatDechargement.Succes(extensionsRetirees);
    }

    /**
     * Enregistrement manuel d'une extension isolee, sans passer par le chargement
     * complet d'un module — l'usage prevu de {@link KernelCapability#ENREGISTRER_EXTENSION},
     * distinct de {@link KernelCapability#CHARGER_MODULE} qui couvre deja
     * l'enregistrement automatique des extensions d'un module charge normalement.
     */
    public DecisionNoyau enregistrerExtensionManuelle(ExtensionDecouverte decouverte, KernelSubject sujetDemandeur) {
        if (decouverte == null) {
            throw new IllegalArgumentException("decouverte ne peut pas etre nulle.");
        }
        if (sujetDemandeur == null) {
            throw new IllegalArgumentException("sujetDemandeur ne peut pas etre nul.");
        }

        DecisionNoyau droit = kernelPermissionCheck.verifier(sujetDemandeur, KernelCapability.ENREGISTRER_EXTENSION);
        if (!droit.autorise()) {
            return droit;
        }

        extensionRegistry.enregistrer(decouverte);
        return DecisionNoyau.autorise(
                "Extension enregistree manuellement pour le module '" + decouverte.moduleId() + "'.");
    }

    public Optional<ManifesteExtension> manifestePour(String moduleId) {
        return Optional.ofNullable(manifestesCharges.get(moduleId));
    }

    public List<String> modulesCharges() {
        return List.copyOf(manifestesCharges.keySet());
    }

    private static <T> T requireNonNull(T valeur, String nom) {
        if (valeur == null) {
            throw new IllegalArgumentException(nom + " ne peut pas etre nul.");
        }
        return valeur;
    }
}
