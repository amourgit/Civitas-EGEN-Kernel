-- Systeme Modules (B2, Niveau 0) — Catalogue, Souscription, Activation (§B.2, §B.11).
-- Cascade stricte, imposee au niveau service (ActivationServiceImpl,
-- SouscriptionServiceImpl) : un module doit etre au Catalogue avant de pouvoir etre
-- Souscrit, et un Contexte souscripteur doit avoir une Souscription active avant
-- qu'un Contexte cible qui en depend ne puisse l'Activer. Aucune de ces trois tables
-- ne reference quoi que ce soit par cle etrangere vers un module Niveau 2 : ce sont
-- des UUID nus (contexte_id), jamais une reference vers les tables d'un module
-- metier — le Niveau 0 ne depend jamais du Niveau 2, meme au niveau du schema.
--
-- Sequence Flyway propre a ce module (table flyway_schema_history_moduleregistry) —
-- aucune combinaison avec un autre module pour SES PROPRES tests. Renumerotee V3
-- (anciennement V1) parce que kernel-bootstrap, seul module a combiner les
-- locations identity + authorization + module-registry dans une meme execution
-- Flyway de production, a besoin de numeros mutuellement uniques dans cet ensemble
-- combine — V1 est reserve a identity, V2 a authorization (voir leurs migrations
-- respectives pour le detail de cette regle).

CREATE TABLE modreg_catalogue_entree (
    id                              UUID PRIMARY KEY,
    module_id                       VARCHAR(100) NOT NULL,
    nom                             VARCHAR(150) NOT NULL,
    description                     TEXT NOT NULL,

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

    CONSTRAINT modreg_catalogue_entree_module_id_unique UNIQUE (module_id)
);

CREATE TABLE modreg_souscription (
    id                              UUID PRIMARY KEY,
    contexte_id                     UUID NOT NULL,
    module_id                       VARCHAR(100) NOT NULL,

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

-- Une seule Souscription ACTIVE par (Contexte, module) — index partiel, plusieurs
-- souscriptions resiliees successives pour le meme couple restant possibles.
CREATE UNIQUE INDEX modreg_souscription_active_unique
    ON modreg_souscription (contexte_id, module_id)
    WHERE supprime_le IS NULL;

CREATE INDEX modreg_souscription_contexte_idx ON modreg_souscription (contexte_id);

CREATE TABLE modreg_activation (
    id                              UUID PRIMARY KEY,
    contexte_id                     UUID NOT NULL,
    module_id                       VARCHAR(100) NOT NULL,

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

-- Une seule Activation ACTIVE par (Contexte, module) — meme discipline que ci-dessus.
CREATE UNIQUE INDEX modreg_activation_active_unique
    ON modreg_activation (contexte_id, module_id)
    WHERE supprime_le IS NULL;

CREATE INDEX modreg_activation_contexte_idx ON modreg_activation (contexte_id);
