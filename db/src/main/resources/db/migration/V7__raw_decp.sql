-- Données brutes de la source DECP (SPEC.md §6.2) : dernière version reçue de chaque enregistrement
-- (couple marché, titulaire) et run qui l'a modifiée.
CREATE TABLE raw.decp_record (
    source_record_id text        PRIMARY KEY,
    run_id           bigint      NOT NULL REFERENCES ops.ingestion_run (id),
    payload          jsonb       NOT NULL,
    checksum         text        NOT NULL,
    recu_le          timestamptz NOT NULL
);

COMMENT ON TABLE raw.decp_record IS 'DECP : un enregistrement par couple (marché, titulaire), après fusion des lignes du jeu';
