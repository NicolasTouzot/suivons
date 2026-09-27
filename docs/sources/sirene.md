# API Sirene 3.11 (INSEE) — service d'appui

> Spike du 2026-09-27 (lot 0, étape 9). Clé dans `INSEE_API_KEY` (en-tête `X-INSEE-Api-Key-Integration`), plan « Accès public ».

## Constats

| | |
|---|---|
| Base | `https://api.insee.fr/api-sirene/3.11` |
| Quotas (en-têtes de réponse) | `x-rate-limit-limit: 30` par minute, `x-quota-limit: 2000` par heure, avec `*-remaining` et `*-reset` (horodatage en ms) : le client peut **piloter son débit sur ces en-têtes** |
| Requête groupée | `POST /siren` (formulaire) avec `q=siren:A OR siren:B OR …`, `champs=…`, `nombre=1000` : **1 000 SIREN en une requête, 2,5 s**, 1 Mo de réponse (999 trouvés sur 1 000) |
| Champs légers | `champs=siren,statutDiffusionUniteLegale,denominationUniteLegale,etatAdministratifUniteLegale,activitePrincipaleUniteLegale,categorieJuridiqueUniteLegale,categorieEntreprise` |
| Incrémental | `q=dateDernierTraitementUniteLegale:[2026-09-26T00:00:00 TO *]` : 1 874 unités modifiées en un jour |
| Historique | `periodesUniteLegale[]` : la période courante a `dateFin = null` |

## Diffusion et vie privée

Sur 999 titulaires DECP tirés au hasard :

| | Diffusion `O` | Diffusion `P` |
|---|---:|---:|
| Personnes morales | 943 | 2 |
| Entrepreneurs individuels (catégorie juridique `1000`, personnes physiques) | 50 | 4 |

- En diffusion partielle, les champs nominatifs valent `[ND]`.
- **~5 % des titulaires sont des personnes physiques** (catégorie juridique `1000`), même en diffusion `O` : jamais d'exposition nominative (SPEC §2, critère d'acceptation 6). Critère de masquage : `statutDiffusion = P` **ou** catégorie juridique `1000`.
- 28 unités sur 999 sont cessées (`etatAdministratifUniteLegale = C`).

## Conséquences

Rafraîchissement du référentiel minimal : quelques centaines de requêtes groupées pour des centaines de milliers de SIREN, compatible avec le quota horaire si le lot est étalé. La lecture en direct de la fiche (identité détaillée) reste le poste sensible (robots d'indexation, point ouvert du lot 7).
