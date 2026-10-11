#!/usr/bin/env bash
# Suivre Notre Argent - recette locale (macOS, Linux) : docs/demarrer-en-local.md.
# Seul prérequis : Docker (Docker Desktop sur macOS).
# Usage : ./suivons.sh [demarrer|demo|donnees|ouvrir|etat|arreter|reinitialiser]
set -euo pipefail
cd "$(dirname "$0")"

SITE=http://localhost:4000

titre() { printf '\n\033[36m== %s\033[0m\n' "$1"; }
ok() { printf '   \033[32m%s\033[0m\n' "$1"; }
info() { printf '   %s\n' "$1"; }
erreur() { printf '\n   \033[31mErreur : %s\033[0m\n' "$1" >&2; exit 1; }

verifier_docker() {
  command -v docker > /dev/null || erreur "Docker n'est pas installé : https://www.docker.com/products/docker-desktop/"
  docker info > /dev/null 2>&1 || erreur "Docker n'est pas démarré. Lancez Docker Desktop (ou le service docker), puis relancez."
}

ouvrir() {
  if command -v open > /dev/null; then open "$SITE"
  elif command -v xdg-open > /dev/null; then xdg-open "$SITE" > /dev/null 2>&1 || true
  else info "Ouvrez $SITE dans votre navigateur."
  fi
}

attendre_site() {
  info "Attente du site (jusqu'à 3 minutes)..."
  for _ in $(seq 1 90); do
    if curl -sf http://localhost:8080/actuator/health > /dev/null && curl -sf "$SITE" > /dev/null; then return; fi
    sleep 2
  done
  erreur "le site ne répond pas. Journaux : docker compose --profile stack logs"
}

demarrer() {
  verifier_docker
  titre 'Construction des images (première fois : environ 10 minutes, ensuite quelques secondes)'
  docker compose --profile stack --profile ingestions build
  titre 'Démarrage de la base de données'
  docker compose up -d --wait db
  titre 'Mise à jour du schéma de la base'
  docker compose --profile outils run --rm migrate
  titre "Démarrage de l'API et du site"
  docker compose --profile stack up -d
  attendre_site
  ok "Site disponible : $SITE"
  info 'Base vide au premier démarrage : ./suivons.sh demo (démonstration) ou ./suivons.sh donnees (vraies données).'
  ouvrir
}

demo() {
  verifier_docker
  titre 'Chargement du jeu de démonstration (remplace les données déjà chargées)'
  docker compose cp fixtures/demo/parcours.sql db:/tmp/parcours.sql
  docker compose exec -T db psql -q -v ON_ERROR_STOP=1 -U suivons -d suivons -f /tmp/parcours.sql
  ok "Jeu de démonstration chargé. Essayez : $SITE/entreprises/329338883"
}

cle_insee() {
  if [ -f .env ] && grep -Eq '^INSEE_API_KEY=.+' .env; then return; fi
  if [ -n "${INSEE_API_KEY:-}" ]; then return; fi
  titre "Clé de l'API Sirene (INSEE)"
  info 'Nécessaire pour relier chaque marché à son entreprise. Clé gratuite : https://portail-api.insee.fr/'
  info 'Elle est enregistrée dans le fichier .env de ce dossier, jamais partagée ni affichée.'
  read -r -s -p '   Collez votre clé puis appuyez sur Entrée : ' cle; echo
  [ -n "$cle" ] || erreur 'aucune clé saisie.'
  printf 'INSEE_API_KEY=%s\n' "$cle" >> .env
  ok 'Clé enregistrée dans .env'
}

donnees() {
  verifier_docker
  cle_insee
  titre 'Chargement des vraies données DECP (environ 40 minutes la première fois)'
  docker compose --profile ingestions run --rm ingestion-decp
  titre "Nomenclature d'activités et mise à jour des entreprises (environ 10 minutes)"
  docker compose --profile ingestions run --rm ingestion-sirene
  ok "Vraies données chargées. Site : $SITE"
}

arreter() {
  verifier_docker
  titre 'Arrêt (les données sont conservées)'
  docker compose --profile stack --profile ingestions stop
  ok 'Arrêté. Pour relancer : ./suivons.sh demarrer'
}

reinitialiser() {
  verifier_docker
  printf '   \033[33m%s\033[0m\n' 'Tout sera effacé : base de données et fichiers téléchargés. Les images restent.'
  read -r -p '   Tapez OUI pour confirmer : ' confirmation
  [ "$confirmation" = OUI ] || { info 'Annulé.'; return; }
  docker compose --profile stack --profile ingestions down -v
  ok 'Tout est effacé. Pour repartir : ./suivons.sh demarrer'
}

aide() {
  cat <<'AIDE'

 Suivre Notre Argent - recette locale
 ------------------------------------
 ./suivons.sh demarrer        Démarrer le site (première fois : environ 10 minutes)
 ./suivons.sh demo            Charger le jeu de démonstration (30 secondes)
 ./suivons.sh donnees         Charger les vraies données DECP (environ 50 minutes, clé INSEE demandée une fois)
 ./suivons.sh ouvrir          Ouvrir le site dans le navigateur
 ./suivons.sh etat            Voir ce qui tourne
 ./suivons.sh arreter         Arrêter (les données sont conservées)
 ./suivons.sh reinitialiser   Tout effacer et repartir de zéro
AIDE
}

case "${1:-}" in
  demarrer) demarrer ;;
  demo) demo ;;
  donnees) donnees ;;
  ouvrir) ouvrir ;;
  etat) verifier_docker; docker compose --profile stack ps ;;
  arreter) arreter ;;
  reinitialiser) reinitialiser ;;
  *) aide ;;
esac
