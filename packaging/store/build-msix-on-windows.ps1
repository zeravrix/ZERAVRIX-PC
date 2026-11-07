[CmdletBinding()]
param(
    [string]$PackageName = 'U1M.Zeravrix',
    [string]$Publisher = 'CN=D7CFED99-F946-4B71-8F83-D7F3A1DD152D',
    [string]$Version = '1.4.1.0',
    [string]$MakeAppx = ''
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Stage = Join-Path $Root '..\deliverables\zeravrix-store-1.4.1\msix-staging'
$Stage = [IO.Path]::GetFullPath($Stage)
$Manifest = Join-Path $Stage 'AppxManifest.xml'
$OutputDirectory = Split-Path $Stage -Parent
$Output = Join-Path $OutputDirectory 'ZERAVRIX-Store-1.4.1-x64.msix'
$Upload = Join-Path $OutputDirectory 'ZERAVRIX-Store-1.4.1-x64.msixupload'

if (-not (Test-Path $Manifest)) { throw "Missing staging manifest: $Manifest" }
if (-not (Test-Path (Join-Path $Stage 'ZERAVRIX.exe'))) { throw "Missing native launcher in staging tree" }

if ([string]::IsNullOrWhiteSpace($MakeAppx)) {
    $candidate = Get-ChildItem "$env:ProgramFiles(x86)\Windows Kits\10\bin" -Filter makeappx.exe -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\x64\\makeappx\.exe$' } |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($null -eq $candidate) { throw 'makeappx.exe was not found. Install the Windows 10/11 SDK and rerun.' }
    $MakeAppx = $candidate.FullName
}

[xml]$xml = Get-Content -Raw $Manifest
$identity = $xml.Package.Identity
$identity.Name = $PackageName
$identity.Publisher = $Publisher
$identity.Version = $Version
$xml.Save($Manifest)

Remove-Item (Join-Path $Stage 'AppxBlockMap.xml') -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $Stage 'AppxSignature.p7x') -Force -ErrorAction SilentlyContinue
Remove-Item $Output -Force -ErrorAction SilentlyContinue
Remove-Item $Upload -Force -ErrorAction SilentlyContinue

& $MakeAppx pack /d $Stage /p $Output /o
if ($LASTEXITCODE -ne 0) { throw "makeappx failed with exit code $LASTEXITCODE" }

Compress-Archive -Path $Output -DestinationPath $Upload -CompressionLevel Optimal
$hash = (Get-FileHash $Output -Algorithm SHA256).Hash.ToLowerInvariant()
$uploadHash = (Get-FileHash $Upload -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $(Split-Path $Output -Leaf)" | Set-Content -Encoding ASCII "$Output.sha256"
"$uploadHash  $(Split-Path $Upload -Leaf)" | Set-Content -Encoding ASCII "$Upload.sha256"
Write-Host "Created: $Output"
Write-Host "Created: $Upload"
Write-Host "MSIX SHA-256: $hash"
Write-Host "MSIXUPLOAD SHA-256: $uploadHash"
Write-Host 'Upload the .msixupload file to Partner Center; Microsoft handles Store signing after certification.'
Write-Host 'For local sideload testing, sign a copy with a test certificate and install the certificate first.'
