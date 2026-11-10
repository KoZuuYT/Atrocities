# Publica a versao atual (mod_version do gradle.properties) como Release no GitHub.
# 1. Compila e exporta o jar (build-and-export.ps1)
# 2. Extrai do CHANGELOG.md a secao "## [<versao>]" como notas da Release
# 3. Cria a tag v<versao>, envia ao GitHub e publica a Release com o jar anexado
# Uso: powershell -ExecutionPolicy Bypass -File .\release.ps1

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$props = Get-Content gradle.properties
$modId = ($props | Where-Object { $_ -match '^mod_id=' }) -replace '^mod_id=', ''
$version = ($props | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
$repo = ($props | Where-Object { $_ -match '^github_repo=' }) -replace '^github_repo=', ''
$tag = "v$version"

if (git status --porcelain) { throw 'Existem mudancas nao commitadas. Faca o commit antes de publicar.' }
if (git tag --list $tag) { throw "A tag $tag ja existe. Aumente mod_version no gradle.properties." }

& "$PSScriptRoot\build-and-export.ps1"
$jar = Join-Path $PSScriptRoot "libs\$modId-$version.jar"

# Notas da Release = secao da versao no CHANGELOG.md
$changelog = Get-Content CHANGELOG.md -Encoding UTF8
$start = ($changelog | Select-String -Pattern "^## \[$([regex]::Escape($version))\]").LineNumber
if (-not $start) { throw "CHANGELOG.md nao tem a secao ## [$version]." }
$notes = New-Object System.Collections.Generic.List[string]
for ($i = $start; $i -lt $changelog.Count; $i++) {
    if ($changelog[$i] -match '^## \[' -or $changelog[$i] -match '^\[.+\]: ') { break }
    $notes.Add($changelog[$i])
}
$title = ($changelog[$start - 1] -replace '^## \[[^\]]+\]\s*-\s*[0-9-]+\s*[^\w]*\s*', '').Trim()
$notesFile = Join-Path $env:TEMP "atrocities-release-$version.md"
$notes.Add('')
$notes.Add('---')
$notes.Add("**Instalacao:** coloque ``$modId-$version.jar`` e o [GeckoLib 4.9.3+](https://www.curseforge.com/minecraft/mc-mods/geckolib) na pasta ``mods`` (NeoForge 1.21.1).")
[System.IO.File]::WriteAllLines($notesFile, $notes, (New-Object System.Text.UTF8Encoding $false))

git tag -a $tag -m "Atrocities $version"
git push origin HEAD
git push origin $tag

$releaseTitle = if ($title) { "Atrocities $version - $title" } else { "Atrocities $version" }
gh release create $tag $jar --repo $repo --title $releaseTitle --notes-file $notesFile
if ($LASTEXITCODE -ne 0) { throw "gh release create falhou ($LASTEXITCODE)" }
Write-Host "Release publicada: https://github.com/$repo/releases/tag/$tag"
