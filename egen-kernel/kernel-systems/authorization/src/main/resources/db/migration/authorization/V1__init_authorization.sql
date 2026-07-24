-- Systeme Authorization (primitif Niveau 1, Charte v3 §A.5) — la seule forme de
-- "permission" que le Kernel connaisse a son propre niveau, avant que SpiceDB ne
-- gouverne les autorisations metier (Niveau 2). Voir KernelPermissionCheck
-- (kernel-sdk) et KernelCapabiliteOctroiService pour la logique complete.
--
-- Le sujet bootstrap (KernelSubject.BOOTSTRAP_ID) n'apparait jamais dans cette table :
-- il est autorise pour toute capacite noyau par construction, sans octroi (voir la
-- validation du constructeur canonique de KernelCapabiliteOctroi).
--
-- Une revocation est toujours une suppression logique (supprime_le renseigne),
-- jamais une suppression physique — meme discipline de Traçabilite que partout
-- ailleurs dans EGEN (Charte v3, §B.13 : "rien n'est jamais supprime physiquement").
--
-- Sequence Flyway propre a ce module (V1, table flyway_schema_history_authorization) —
-- ce module ne combine ses migrations avec aucun autre pour ses tests (a la
-- difference d'organization-impl qui combine identity), donc aucune coordination de
-- numerotation n'est necessaire ici. Voir la note de convention dans
-- V1__init_identity.sql (identity-provider-keycloak) pour le detail de cette regle.

CREATE TABLE authz_capacite_octroi (
    id                              UUID PRIMARY KEY,
    sujet_id                        UUID NOT NULL,
    capacite                        VARCHAR(40) NOT NULL,

    cree_le                         TIMESTAMPTZ NOT NULL,
    cree_par_type                   VARCHAR(20) NOT NULL,
    cree_par_personne_id            UUID,
    cree_par_systeme_label          VARCHAR(100),
    modifie_le                      TIMESTAMPTZ NOT NULL,
    modifie_par_type                VARCHAR(20) NOT NULL,
    modifie_par_personne_id         UUID,
    modifie_par_systeme_label       VARCHAR(100),
    version                         BIGINT NOT NULL,
    origine_donnee                  VARCHAR(30) NOT NULL,
    motif_derniere_modification     TEXT,
    supprime_le                     TIMESTAMPTZ,
    supprime_par_type               VARCHAR(20),
    supprime_par_personne_id        UUID,
    supprime_par_systeme_label      VARCHAR(100)
);

-- Un seul octroi ACTIF par (sujet, capacite) — un index partiel plutot qu'une
-- contrainte UNIQUE classique, puisque plusieurs octrois revoques successifs pour le
-- meme couple doivent rester possibles au fil du temps (chacun garde sa propre trace).
CREATE UNIQUE INDEX authz_capacite_octroi_actif_unique
    ON authz_capacite_octroi (sujet_id, capacite)
    WHERE supprime_le IS NULL;

CREATE INDEX authz_capacite_octroi_sujet_idx ON authz_capacite_octroi (sujet_id);
