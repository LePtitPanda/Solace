$ErrorActionPreference = 'Stop'
$instance = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$artifactDir = Join-Path $instance 'local/solace-audit-2026-10-13'
New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$outer = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $instance 'mods/alltheleaks-1.1.1+1.20.1-forge.jar'))
try {
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($outer.GetEntry('META-INF/jarjar/mixinextras-forge-0.5.0.jar'), (Join-Path $artifactDir 'mixinextras-forge-0.5.0.jar'), $true)
} finally { $outer.Dispose() }
$inner = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $artifactDir 'mixinextras-forge-0.5.0.jar'))
try {
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($inner.GetEntry('META-INF/jars/MixinExtras-0.5.0.jar'), (Join-Path $artifactDir 'MixinExtras-0.5.0.jar'), $true)
} finally { $inner.Dispose() }
$previousJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
    Push-Location (Join-Path $instance 'Solace')
    try {
        & ./gradlew.bat --offline --init-script tools/audit/audit-jar.gradle jar
        if ($LASTEXITCODE -ne 0) { throw 'Audit build failed' }
        Copy-Item -LiteralPath build/libs/solace-1.0.jar -Destination (Join-Path $artifactDir 'solace-audit.jar') -Force
    } finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJava }
