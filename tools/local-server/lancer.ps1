# Local test server of Rago Practice (Windows): PandaSpigot 1.8.8 with RagoCombatEngine, WorldEdit,
# PacketEvents and the Rago plugins. How to use it: LISEZ-MOI.txt. Start: double-click lancer-windows.bat.
#
# Optional settings (environment variables):
#   RAGO_JAVA          the java program to use (default: %JAVA_HOME%\bin\java.exe, else java)
#   RAGO_ACCEPTE_CLUF  "yes" (or "oui") to accept the Minecraft EULA without a question (automatic start)
#   RAGO_MEMOIRE       the server's maximum memory (default 2G)
#   RAGO_DONNEES       the folder of the worlds, the schematics and the backups (default: %USERPROFILE%\Rago)
#   RAGO_SAUVEGARDES   how many backups of the worlds are kept (default 10; 0: no backup)
# Works with Windows PowerShell 5.1 (Windows 10 and 11) and PowerShell 7.
# This file is UTF-8 with a BOM: without it, PowerShell 5.1 would misread any non-ASCII character.

$ErrorActionPreference = 'Stop'

# The PandaSpigot build the engine control test passes on.
$PandaSpigotUrl = 'https://downloads.hpfxd.com/v2/projects/pandaspigot/versions/1.8.8/builds/226/downloads/paperclip'
$PandaSpigotSha256 = 'ac73146b275230cbd0612ef0f3d6b40333abd3182beae457cad5321efff6359b'
$ServerJar = 'pandaspigot-226.jar'
# WorldEdit 6.1, the last version for Bukkit 1.8 (https://dev.bukkit.org/projects/worldedit/files/880435).
$WorldEditUrl = 'https://mediafilez.forgecdn.net/files/880/435/worldedit-bukkit-6.1.jar'
$WorldEditSha256 = 'b9d3f0648e47424d66d94081b21a26504de0fbe6843f3d977c023aec066d4496'
$WorldEditJar = 'plugins\worldedit-bukkit-6.1.jar'
# PacketEvents 2.14.0, which RagoLobby needs for its NPCs (https://github.com/retrooper/packetevents).
$PacketEventsUrl = 'https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar'
$PacketEventsSha256 = '060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7'
$PacketEventsJar = 'plugins\packetevents-spigot-2.14.0.jar'

Set-Location -LiteralPath $PSScriptRoot

function Say([string]$Text) { Write-Host "[Rago] $Text" }
function Stop-With([string]$Text) {
    Write-Host "[Rago] ERROR: $Text" -ForegroundColor Red
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
    Stop-With ('Java was not found. Install Java 21 (https://adoptium.net/temurin/releases/?version=21) ' +
        'or Java 8, then run lancer-windows.bat again.')
}
# "java -version" writes to the error output: PowerShell 5.1 would make it a fatal error with 'Stop'.
$ErrorActionPreference = 'Continue'
$JavaVersion = (& $Java -version 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ -match 'version' } |
    Select-Object -First 1)
$ErrorActionPreference = 'Stop'
if (-not $JavaVersion) { $JavaVersion = 'unknown version' }
Say "Java: $JavaVersion"
if ($JavaVersion -match 'version "(\d+)(\.(\d+))?') {
    $Major = [int]$Matches[1]
    if ($Major -eq 1 -and $Matches[3]) { $Major = [int]$Matches[3] }
    if ($Major -lt 8) { Stop-With 'Java 8 or newer is needed.' }
}

# --- Minecraft EULA ------------------------------------------------------------------------------
$EulaAccepted = (Test-Path -LiteralPath 'eula.txt') -and
    (Select-String -LiteralPath 'eula.txt' -Pattern '^eula=true' -Quiet)
if (-not $EulaAccepted) {
    Say 'To run a Minecraft server, you must accept the Minecraft EULA:'
    Say 'https://aka.ms/MinecraftEULA'
    $Answer = $env:RAGO_ACCEPTE_CLUF
    if (-not $Answer) { $Answer = Read-Host '[Rago] Do you accept the Minecraft EULA? (yes/no)' }
    if ($Answer -notmatch '^\s*(yes|y|oui|o)\s*$') { Stop-With 'EULA not accepted: the server does not start.' }
    Set-Content -LiteralPath 'eula.txt' -Value 'eula=true' -Encoding Ascii
}

# --- Downloads -----------------------------------------------------------------------------------
# PowerShell 5.1 does not turn TLS 1.2 on by default, and its progress bar slows the downloads
# down a lot.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor
    [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'

# Downloads the file if it is missing, then checks its sum. A file without the right sum is
# deleted.
function Get-Verified([string]$Name, [string]$Url, [string]$Sha256, [string]$File) {
    $Path = Join-Path $PSScriptRoot $File
    if (-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).Length -eq 0) {
        Say "Downloading $Name..."
        try {
            Invoke-WebRequest -Uri $Url -OutFile "$Path.part" -UseBasicParsing
        } catch {
            Stop-With "the download of $Name failed ($($_.Exception.Message)). Check your connection and run again."
        }
        Move-Item -LiteralPath "$Path.part" -Destination $Path -Force
    }
    # The sum is computed with .NET and not with Get-FileHash: started from PowerShell 7, Windows
    # PowerShell 5.1 inherits its module paths and no longer finds Get-FileHash.
    $Stream = [IO.File]::OpenRead($Path)
    try {
        $HashBytes = [Security.Cryptography.SHA256]::Create().ComputeHash($Stream)
    } finally {
        $Stream.Dispose()
    }
    $ActualSha256 = ([BitConverter]::ToString($HashBytes) -replace '-', '').ToLowerInvariant()
    if ($ActualSha256 -ne $Sha256) {
        Remove-Item -LiteralPath $Path -Force
        Stop-With ("the downloaded file $Name is not the right one (sum $ActualSha256). It was deleted: " +
            'run lancer-windows.bat again to download it again.')
    }
}
[void][IO.Directory]::CreateDirectory((Join-Path $PSScriptRoot 'plugins'))
Get-Verified 'PandaSpigot 1.8.8 (build 226)' $PandaSpigotUrl $PandaSpigotSha256 $ServerJar
Get-Verified 'WorldEdit 6.1' $WorldEditUrl $WorldEditSha256 $WorldEditJar
Get-Verified 'PacketEvents 2.14.0' $PacketEventsUrl $PacketEventsSha256 $PacketEventsJar

# --- Worlds, schematics and backups --------------------------------------------------------------
# They are kept outside this folder: a new version of the test server finds them again.
$DataDir = if ($env:RAGO_DONNEES) { $env:RAGO_DONNEES } else { Join-Path $env:USERPROFILE 'Rago' }
$DataDir = [IO.Path]::GetFullPath($DataDir)
$Worlds = Join-Path $DataDir 'mondes'
$Schematics = Join-Path $DataDir 'schematics'
$Backups = Join-Path $DataDir 'sauvegardes'
try {
    foreach ($Dir in $Worlds, $Schematics, $Backups) { [void][IO.Directory]::CreateDirectory($Dir) }
} catch {
    Stop-With "cannot create the folder $DataDir ($($_.Exception.Message))."
}
Say "Worlds and schematics: $DataDir"

$KeepText = if ($null -ne $env:RAGO_SAUVEGARDES -and $env:RAGO_SAUVEGARDES -ne '') { $env:RAGO_SAUVEGARDES } else { '10' }
if ($KeepText -notmatch '^\d+$') {
    Stop-With "RAGO_SAUVEGARDES must be a number (for example 10), not `"$KeepText`"."
}
$Keep = [int]$KeepText
$ToSave = @()
foreach ($Root in $Worlds, $Schematics) {
    $ToSave += [IO.Directory]::GetFiles($Root, '*', [IO.SearchOption]::AllDirectories)
}
if ($Keep -gt 0 -and $ToSave.Count -gt 0) {
    $Archive = Join-Path $Backups ('mondes-' + [DateTime]::Now.ToString('yyyy-MM-dd_HH-mm-ss') + '.zip')
    Say "Backup of the worlds and the schematics: $Archive"
    # A .zip archive made with .NET, and not Compress-Archive: the same reason as for Get-FileHash.
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
        Stop-With ("the backup of the worlds failed ($($_.Exception.Message)). The server does not start, " +
            'so as not to damage anything. Free some space, or run with RAGO_SAUVEGARDES=0 to start without a backup.')
    }
    # Keeps the $Keep most recent: the names are dated, the alphabetical order is the order of the dates.
    $Old = @([IO.Directory]::GetFiles($Backups, 'mondes-*.zip') | Sort-Object -Descending | Select-Object -Skip $Keep)
    foreach ($File in $Old) { Remove-Item -LiteralPath $File -Force }
}

# WorldEdit keeps its schematics with the worlds. This file is only written if it does not exist:
# the other settings of WorldEdit keep their default value.
$WorldEditConfig = Join-Path $PSScriptRoot 'plugins\WorldEdit\config.yml'
if (-not (Test-Path -LiteralPath $WorldEditConfig)) {
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $WorldEditConfig))
    $YamlPath = $Schematics.Replace('\', '/').Replace("'", "''")
    $Lines = @(
        "# Written by the Rago launcher: the schematics are kept with the worlds ($DataDir).",
        'saving:',
        "  dir: '$YamlPath'"
    )
    # UTF-8 without a BOM: Set-Content -Encoding UTF8 of PowerShell 5.1 would add one.
    [IO.File]::WriteAllLines($WorldEditConfig, [string[]]$Lines, (New-Object Text.UTF8Encoding $false))
}

# --- Start ---------------------------------------------------------------------------------------
$Memory = if ($env:RAGO_MEMOIRE) { $env:RAGO_MEMOIRE } else { '2G' }
Say 'Starting the server. In Minecraft 1.8.9: Multiplayer, Direct Connection, address localhost.'
Say "To stop it, type stop here, then Enter."
& $Java "-Xmx$Memory" '-DIReallyKnowWhatIAmDoingISwear=true' -jar $ServerJar --world-container $Worlds nogui
exit $LASTEXITCODE
