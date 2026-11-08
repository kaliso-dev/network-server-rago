# Serveur de test local Rago Practice (Windows) : PandaSpigot 1.8.8 avec RagoCombatEngine, WorldEdit,
# PacketEvents et les plugins Rago. Mode d'emploi : LISEZ-MOI.txt. Lancement : double-clic sur lancer-windows.bat.
#
# Réglages facultatifs (variables d'environnement) :
#   RAGO_JAVA          programme java à utiliser (par défaut : %JAVA_HOME%\bin\java.exe, sinon java)
#   RAGO_ACCEPTE_CLUF  "oui" pour accepter le CLUF de Minecraft sans question (lancement automatique)
#   RAGO_MEMOIRE       mémoire maximale du serveur (par défaut 2G)
#   RAGO_DONNEES       dossier des mondes, des schematics et des sauvegardes (par défaut : %USERPROFILE%\Rago)
#   RAGO_SAUVEGARDES   nombre de sauvegardes des mondes gardées (par défaut 10 ; 0 : pas de sauvegarde)
# Compatible avec Windows PowerShell 5.1 (Windows 10 et 11) et PowerShell 7.
# Ce fichier est en UTF-8 avec BOM : sans BOM, PowerShell 5.1 lirait mal les accents.

$ErrorActionPreference = 'Stop'

# Le build de PandaSpigot sur lequel le test de contrôle du moteur passe.
$PandaSpigotUrl = 'https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip'
$PandaSpigotSha256 = 'ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b'
$ServerJar = 'pandaspigot-226.jar'
# WorldEdit 6.1, la dernière version pour Bukkit 1.8 (https://dev.bukkit.org/projects/worldedit/files/880435).
$WorldEditUrl = 'https://mediafilez.forgecdn.net/files/880/435/worldedit-bukkit-6.1.jar'
$WorldEditSha256 = 'b9d3f0648e47424d66d94081b21a26504de0fbe6843f3d977c023aec066d4496'
$WorldEditJar = 'plugins\worldedit-bukkit-6.1.jar'
# PacketEvents 2.14.0, dont RagoLobby a besoin pour ses PNJ (https://github.com/retrooper/packetevents).
$PacketEventsUrl = 'https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar'
$PacketEventsSha256 = '060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7'
$PacketEventsJar = 'plugins\packetevents-spigot-2.14.0.jar'

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

# --- Téléchargements ------------------------------------------------------------------------------
# PowerShell 5.1 n'active pas TLS 1.2 par défaut, et sa barre de progression ralentit beaucoup les
# téléchargements.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor
    [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'

# Télécharge le fichier s'il manque, puis vérifie sa somme. Un fichier qui n'a pas la bonne somme
# est supprimé.
function Get-Verified([string]$Name, [string]$Url, [string]$Sha256, [string]$File) {
    $Path = Join-Path $PSScriptRoot $File
    if (-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).Length -eq 0) {
        Say "Téléchargement de $Name..."
        try {
            Invoke-WebRequest -Uri $Url -OutFile "$Path.part" -UseBasicParsing
        } catch {
            Stop-With "le téléchargement de $Name a échoué ($($_.Exception.Message)). Vérifie ta connexion et relance."
        }
        Move-Item -LiteralPath "$Path.part" -Destination $Path -Force
    }
    # La somme est calculée avec .NET et non avec Get-FileHash : lancé depuis PowerShell 7, Windows
    # PowerShell 5.1 hérite de ses chemins de modules et ne trouve plus Get-FileHash.
    $Stream = [IO.File]::OpenRead($Path)
    try {
        $HashBytes = [Security.Cryptography.SHA256]::Create().ComputeHash($Stream)
    } finally {
        $Stream.Dispose()
    }
    $ActualSha256 = ([BitConverter]::ToString($HashBytes) -replace '-', '').ToLowerInvariant()
    if ($ActualSha256 -ne $Sha256) {
        Remove-Item -LiteralPath $Path -Force
        Stop-With ("le fichier $Name téléchargé n'est pas le bon (somme $ActualSha256). Il a été supprimé : " +
            'relance lancer-windows.bat pour le télécharger à nouveau.')
    }
}
[void][IO.Directory]::CreateDirectory((Join-Path $PSScriptRoot 'plugins'))
Get-Verified 'PandaSpigot 1.8.8 (build 226)' $PandaSpigotUrl $PandaSpigotSha256 $ServerJar
Get-Verified 'WorldEdit 6.1' $WorldEditUrl $WorldEditSha256 $WorldEditJar
Get-Verified 'PacketEvents 2.14.0' $PacketEventsUrl $PacketEventsSha256 $PacketEventsJar

# --- Mondes, schematics et sauvegardes -------------------------------------------------------------
# Ils sont rangés hors de ce dossier : une nouvelle version du serveur de test les retrouve.
$DataDir = if ($env:RAGO_DONNEES) { $env:RAGO_DONNEES } else { Join-Path $env:USERPROFILE 'Rago' }
$DataDir = [IO.Path]::GetFullPath($DataDir)
$Worlds = Join-Path $DataDir 'mondes'
$Schematics = Join-Path $DataDir 'schematics'
$Backups = Join-Path $DataDir 'sauvegardes'
try {
    foreach ($Dir in $Worlds, $Schematics, $Backups) { [void][IO.Directory]::CreateDirectory($Dir) }
} catch {
    Stop-With "impossible de créer le dossier $DataDir ($($_.Exception.Message))."
}
Say "Mondes et schematics : $DataDir"

$KeepText = if ($null -ne $env:RAGO_SAUVEGARDES -and $env:RAGO_SAUVEGARDES -ne '') { $env:RAGO_SAUVEGARDES } else { '10' }
if ($KeepText -notmatch '^\d+$') {
    Stop-With "RAGO_SAUVEGARDES doit être un nombre (par exemple 10), pas « $KeepText »."
}
$Keep = [int]$KeepText
$ToSave = @()
foreach ($Root in $Worlds, $Schematics) {
    $ToSave += [IO.Directory]::GetFiles($Root, '*', [IO.SearchOption]::AllDirectories)
}
if ($Keep -gt 0 -and $ToSave.Count -gt 0) {
    $Archive = Join-Path $Backups ('mondes-' + [DateTime]::Now.ToString('yyyy-MM-dd_HH-mm-ss') + '.zip')
    Say "Sauvegarde des mondes et des schematics : $Archive"
    # Une archive .zip faite avec .NET, et non Compress-Archive : même raison que pour Get-FileHash.
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    try {
        $Zip = [IO.Compression.ZipFile]::Open("$Archive.part", [IO.Compression.ZipArchiveMode]::Create)
        try {
            foreach ($File in $ToSave) {
                $Entry = $File.Substring($DataDir.TrimEnd('\').Length + 1).Replace('\', '/')
                [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Zip, $File, $Entry,
                    [IO.Compression.CompressionLevel]::Optimal)
            }
        } finally {
            $Zip.Dispose()
        }
        Move-Item -LiteralPath "$Archive.part" -Destination $Archive -Force
    } catch {
        Remove-Item -LiteralPath "$Archive.part" -Force -ErrorAction SilentlyContinue
        Stop-With ("la sauvegarde des mondes a échoué ($($_.Exception.Message)). Le serveur ne démarre pas, " +
            'pour ne rien abîmer. Libère de la place, ou lance avec RAGO_SAUVEGARDES=0 pour démarrer sans sauvegarde.')
    }
    # Garde les $Keep plus récentes : les noms sont datés, l'ordre alphabétique est l'ordre des dates.
    $Old = @([IO.Directory]::GetFiles($Backups, 'mondes-*.zip') | Sort-Object -Descending | Select-Object -Skip $Keep)
    foreach ($File in $Old) { Remove-Item -LiteralPath $File -Force }
}

# WorldEdit range ses schematics avec les mondes. Ce fichier n'est écrit que s'il n'existe pas : les
# autres réglages de WorldEdit gardent leur valeur par défaut.
$WorldEditConfig = Join-Path $PSScriptRoot 'plugins\WorldEdit\config.yml'
if (-not (Test-Path -LiteralPath $WorldEditConfig)) {
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $WorldEditConfig))
    $YamlPath = $Schematics.Replace('\', '/').Replace("'", "''")
    $Lines = @(
        "# Écrit par le lanceur Rago : les schematics sont rangés avec les mondes ($DataDir).",
        'saving:',
        "  dir: '$YamlPath'"
    )
    # UTF-8 sans BOM : Set-Content -Encoding UTF8 de PowerShell 5.1 ajouterait un BOM.
    [IO.File]::WriteAllLines($WorldEditConfig, [string[]]$Lines, (New-Object Text.UTF8Encoding $false))
}

# --- Démarrage -----------------------------------------------------------------------------------
$Memory = if ($env:RAGO_MEMOIRE) { $env:RAGO_MEMOIRE } else { '2G' }
Say 'Démarrage du serveur. Dans Minecraft 1.8.9 : Multijoueur, Connexion directe, adresse localhost.'
Say "Pour l'arrêter, tape stop ici puis Entrée."
& $Java "-Xmx$Memory" '-DIReallyKnowWhatIAmDoingISwear=true' -jar $ServerJar --world-container $Worlds nogui
exit $LASTEXITCODE
