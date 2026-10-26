[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$KeyStorePath,
    [Parameter(Mandatory = $true)][string]$KeytoolPath,
    [string]$Alias = 'mimi-reader-release',
    [switch]$NoPause
)

# Run in the owner's visible console. Passwords go directly to keytool's
# non-echoing prompt, never through parameters, environment variables or logs.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$exitStatus = 1
try {
    $Host.UI.RawUI.WindowTitle = 'Mimi Reader - PRIVATE signing key'
    if (-not [IO.Path]::IsPathRooted($KeyStorePath)) { throw 'Use an absolute keystore path.' }
    $keyFile = [IO.Path]::GetFullPath($KeyStorePath)
    if ([IO.Path]::GetExtension($keyFile) -ne '.p12') { throw 'Use a .p12 filename.' }
    $parent = [IO.Path]::GetDirectoryName($keyFile)
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'The destination directory must already exist.' }
    $repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\') + '\'
    if ($keyFile.StartsWith($repository, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Private keys must stay outside this repository, even in ignored directories.'
    }
    if (-not (Test-Path -LiteralPath $KeytoolPath -PathType Leaf)) { throw 'The selected keytool does not exist.' }
    $certificateFile = [IO.Path]::ChangeExtension($keyFile, '.certificate.pem')
    foreach ($destination in @($keyFile, $certificateFile)) {
        if (Test-Path -LiteralPath $destination) { throw "Refusing to replace existing file: $destination" }
    }
    $temporaryKey = Join-Path $parent ('.mimi-key-' + [Guid]::NewGuid().ToString('N') + '.p12')
    $temporaryCertificate = Join-Path $parent ('.mimi-certificate-' + [Guid]::NewGuid().ToString('N') + '.pem')

    Write-Host 'Create a strong, unique password and save it in your password manager.'
    Write-Host 'Do NOT paste the password into chat. Input is hidden in the native prompt.'
    Write-Host 'Enter it twice to create the key, then once more to export its PUBLIC certificate.'
    Write-Host "Private destination: $keyFile"
    Write-Host 'The public certificate identifies the GitHub account jagumiel, not a legal name.'
    & $KeytoolPath -genkeypair -storetype PKCS12 -keystore $temporaryKey -alias $Alias `
        -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10950 -dname 'CN=jagumiel, O=Mimi Reader'
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $temporaryKey -PathType Leaf)) {
        throw "Key generation did not complete. Any generated file remains at $temporaryKey; do not share it."
    }

    # Restrict only this newly generated NTFS file, never the drive/directory.
    $security = New-Object Security.AccessControl.FileSecurity
    $security.SetAccessRuleProtection($true, $false)
    $ownerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = New-Object Security.Principal.SecurityIdentifier('S-1-5-18')
    foreach ($sid in @($ownerSid, $systemSid)) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'Allow')
        $security.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $temporaryKey -AclObject $security
    # File.Move never overwrites an existing destination. Keep any generated key
    # on failure instead of deleting/replacing private material automatically.
    [IO.File]::Move($temporaryKey, $keyFile)
    Write-Host 'Key generated. Re-enter the SAME password to export the PUBLIC certificate.'
    & $KeytoolPath -exportcert -rfc -storetype PKCS12 -keystore $keyFile -alias $Alias -file $temporaryCertificate
    if ($LASTEXITCODE -ne 0) { throw "The private key is saved at $keyFile, but public-certificate export did not finish. Do not regenerate/overwrite the key." }
    [IO.File]::Move($temporaryCertificate, $certificateFile)
    Write-Host "SUCCESS. PRIVATE encrypted keystore: $keyFile"
    Write-Host "PUBLIC certificate: $certificateFile"
    Write-Host "Alias: $Alias"
    Write-Host 'Keep two protected backups in separate locations, including one offline.'
    Write-Host 'Never commit/upload the .p12 file or its password. This does NOT publish or sign an APK.'
    $exitStatus = 0
} catch {
    Write-Host ('STOPPED: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'No existing key was overwritten. Keep any newly generated private file secure.'
} finally {
    if (-not $NoPause) { [void](Read-Host 'Press Enter to close this window') }
}
exit $exitStatus
