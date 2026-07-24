package africa.civitas.egen.kernel.identity;

import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * L'unique implementation du primitif Identite (Niveau 1) — compilee en dur, jamais
 * retiree, jamais substituee. Voir {@link KernelSubjectService} pour ce qu'elle ne
 * fait volontairement pas.
 */
@ApplicationScoped
public class KernelSubjectServiceImpl implements KernelSubjectService {

    static final String LABEL_BOOTSTRAP = "kernel-bootstrap";
    static final String PREFIXE_SUJET_ORDINAIRE = "kernel-sujet:";

    @Override
    public boolean estBootstrap(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id ne peut pas etre nul.");
        }
        return id.equals(KernelSubject.BOOTSTRAP_ID);
    }

    @Override
    public KernelSubject resoudre(UUID id) {
        return new KernelSubject(id, estBootstrap(id));
    }

    @Override
    public Acteur versActeur(KernelSubject sujet) {
        if (sujet == null) {
            throw new IllegalArgumentException("sujet ne peut pas etre nul.");
        }
        return sujet.bootstrap()
                ? Acteur.systeme(LABEL_BOOTSTRAP)
                : Acteur.systeme(PREFIXE_SUJET_ORDINAIRE + sujet.id());
    }
}

