# Démarrer le site sur son ordinateur

Ce guide permet de faire tourner Suivre Notre Argent sur votre poste, sans rien installer d'autre que Docker Desktop. C'est la **recette locale** : la même configuration que celle qui sera déployée chez l'hébergeur.

## 1. Installer Docker Desktop (une seule fois)

1. Téléchargez Docker Desktop : <https://www.docker.com/products/docker-desktop/>.
2. Installez-le avec les options proposées. Sous Windows, acceptez WSL 2 et redémarrez l'ordinateur si l'installateur le demande.
3. Lancez Docker Desktop et attendez l'indication **Engine running** en bas à gauche.
4. Dans Docker Desktop, *Settings > Resources*, laissez au moins **4 Go de mémoire**.

Prévoyez environ 3 Go d'espace disque (images, base de données, fichiers téléchargés).

## 2. Récupérer le projet

Sur la page GitHub du projet, bouton **Code > Download ZIP**, puis extrayez le dossier (par exemple dans `Documents\suivons`). Git n'est pas nécessaire.

## 3. Lancer

**Windows** : double-cliquez sur `suivons.cmd`. Un menu s'ouvre :

```
 Suivre Notre Argent - recette locale
 ------------------------------------
 1. Démarrer le site (première fois : environ 10 minutes)
 2. Charger le jeu de démonstration (30 secondes)
 3. Charger les vraies données DECP (environ 50 minutes, clé INSEE demandée une fois)
 4. Ouvrir le site dans le navigateur
 5. Voir ce qui tourne
 6. Arrêter (les données sont conservées)
 7. Tout effacer et repartir de zéro
 0. Quitter
```

**macOS, Linux** : dans un terminal ouvert dans le dossier, `./suivons.sh demarrer`, puis `./suivons.sh demo` ou `./suivons.sh donnees` (`./suivons.sh` seul affiche l'aide).

Première utilisation :

1. **Démarrer le site** (1). La première fois, les images sont construites : environ 10 minutes. Le navigateur s'ouvre sur <http://localhost:4000>.
2. **Charger le jeu de démonstration** (2) pour essayer tout de suite, par exemple la fiche <http://localhost:4000/entreprises/329338883>.
3. Ou **charger les vraies données** (3) : tout le jeu DECP (plus de 700 000 lignes), rattaché aux entreprises via l'API Sirene. Il faut une clé gratuite de l'API Sirene, à créer sur <https://portail-api.insee.fr/> ; elle est demandée une seule fois et enregistrée dans le fichier `.env` du dossier, qui ne doit jamais être partagé.

Les données restent après un arrêt (6). **Tout effacer** (7) supprime la base et les fichiers téléchargés.

## En cas de problème

| Message | Que faire |
|---|---|
| « Docker n'est pas installé » | Installer Docker Desktop (étape 1). |
| « Docker Desktop n'est pas démarré » | Lancer Docker Desktop et attendre « Engine running ». |
| « port is already allocated » | Un autre logiciel utilise le port 4000, 8080 ou 5432 : le fermer, ou arrêter l'autre projet Docker. |
| « Le site ne répond pas » | Voir les journaux : `docker compose --profile stack logs`, et les transmettre. |
| Le chargement des vraies données s'arrête sur une erreur de quota | L'API Sirene limite le nombre d'appels par heure : relancer plus tard, le chargement reprend là où il s'est arrêté. |

## Ce qui tourne

| Service | Adresse | Rôle |
|---|---|---|
| `front` | <http://localhost:4000> | Le site (rendu serveur Angular) |
| `api` | <http://localhost:8080/api/v1> | L'API REST ; `/actuator/health` pour son état |
| `db` | `localhost:5432` | PostgreSQL (utilisateur et mot de passe de développement `suivons`) |
| `migrate`, `ingestion-decp`, `ingestion-sirene` | — | Lancés à la demande par le script, puis arrêtés |

Tout est construit dans Docker à partir du `Dockerfile` à la racine du projet ; aucun Java ni Node n'est nécessaire sur le poste.
