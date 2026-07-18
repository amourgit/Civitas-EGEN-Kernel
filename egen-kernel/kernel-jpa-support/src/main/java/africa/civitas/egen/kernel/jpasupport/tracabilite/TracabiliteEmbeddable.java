package africa.civitas.egen.kernel.jpasupport.tracabilite;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.ActeurType;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection JPA du Socle de Traçabilite (kernel-sdk). Cette classe existe uniquement
 * pour la persistance — la logique d'evolution (incrementation de version, exigence
 * de motif...) reste entierement dans {@link Tracabilite}, jamais dupliquee ici.
 * Toute lecture passe par {@link #toDomain()}, toute ecriture par {@link #fromDomain}.
 */
@Embeddable
public class TracabiliteEmbeddable {

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "cree_par_type", nullable = false, length = 20)
    private ActeurType creeParType;

    @Column(name = "cree_par_personne_id")
    private UUID creeParPersonneId;

    @Column(name = "cree_par_systeme_label", length = 100)
    private String creeParSystemeLabel;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "modifie_par_type", nullable = false, length = 20)
    private ActeurType modifieParType;

    @Column(name = "modifie_par_personne_id")
    private UUID modifieParPersonneId;

    @Column(name = "modifie_par_systeme_label", length = 100)
    private String modifieParSystemeLabel;

    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "origine_donnee", nullable = false, length = 30)
    private OrigineDonnee origineDonnee;

    @Column(name = "motif_derniere_modification", columnDefinition = "text")
    private String motifDerniereModification;

    @Column(name = "supprime_le")
    private Instant supprimeLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "supprime_par_type", length = 20)
    private ActeurType supprimeParType;

    @Column(name = "supprime_par_personne_id")
    private UUID supprimeParPersonneId;

    @Column(name = "supprime_par_systeme_label", length = 100)
    private String supprimeParSystemeLabel;

    /** Constructeur requis par JPA — ne pas utiliser directement. */
    protected TracabiliteEmbeddable() {
    }

    public static TracabiliteEmbeddable fromDomain(Tracabilite t) {
        TracabiliteEmbeddable e = new TracabiliteEmbeddable();
        e.creeLe = t.creeLe();
        e.creeParType = t.creePar().type();
        e.creeParPersonneId = t.creePar().personneId();
        e.creeParSystemeLabel = t.creePar().systemeLabel();
        e.modifieLe = t.modifieLe();
        e.modifieParType = t.modifiePar().type();
        e.modifieParPersonneId = t.modifiePar().personneId();
        e.modifieParSystemeLabel = t.modifiePar().systemeLabel();
        e.version = t.version();
        e.origineDonnee = t.origineDonnee();
        e.motifDerniereModification = t.motifDerniereModification();
        if (t.supprimeLe() != null) {
            e.supprimeLe = t.supprimeLe();
            e.supprimeParType = t.supprimePar().type();
            e.supprimeParPersonneId = t.supprimePar().personneId();
            e.supprimeParSystemeLabel = t.supprimePar().systemeLabel();
        }
        return e;
    }

    public Tracabilite toDomain() {
        Acteur supprimePar = supprimeParType == null ? null
                : new Acteur(supprimeParType, supprimeParPersonneId, supprimeParSystemeLabel);
        return new Tracabilite(
                creeLe,
                new Acteur(creeParType, creeParPersonneId, creeParSystemeLabel),
                modifieLe,
                new Acteur(modifieParType, modifieParPersonneId, modifieParSystemeLabel),
                version,
                origineDonnee,
                motifDerniereModification,
                supprimeLe,
                supprimePar);
    }
}
