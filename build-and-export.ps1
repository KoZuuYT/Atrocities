# Compila o Atrocities e exporta o .jar para a pasta libs\ do projeto.
# Uso: powershell -ExecutionPolicy Bypass -File .\build-and-export.ps1

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$exportDir = Join-Path $PSScriptRoot 'libs'
$tmpDir = 'D:\gradle-tmp'

# JDK 21: procura nas instalacoes mais comuns (Microsoft, Temurin, Oracle).
$jdkRoots = @('C:\Program Files\Microsoft', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java')
$jdk = $jdkRoots | Where-Object { Test-Path $_ } |
    ForEach-Object { Get-ChildItem $_ -Directory | Where-Object Name -like 'jdk-21*' } |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $jdk) { throw 'JDK 21 nao encontrado.' }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

# A pasta do usuario tem acento ("Usuario"), o que quebra os sockets locais do Java no Windows.
# Usamos uma pasta temporaria so com caracteres ASCII.
New-Item -ItemType Directory -Force $tmpDir | Out-Null
$env:TMP = $tmpDir
$env:TEMP = $tmpDir
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$tmpDir -Djava.io.tmpdir=$tmpDir"

$props = Get-Content gradle.properties
$modId = ($props | Where-Object { $_ -match '^mod_id=' }) -replace '^mod_id=', ''
$version = ($props | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''

& .\gradlew.bat build --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Build falhou (codigo $LASTEXITCODE)" }

$jar = "build\libs\$modId-$version.jar"
if (-not (Test-Path $jar)) { throw "Jar nao encontrado: $jar" }

New-Item -ItemType Directory -Force $exportDir | Out-Null
Copy-Item $jar $exportDir -Force
Write-Host "Exportado: $exportDir\$modId-$version.jar"
