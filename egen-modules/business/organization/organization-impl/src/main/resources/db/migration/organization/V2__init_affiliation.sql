-- Rattachements (ex-Systeme A3, desormais sous-domaine "affiliation" du module
-- business Organization — fusion actee, Charte v3 §C.1).
-- Affectation, Lexique des Mandats, Mandat (symetrique au Type de Cellule),
-- Delegation. Chaque table porte integralement le Socle de Traçabilite.
--
-- Renumerotee V2 dans la sequence propre a ce module (voir la note de convention
-- dans V1__init_organization.sql) — anciennement V4 dans la sequence globale
-- platform-wide, abandonnee lors du refactoring vers la Charte v3.

CREATE TABLE aff_affectation (
    id                              UUID PRIMARY KEY,
    personne_id                     UUID NOT NULL,
    cellule_id                      UUID NOT NULL,
    mandat_id                       UUID NOT NULL,
    quotite_engagement              VARCHAR(20) NOT NULL,
    date_debut                      DATE NOT NULL,
    date_fin                        DATE,
    statut                          VARCHAR(30) NOT NULL,
    motif_fin                       VARCHAR(20),

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

CREATE INDEX idx_aff_affectation_personne ON aff_affectation(personne_id);
CREATE INDEX idx_aff_affectation_cellule ON aff_affectation(cellule_id);

CREATE TABLE aff_lexique_des_mandats (
    id                              UUID PRIMARY KEY,
    organisation_id                 UUID NOT NULL,
    nom                             VARCHAR(150) NOT NULL,
    description                     TEXT NOT NULL,
    modele_sectoriel_origine_id     UUID,

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

CREATE INDEX idx_aff_lexique_organisation ON aff_lexique_des_mandats(organisation_id);

CREATE TABLE aff_mandat (
    id                              UUID PRIMARY KEY,
    lexique_mandats_id              UUID NOT NULL REFERENCES aff_lexique_des_mandats(id),
    libelle                         VARCHAR(100) NOT NULL,
    description                     TEXT NOT NULL,
    niveau_autorite_indicatif       INT NOT NULL,
    mandat_modele_origine_id        UUID,
    statut                          VARCHAR(20) NOT NULL,

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

CREATE INDEX idx_aff_mandat_lexique ON aff_mandat(lexique_mandats_id);

CREATE TABLE aff_mandat_supervises (
    mandat_id           UUID NOT NULL REFERENCES aff_mandat(id) ON DELETE CASCADE,
    mandat_supervise_id UUID NOT NULL,
    PRIMARY KEY (mandat_id, mandat_supervise_id)
);

CREATE TABLE aff_delegation (
    id                              UUID PRIMARY KEY,
    affectation_origine_id          UUID NOT NULL REFERENCES aff_affectation(id),
    personne_beneficiaire_id        UUID NOT NULL,
    etendue                         VARCHAR(20) NOT NULL,
    actions_couvertes               TEXT,
    date_debut                      DATE NOT NULL,
    date_fin                        DATE NOT NULL,
    motif                           VARCHAR(20) NOT NULL,
    statut                          VARCHAR(30) NOT NULL,

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

CREATE INDEX idx_aff_delegation_affectation ON aff_delegation(affectation_origine_id);
CREATE INDEX idx_aff_delegation_beneficiaire ON aff_delegation(personne_beneficiaire_id);
