package africa.civitas.egen.kernel.moduleregistry.service;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;

import java.util.UUID;

/**
 * Repond a la question que kernel-plugin-engine (a venir) posera avant de charger un
 * module dans une Cellule donnee : "ce module doit-il tourner ici ?". Fail-closed par
 * construction, comme tout le reste du Kernel a ce niveau : en l'absence d'une
 * Activation active trouvee, la reponse est celle de la Politique-noyau ({@link
 * africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyauQuestion#ACTIVATION_NON_RESOLUE}),
 * toujours un refus.
 *
 * <p>Cette question est independante de {@code KernelPermissionCheck} (Systeme
 * Authorization) : celle-ci verifie qui a le droit de DECLENCHER un chargement de
 * module (une capacite administrative) ; celle-la verifie si CE module precis est
 * cense tourner dans CE Contexte precis (une donnee de Souscription/Activation).
 * kernel-plugin-engine devra consulter les deux, independamment, avant de charger
 * quoi que ce soit.
 */
public interface ModuleActivationResolver {

    DecisionNoyau estActifPour(UUID celluleId, ModuleId moduleId);
}
