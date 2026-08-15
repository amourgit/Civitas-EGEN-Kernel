package africa.civitas.egen.kernel.moduleregistry.service;

import africa.civitas.egen.kernel.domain.module.Activation;
import africa.civitas.egen.kernel.moduleregistry.command.ActiverModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.DesactiverModuleCommand;

import java.util.List;
import java.util.UUID;

/** Administre les Activations — le troisieme et dernier palier de la cascade (§B.11). */
public interface ActivationService {

    /**
     * @throws africa.civitas.egen.kernel.moduleregistry.exception.ModuleIntrouvableAuCatalogueException
     *         si ce module n'est pas au Catalogue
     * @throws africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionRequiseException
     *         si {@code commande.contexteSouscripteurId()} n'a pas de Souscription active pour ce module
     * @throws africa.civitas.egen.kernel.moduleregistry.exception.ActivationDejaActiveException
     *         si une Activation active existe deja pour ce Contexte et ce module
     */
    Activation activer(ActiverModuleCommand commande);

    /**
     * @throws africa.civitas.egen.kernel.moduleregistry.exception.ActivationIntrouvableException
     *         si l'Activation est introuvable ou deja eteinte
     */
    void desactiver(DesactiverModuleCommand commande);

    List<Activation> listerPourContexte(UUID contexteId);
}
