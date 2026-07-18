-- Systeme B4 — Referentiels Communs
-- Nomenclatures universelles (Pays, Langue, Devise, Fuseau Horaire, Unite de Mesure)
-- et gabarits de Lexique Organisationnel (Modele Sectoriel, Type de Cellule Modele,
-- Mandat Modele). Chaque table porte integralement le Socle de Traçabilite.

CREATE TABLE refdata_pays (
    id                              UUID PRIMARY KEY,
    code_alpha2                     VARCHAR(2) NOT NULL UNIQUE,
    code_alpha3                     VARCHAR(3) NOT NULL UNIQUE,
    nom_officiel                    VARCHAR(200) NOT NULL,
    nom_usuel                       VARCHAR(100) NOT NULL,
    indicatif_telephonique          VARCHAR(6) NOT NULL,
    code_devise_par_defaut          VARCHAR(3) NOT NULL,

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

CREATE TABLE refdata_langue (
    id                              UUID PRIMARY KEY,
    code_iso_639                    VARCHAR(3) NOT NULL UNIQUE,
    nom_officiel                    VARCHAR(100) NOT NULL,
    nom_natif                       VARCHAR(100) NOT NULL,
    sens_ecriture                   VARCHAR(20) NOT NULL,

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

CREATE TABLE refdata_devise (
    id                              UUID PRIMARY KEY,
    code_iso_4217                   VARCHAR(3) NOT NULL UNIQUE,
    symbole                         VARCHAR(10) NOT NULL,
    nom                             VARCHAR(100) NOT NULL,
    nombre_decimales                INT NOT NULL,

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

CREATE TABLE refdata_fuseau_horaire (
    id                              UUID PRIMARY KEY,
    identifiant_iana                VARCHAR(100) NOT NULL UNIQUE,
    libelle_usuel                   VARCHAR(150) NOT NULL,
    decalage_utc_reference           VARCHAR(6) NOT NULL,

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

CREATE TABLE refdata_unite_mesure (
    id                              UUID PRIMARY KEY,
    nom                             VARCHAR(100) NOT NULL UNIQUE,
    symbole                         VARCHAR(20) NOT NULL,
    categorie                       VARCHAR(20) NOT NULL,
    facteur_conversion              NUMERIC(20,6) NOT NULL,

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

CREATE TABLE refdata_modele_sectoriel (
    id                              UUID PRIMARY KEY,
    nom_secteur                     VARCHAR(150) NOT NULL,
    description                     TEXT NOT NULL,
    version_modele                  VARCHAR(30) NOT NULL,
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

CREATE TABLE refdata_type_cellule_modele (
    id                              UUID PRIMARY KEY,
    modele_sectoriel_id             UUID NOT NULL REFERENCES refdata_modele_sectoriel(id),
    libelle_metier_suggere          VARCHAR(100) NOT NULL,
    niveau_indicatif_suggere        INT NOT NULL,
    type_parent_suggere_id          UUID REFERENCES refdata_type_cellule_modele(id),

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

CREATE INDEX idx_refdata_tcm_modele_sectoriel ON refdata_type_cellule_modele(modele_sectoriel_id);

CREATE TABLE refdata_mandat_modele (
    id                              UUID PRIMARY KEY,
    modele_sectoriel_id             UUID NOT NULL REFERENCES refdata_modele_sectoriel(id),
    libelle_suggere                 VARCHAR(100) NOT NULL,
    niveau_autorite_indicatif       INT NOT NULL,
    description_responsabilites     TEXT NOT NULL,

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

CREATE INDEX idx_refdata_mm_modele_sectoriel ON refdata_mandat_modele(modele_sectoriel_id);
