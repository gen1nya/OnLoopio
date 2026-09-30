param([Parameter(Mandatory=$true)][string]$Drive,[string]$CaCertificate)
$ErrorActionPreference='Stop'

function Read-Secret([string]$Prompt) {
    $secure=Read-Host $Prompt -AsSecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
}

$root=[IO.Path]::GetPathRoot([IO.Path]::GetFullPath($Drive))
if(-not $root -or -not (Test-Path -LiteralPath $root -PathType Container)) { throw 'Choose the mounted Y1 USB drive.' }
if($root.TrimEnd('\') -ieq $env:SystemDrive.TrimEnd('\')) { throw 'The Windows system drive cannot be used.' }
$driveInfo=[IO.DriveInfo]::new($root)
if(-not $driveInfo.IsReady) { throw 'The selected drive is not ready.' }
$target=Join-Path $root 'OnLoopio-setup.json'
if(Test-Path -LiteralPath $target) { throw 'A setup file already exists on this drive. Remove it after checking the previous attempt.' }
Write-Host "Y1 USB drive: $root"
$choice=Read-Host 'Type YES to confirm this is your Y1 storage'
if($choice -cne 'YES') { throw 'Setup cancelled.' }

$ssid=Read-Host 'Wi-Fi name (SSID)'
$wifiPassword=Read-Secret 'Wi-Fi password (leave blank for an open network)'
$url=Read-Host 'Navidrome URL, including https://'
$username=Read-Host 'Navidrome username'
$serverPassword=Read-Secret 'Navidrome password'
$uri=$null
if(-not [uri]::TryCreate($url,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) { throw 'Use a plain http(s) server URL without credentials or query parameters.' }
if([Text.Encoding]::UTF8.GetByteCount($ssid) -lt 1 -or [Text.Encoding]::UTF8.GetByteCount($ssid) -gt 32 -or $ssid -match '[\x00-\x1F]') { throw 'Wi-Fi name must be 1–32 UTF-8 bytes without control characters.' }
if($wifiPassword.Length -ne 0 -and ($wifiPassword.Length -lt 8 -or $wifiPassword.Length -gt 63)) { throw 'Wi-Fi password must be 8–63 characters or empty.' }
if([string]::IsNullOrWhiteSpace($username) -or [string]::IsNullOrEmpty($serverPassword)) { throw 'Navidrome username and password are required.' }
$ca=''
if($CaCertificate) { $ca=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $CaCertificate).Path) }
if($ca.Length -gt 16384) { throw 'CA certificate exceeds the supported size.' }
$payload=@{version=1;wifi=@{ssid=$ssid;password=$wifiPassword};server=@{url=$url;username=$username;password=$serverPassword;trustedCa=$ca}} | ConvertTo-Json -Depth 5 -Compress
if([Text.Encoding]::UTF8.GetByteCount($payload) -gt 32768) { throw 'Setup file exceeds the supported size.' }
[IO.File]::WriteAllText($target,$payload,[Text.UTF8Encoding]::new($false))
$payload=$null;$wifiPassword=$null;$serverPassword=$null
Write-Host 'Setup file written. Safely eject the Y1 USB drive, disconnect the cable, and wait for OnLoopio to import it.'
Write-Host 'The player removes the setup file after reading it. If setup fails, reconnect USB and run this tool again.'
