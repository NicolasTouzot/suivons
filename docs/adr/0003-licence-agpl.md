# 0003 — Licence du code : AGPL-3.0-or-later

- Statut : Accepté
- Date : 2026-09-26

## Contexte

Décision ouverte du `SPEC.md` §14 (options : AGPL-3.0, MIT, EUPL). Objectifs : projet pleinement open source, crédible pour un usage citoyen, et protégé contre une réappropriation fermée, notamment sous forme de service en ligne.

## Décision

Le code est publié sous **GNU AGPL-3.0-or-later**.

- MIT écartée : permet une reprise propriétaire sans contrepartie.
- EUPL-1.2 écartée : copyleft proche, mais compatibilité descendante vers d'autres licences (GPL, AGPL…) qui dilue la protection ; l'AGPL est mieux connue de l'écosystème.
- L'AGPL impose à quiconque exploite une version modifiée en ligne d'en publier le code source (article 13).

## Conséquences

- Le front doit exposer un lien « Code source » vers le dépôt (obligation de l'article 13, à livrer au plus tard au lot 6).
- Toute dépendance doit être compatible AGPL (Apache-2.0, MIT, BSD, LGPL, GPL-3.0 : oui ; licences propriétaires ou « non commerciales » : non). Contrôle à ajouter dans la CI.
- Contributions externes : acceptées sous la même licence (inbound = outbound). Si une double licence commerciale est envisagée un jour, un accord de contribution (CLA) devra être mis en place **avant** les premières contributions externes.
- Les données restent sous la licence de leurs producteurs ; l'AGPL ne couvre que le code.
