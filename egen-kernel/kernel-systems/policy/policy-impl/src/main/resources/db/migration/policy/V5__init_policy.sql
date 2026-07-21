-- Systeme B1 — Politique
-- Politique, Derogation. Chaque table porte integralement le Socle de Traçabilite.
--
-- Numerotee V5 dans la sequence globale de la plateforme (V1 identity,
-- V2 reference-data, V3 organization, V4 affiliation) — voir la note de convention
-- dans V1__init_identity.sql.

CREATE TABLE pol_politique (
    id                              UUID PRIMARY KEY,
    contexte_id                     UUID NOT NULL,
    contexte_nature                 VARCHAR(20) NOT NULL,
    domaine                         VARCHAR(30) NOT NULL,
    nom_regle                       VARCHAR(150) NOT NULL,
    valeur                          TEXT NOT NULL,
    statut                          VARCHAR(20) NOT NULL,
    date_entree_vigueur             DATE NOT NULL,

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

CREATE INDEX idx_pol_politique_contexte ON pol_politique(contexte_id);

CREATE TABLE pol_derogation (
    id                              UUID PRIMARY KEY,
    politique_id                    UUID NOT NULL REFERENCES pol_politique(id),
    cellule_derogatoire_id          UUID NOT NULL,
    valeur                          TEXT NOT NULL,
    justification                   TEXT NOT NULL,
    date_entree_vigueur             DATE NOT NULL,
    date_fin                        DATE,

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
    supprime_par_systeme_label      VARCHAR(100),

    CONSTRAINT uq_pol_derogation_politique_cellule UNIQUE (politique_id, cellule_derogatoire_id)
);

CREATE INDEX idx_pol_derogation_politique ON pol_derogation(politique_id);
