-- Schéma ops : sources, exécutions d'ingestion, rejets (SPEC.md §6.1).
-- ops.signalement arrive avec le lot 6 (signalement d'erreur).

CREATE TABLE ops.source (
    code          text PRIMARY KEY,
    libelle       text NOT NULL,
    producteur    text NOT NULL,
    licence       text,
    url_reference text NOT NULL,
    frequence     text NOT NULL
);

COMMENT ON TABLE ops.source IS 'Sources de données (flux et services d''appui), SPEC §4';

INSERT INTO ops.source (code, libelle, producteur, licence, url_reference, frequence) VALUES
    ('SIRENE', 'API Sirene', 'INSEE', 'Licence Ouverte 2.0',
     'https://portail-api.insee.fr/', 'Hebdomadaire'),
    ('RECHERCHE_ENTREPRISES', 'API Recherche d''entreprises', 'DINUM', 'Licence Ouverte 2.0',
     'https://recherche-entreprises.api.gouv.fr', 'À la demande'),
    ('DECP', 'Données essentielles de la commande publique', 'Direction des affaires juridiques (DAJ)',
     'Licence Ouverte 2.0', 'https://data.economie.gouv.fr/explore/dataset/decp-2022-marches-valides/', 'Hebdomadaire'),
    ('TAM', 'Transparency Award Module', 'Commission européenne, DG Concurrence', NULL,
     'https://webgate.ec.europa.eu/competition/transparency/public', 'Mensuelle'),
    ('KOHESIO', 'Kohesio', 'Commission européenne, DG REGIO', NULL,
     'https://kohesio.ec.europa.eu', 'Mensuelle');

CREATE TABLE ops.ingestion_run (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_code      text        NOT NULL REFERENCES ops.source (code),
    version_source   text,
    debut            timestamptz NOT NULL DEFAULT now(),
    fin              timestamptz,
    statut           text        NOT NULL DEFAULT 'EN_COURS',
    lus              integer     NOT NULL DEFAULT 0,
    charges          integer     NOT NULL DEFAULT 0,
    rejetes          integer     NOT NULL DEFAULT 0,
    checksum_fichier text,
    CONSTRAINT ingestion_run_statut_ck CHECK (statut IN ('EN_COURS', 'SUCCES', 'ECHEC')),
    CONSTRAINT ingestion_run_fin_ck CHECK ((statut = 'EN_COURS') = (fin IS NULL)),
    CONSTRAINT ingestion_run_compteurs_ck CHECK (lus >= 0 AND charges >= 0 AND rejetes >= 0)
);

CREATE INDEX ingestion_run_source_debut_idx ON ops.ingestion_run (source_code, debut DESC);

COMMENT ON TABLE ops.ingestion_run IS 'Exécutions des ingestions (pipeline SPEC §7.3), une ligne par run';

CREATE TABLE ops.rejet (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id           bigint      NOT NULL REFERENCES ops.ingestion_run (id),
    source_record_id text,
    motif            text        NOT NULL,
    payload          jsonb,
    cree_le          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX rejet_run_idx ON ops.rejet (run_id);

COMMENT ON TABLE ops.rejet IS 'Enregistrements source rejetés à la transformation, avec leur motif';
