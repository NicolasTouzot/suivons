<#
  Recette locale de Suivre Notre Argent (Windows) : docs/demarrer-en-local.md.
  Seul prérequis : Docker Desktop. Lancement : double-clic sur suivons.cmd, ou « suivons.cmd demarrer ».
  Fichier enregistré en UTF-8 avec BOM : indispensable pour les accents sous Windows PowerShell 5.1.
#>
param([string]$Commande = '')

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location (Split-Path -Parent $PSScriptRoot)

$Site = 'http://localhost:4000'

function Titre([string]$Texte) { Write-Host ''; Write-Host "== $Texte" -ForegroundColor Cyan }
function Ok([string]$Texte) { Write-Host "   $Texte" -ForegroundColor Green }
function Info([string]$Texte) { Write-Host "   $Texte" }
function Alerte([string]$Texte) { Write-Host "   $Texte" -ForegroundColor Yellow }

# docker compose, arrêt à la première erreur
function Compose {
    & docker compose @args
    if ($LASTEXITCODE -ne 0) { throw "La commande « docker compose $($args -join ' ') » a échoué." }
}

function Verifier-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker n'est pas installé. Installez Docker Desktop : https://www.docker.com/products/docker-desktop/ puis relancez."
    }
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop n'est pas démarré. Lancez-le (menu Démarrer > Docker Desktop), attendez « Engine running », puis relancez."
    }
}

function Attendre-Site {
    Info "Attente du site (jusqu'à 3 minutes)..."
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $api = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 2
            $page = Invoke-WebRequest -Uri $Site -UseBasicParsing -TimeoutSec 5
            if ($api.StatusCode -eq 200 -and $page.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw 'Le site ne répond pas. Journaux : docker compose --profile stack logs'
}

function Demarrer {
    Verifier-Docker
    Titre 'Construction des images (première fois : environ 10 minutes, ensuite quelques secondes)'
    Compose --profile stack --profile ingestions build
    Titre 'Démarrage de la base de données'
    Compose up -d --wait db
    Titre 'Mise à jour du schéma de la base'
    Compose --profile outils run --rm migrate
    Titre "Démarrage de l'API et du site"
    Compose --profile stack up -d
    Attendre-Site
    Ok "Site disponible : $Site"
    Info 'Base vide au premier démarrage : chargez le jeu de démonstration (2) ou les vraies données (3).'
    Start-Process $Site
}

function Charger-Demo {
    Verifier-Docker
    Titre 'Chargement du jeu de démonstration (remplace les données déjà chargées)'
    Compose cp fixtures/demo/parcours.sql db:/tmp/parcours.sql
    Compose exec -T db psql -q -v ON_ERROR_STOP=1 -U suivons -d suivons -f /tmp/parcours.sql
    Ok "Jeu de démonstration chargé. Essayez : $Site/entreprises/329338883"
}

function Cle-Insee {
    if ((Test-Path .env) -and (Select-String -Path .env -Pattern '^INSEE_API_KEY=.+' -Quiet)) { return }
    Titre "Clé de l'API Sirene (INSEE)"
    Info 'Nécessaire pour relier chaque marché à son entreprise. Clé gratuite : https://portail-api.insee.fr/'
    Info 'Elle est enregistrée dans le fichier .env de ce dossier, jamais partagée ni affichée.'
    $saisie = Read-Host '   Collez votre clé puis appuyez sur Entrée' -AsSecureString
    $cle = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($saisie))
    if ([string]::IsNullOrWhiteSpace($cle)) { throw 'Aucune clé saisie.' }
    # .env sans BOM : docker compose le lit tel quel
    [System.IO.File]::AppendAllText((Join-Path (Get-Location) '.env'), "INSEE_API_KEY=$($cle.Trim())`n",
        (New-Object System.Text.UTF8Encoding($false)))
    Ok 'Clé enregistrée dans .env'
}

function Charger-Donnees {
    Verifier-Docker
    Cle-Insee
    Titre 'Chargement des vraies données DECP (environ 40 minutes la première fois)'
    Info "Téléchargement du jeu complet, puis rattachement des entreprises via l'API Sirene."
    Compose --profile ingestions run --rm ingestion-decp
    Titre "Nomenclature d'activités et mise à jour des entreprises (environ 10 minutes)"
    Compose --profile ingestions run --rm ingestion-sirene
    Ok "Vraies données chargées. Site : $Site"
}

function Arreter {
    Verifier-Docker
    Titre 'Arrêt (les données sont conservées)'
    Compose --profile stack --profile ingestions stop
    Ok 'Arrêté. Pour relancer : Démarrer le site (1).'
}

function Reinitialiser {
    Verifier-Docker
    Alerte 'Tout sera effacé : base de données et fichiers téléchargés. Les images restent.'
    $confirmation = Read-Host '   Tapez OUI pour confirmer'
    if ($confirmation -cne 'OUI') { Info 'Annulé.'; return }
    Compose --profile stack --profile ingestions down -v
    Ok 'Tout est effacé. Pour repartir : Démarrer le site (1).'
}

function Etat {
    Verifier-Docker
    Compose --profile stack ps
}

function Aide {
    Write-Host ''
    Write-Host ' Suivre Notre Argent - recette locale' -ForegroundColor Cyan
    Write-Host ' ------------------------------------'
    Write-Host ' 1. Démarrer le site (première fois : environ 10 minutes)'
    Write-Host ' 2. Charger le jeu de démonstration (30 secondes)'
    Write-Host ' 3. Charger les vraies données DECP (environ 50 minutes, clé INSEE demandée une fois)'
    Write-Host ' 4. Ouvrir le site dans le navigateur'
    Write-Host ' 5. Voir ce qui tourne'
    Write-Host ' 6. Arrêter (les données sont conservées)'
    Write-Host ' 7. Tout effacer et repartir de zéro'
    Write-Host ' 0. Quitter'
}

function Executer([string]$Choix) {
    switch ($Choix) {
        { $_ -in '1', 'demarrer' } { Demarrer; break }
        { $_ -in '2', 'demo' } { Charger-Demo; break }
        { $_ -in '3', 'donnees' } { Charger-Donnees; break }
        { $_ -in '4', 'ouvrir' } { Start-Process $Site; break }
        { $_ -in '5', 'etat' } { Etat; break }
        { $_ -in '6', 'arreter' } { Arreter; break }
        { $_ -in '7', 'reinitialiser' } { Reinitialiser; break }
        default { Aide }
    }
}

if ($Commande) {
    try { Executer $Commande } catch { Write-Host ''; Write-Host "   Erreur : $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
    exit 0
}

# Sans argument (double-clic) : menu
while ($true) {
    Aide
    $choix = Read-Host ' Votre choix'
    if ($choix -eq '0' -or $choix -eq 'q') { break }
    try { Executer $choix } catch { Write-Host ''; Write-Host "   Erreur : $($_.Exception.Message)" -ForegroundColor Red }
}
