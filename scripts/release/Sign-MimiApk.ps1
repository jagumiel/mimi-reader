[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$InputApk,
    [Parameter(Mandatory = $true)][string]$OutputApk,
    [Parameter(Mandatory = $true)][string]$KeyStorePath,
    [Parameter(Mandatory = $true)][string]$BuildToolsDirectory,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ExpectedCertificateSha256,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2147483647)][int]$ExpectedVersionCode,
    [string]$ExpectedApplicationId = 'io.github.jagumiel.mimireader',
    [string]$Alias = 'mimi-reader-release',
    [switch]$NoPause
)

# Run in the owner's visible console. apksigner asks for the password directly;
# never supply a password parameter, environment variable, transcript or log.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$exitStatus = 1
try {
    $Host.UI.RawUI.WindowTitle = 'Mimi Reader - sign release candidate'
    foreach ($path in @($InputApk, $OutputApk, $KeyStorePath, $BuildToolsDirectory)) {
        if (-not [IO.Path]::IsPathRooted($path)) { throw 'All signing paths must be absolute.' }
    }
    $source = [IO.Path]::GetFullPath($InputApk)
    $destination = [IO.Path]::GetFullPath($OutputApk)
    if ([IO.Path]::GetExtension($source) -ne '.apk' -or [IO.Path]::GetExtension($destination) -ne '.apk') {
        throw 'Input and output must be APK files.'
    }
    if ($source -eq $destination -or (Test-Path -LiteralPath $destination) -or
            (Test-Path -LiteralPath ($destination + '.sha256'))) { throw 'Refusing to overwrite an APK/checksum.' }
    foreach ($path in @($source, $KeyStorePath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing input: $path" }
    }
    $parent = [IO.Path]::GetDirectoryName($destination)
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'Create the output directory first.' }
    $signer = Join-Path $BuildToolsDirectory 'apksigner.bat'
    $aapt = Join-Path $BuildToolsDirectory 'aapt.exe'
    $zipalign = Join-Path $BuildToolsDirectory 'zipalign.exe'
    foreach ($tool in @($signer, $aapt, $zipalign)) {
        if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Missing SDK tool: $tool" }
    }
    # Native Windows aapt rejects accented absolute paths; a relative leaf works.
    Push-Location -LiteralPath ([IO.Path]::GetDirectoryName($source))
    try {
        $badging = (& $aapt dump badging ([IO.Path]::GetFileName($source))) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw 'APK metadata validation failed.' }
    } finally { Pop-Location }
    if ($badging -notmatch ("package: name='" + [regex]::Escape($ExpectedApplicationId) + "' versionCode='" + $ExpectedVersionCode + "'")) {
        throw 'The APK application ID/version does not match the approved candidate.'
    }
    if ($badging -match 'application-debuggable') { throw 'A debug APK is not a release candidate.' }
    & $zipalign -c -P 16 4 $source
    if ($LASTEXITCODE -ne 0) { throw 'Unsigned APK alignment failed; do not sign it.' }
    $temporary = Join-Path $parent ('.mimi-signed-' + [Guid]::NewGuid().ToString('N') + '.apk')
    Write-Host 'Enter your keystore password only in the native prompt below, never in chat.'
    & $signer sign --ks $KeyStorePath --ks-key-alias $Alias --v4-signing-enabled false --out $temporary $source
    if ($LASTEXITCODE -ne 0) { throw "Signing did not complete; any generated APK remains at $temporary." }
    $verification = (& $signer verify --verbose --print-certs $temporary) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed. The candidate is not finalized.' }
    $certificates = [regex]::Matches($verification, 'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})')
    if ($certificates.Count -ne 1 -or $certificates[0].Groups[1].Value -ne $ExpectedCertificateSha256) {
        throw 'The signing certificate does not match the approved fingerprint.'
    }
    foreach ($api in @(29, 31)) {
        & $signer verify --min-sdk-version $api --max-sdk-version $api $temporary
        if ($LASTEXITCODE -ne 0) { throw "Signature verification failed for API $api." }
    }
    & $zipalign -c -P 16 4 $temporary
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment failed.' }
    [IO.File]::Move($temporary, $destination)
    $digest = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText($destination + '.sha256', $digest + '  ' + [IO.Path]::GetFileName($destination) + "`n", [Text.Encoding]::ASCII)
    Write-Host $verification
    Write-Host "SUCCESS. Signed candidate: $destination"
    Write-Host "SHA-256: $digest"
    Write-Host 'This does NOT install, upload, tag or publish the candidate.'
    $exitStatus = 0
} catch {
    Write-Host ('STOPPED: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'No existing APK was overwritten. Keep the unsigned build/mapping/SBOM.'
} finally {
    if (-not $NoPause) { [void](Read-Host 'Press Enter to close this window') }
}
exit $exitStatus
