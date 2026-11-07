# Serveur de test local Rago Practice (Windows) : PandaSpigot 1.8.8 avec RagoCombatEngine.
# Mode d'emploi : LISEZ-MOI.txt. Lancement : double-clic sur lancer-windows.bat.
#
# Réglages facultatifs (variables d'environnement) :
#   RAGO_JAVA          programme java à utiliser (par défaut : %JAVA_HOME%\bin\java.exe, sinon java)
#   RAGO_ACCEPTE_CLUF  "oui" pour accepter le CLUF de Minecraft sans question (lancement automatique)
#   RAGO_MEMOIRE       mémoire maximale du serveur (par défaut 2G)
# Compatible avec Windows PowerShell 5.1 (Windows 10 et 11) et PowerShell 7.
# Ce fichier est en UTF-8 avec BOM : sans BOM, PowerShell 5.1 lirait mal les accents.

$ErrorActionPreference = 'Stop'

# Le build de PandaSpigot sur lequel le test de contrôle du moteur passe.
$PandaSpigotUrl = 'https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip'
$PandaSpigotSha256 = 'ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b'
$ServerJar = 'pandaspigot-226.jar'

Set-Location -LiteralPath $PSScriptRoot

function Say([string]$Text) { Write-Host "[Rago] $Text" }
function Stop-With([string]$Text) {
    Write-Host "[Rago] ERREUR : $Text" -ForegroundColor Red
    exit 1
}

# --- Java ----------------------------------------------------------------------------------------
if ($env:RAGO_JAVA) {
    $Java = $env:RAGO_JAVA
} elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $Java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $Java = 'java'
}
if (-not (Get-Command $Java -ErrorAction SilentlyContinue)) {
    Stop-With ('Java est introuvable. Installe Java 21 (https://adoptium.net/fr/temurin/releases/?version=21) ' +
        'ou Java 8, puis relance lancer-windows.bat.')
}
# "java -version" écrit sur la sortie d'erreur : PowerShell 5.1 en ferait une erreur fatale avec 'Stop'.
$ErrorActionPreference = 'Continue'
$JavaVersion = (& $Java -version 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ -match 'version' } |
    Select-Object -First 1)
$ErrorActionPreference = 'Stop'
if (-not $JavaVersion) { $JavaVersion = 'version inconnue' }
Say "Java : $JavaVersion"
if ($JavaVersion -match 'version "(\d+)(\.(\d+))?') {
    $Major = [int]$Matches[1]
    if ($Major -eq 1 -and $Matches[3]) { $Major = [int]$Matches[3] }
    if ($Major -lt 8) { Stop-With 'il faut Java 8 ou plus récent.' }
}

# --- CLUF de Minecraft ---------------------------------------------------------------------------
$EulaAccepted = (Test-Path -LiteralPath 'eula.txt') -and
    (Select-String -LiteralPath 'eula.txt' -Pattern '^eula=true' -Quiet)
if (-not $EulaAccepted) {
    Say 'Pour lancer un serveur Minecraft, il faut accepter le CLUF de Minecraft :'
    Say 'https://aka.ms/MinecraftEULA'
    $Answer = $env:RAGO_ACCEPTE_CLUF
    if (-not $Answer) { $Answer = Read-Host '[Rago] Tu acceptes le CLUF de Minecraft ? (oui/non)' }
    if ($Answer -notmatch '^\s*(oui|o)\s*$') { Stop-With 'CLUF non accepté : le serveur ne démarre pas.' }
    Set-Content -LiteralPath 'eula.txt' -Value 'eula=true' -Encoding Ascii
}

# --- PandaSpigot ---------------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $ServerJar) -or (Get-Item -LiteralPath $ServerJar).Length -eq 0) {
    Say 'Téléchargement de PandaSpigot 1.8.8 (build 226)...'
    # PowerShell 5.1 n'active pas TLS 1.2 par défaut, et sa barre de progression ralentit beaucoup
    # le téléchargement.
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor
        [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    try {
        Invoke-WebRequest -Uri $PandaSpigotUrl -OutFile "$ServerJar.part" -UseBasicParsing
    } catch {
        Stop-With "le téléchargement de PandaSpigot a échoué ($($_.Exception.Message)). Vérifie ta connexion et relance."
    }
    Move-Item -LiteralPath "$ServerJar.part" -Destination $ServerJar -Force
}
$ActualSha256 = (Get-FileHash -LiteralPath $ServerJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ActualSha256 -ne $PandaSpigotSha256) {
    Remove-Item -LiteralPath $ServerJar -Force
    Stop-With ("le fichier PandaSpigot téléchargé n'est pas le bon (somme $ActualSha256). Il a été supprimé : " +
        'relance lancer-windows.bat pour le télécharger à nouveau.')
}

# --- Démarrage -----------------------------------------------------------------------------------
$Memory = if ($env:RAGO_MEMOIRE) { $env:RAGO_MEMOIRE } else { '2G' }
Say 'Démarrage du serveur. Dans Minecraft 1.8.9 : Multijoueur, Connexion directe, adresse localhost.'
Say "Pour l'arrêter, tape stop ici puis Entrée."
& $Java "-Xmx$Memory" '-DIReallyKnowWhatIAmDoingISwear=true' -jar $ServerJar nogui
exit $LASTEXITCODE
