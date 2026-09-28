"""Génère decp-2022-juin-2026-extrait.parquet depuis l'extrait JSON (lisible et relu), avec les types de l'export
Parquet réel de data.economie.gouv.fr. Usage : python3 fixtures/decp/generer-parquet.py (paquet duckdb requis)."""
from pathlib import Path

import duckdb

ICI = Path(__file__).parent
# Types des colonnes non textuelles de l'export réel (toutes les autres sont VARCHAR)
TYPES = {"dureemois": "BIGINT", "datenotification": "DATE", "datepublicationdonnees": "DATE", "montant": "DOUBLE"}

base = duckdb.connect()
source = ICI / "decp-2022-juin-2026-extrait.json"
colonnes = [c[0] for c in base.sql(f"DESCRIBE SELECT * FROM read_json('{source}')").fetchall()]
selection = ", ".join(f"CAST({c} AS {TYPES.get(c, 'VARCHAR')}) AS {c}" for c in colonnes)
base.sql(f"COPY (SELECT {selection} FROM read_json('{source}')) "
         f"TO '{ICI / 'decp-2022-juin-2026-extrait.parquet'}' (FORMAT parquet)")
print(base.sql(f"SELECT count(*) FROM '{ICI / 'decp-2022-juin-2026-extrait.parquet'}'").fetchone()[0], "lignes")
