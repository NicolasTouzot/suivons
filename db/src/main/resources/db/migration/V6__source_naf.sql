-- Source de la nomenclature NAF (core.naf), chargée par ingestion-sirene depuis les fichiers de l'INSEE
-- (SPEC.md §6.3) : ses chargements sont tracés dans ops.ingestion_run comme ceux des autres sources.
INSERT INTO ops.source (code, libelle, producteur, licence, url_reference, frequence) VALUES
    ('NAF', 'Nomenclature d''activités française (NAF rév. 2 et NAF 2025)', 'INSEE', 'Licence Ouverte 2.0',
     'https://www.insee.fr/fr/information/2406147', 'À chaque changement de nomenclature');
