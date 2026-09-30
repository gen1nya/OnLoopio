param(
    [string]$Updater=(Join-Path $env:LOCALAPPDATA 'Innioasis Updater'),
    [string]$BackupRoot=(Join-Path $env:LOCALAPPDATA 'OnLoopio\secrets'),
    [string]$RestoreBackup,
    [switch]$ValidateOnly
)
$ErrorActionPreference='Stop'
$package=(Resolve-Path -LiteralPath $PSScriptRoot).Path
$manifest=Get-Content -LiteralPath (Join-Path $package 'manifest.json') -Raw | ConvertFrom-Json
$scatter=Join-Path $package 'MT6572_Android_scatter.txt'
$schemaPath=Join-Path $Updater 'console_mode.xsd'
$flashExe=Join-Path $Updater 'flash_tool.exe'
$daemon=Join-Path $Updater 'MTK_AllInOne_DA.bin'
foreach($path in @($schemaPath,$flashExe,$daemon,$scatter)) { if(-not (Test-Path -LiteralPath $path -PathType Leaf)){throw "Required installer file is missing: $path"} }
if($manifest.model -ne 'Innioasis Y1 verified Type A profile' -or $manifest.personalized -ne $false -or $manifest.private_seed_embedded -ne $false) { throw 'This is not a generic Y1 Type A package.' }
if(Get-Process -Name flash_tool -ErrorAction SilentlyContinue) { throw 'Close the other SP Flash Tool process first.' }

function Assert-Scatter([string]$source,[string]$cached) {
    $left=[IO.File]::ReadAllText($source);$right=[IO.File]::ReadAllText($cached)
    foreach($field in @('partition_name','linear_start_addr','partition_size','region')) {
        $pattern=$field+':\s*(\S+)'
        $a=@([regex]::Matches($left,$pattern) | ForEach-Object {$_.Groups[1].Value})
        $b=@([regex]::Matches($right,$pattern) | ForEach-Object {$_.Groups[1].Value})
        if(($a -join '|') -ne ($b -join '|')){throw "Firmware scatter differs from installed Updater: $field"}
    }
    foreach($expected in @(@('BOOTIMG','0x3180000','0x600000'),@('ANDROID','0x4b40000','0x28a00000'))) {
        $part=[regex]::Match($left,'(?ms)^- partition_index:.*?partition_name:\s*'+$expected[0]+'\s.*?(?=^- partition_index:|\z)').Value
        if(-not $part -or $part -notmatch ('linear_start_addr:\s*'+$expected[1]+'\b') -or $part -notmatch ('partition_size:\s*'+$expected[2]+'\b')){throw "Unexpected $($expected[0]) scatter layout"}
    }
}
Assert-Scatter $scatter (Join-Path $Updater 'MT6572_Android_scatter.txt')

function Assert-Images([string]$boot,[string]$system,[string]$bootHash,[string]$systemHash) {
    if((Get-Item -LiteralPath $boot).Length -ne 0x600000 -or (Get-Item -LiteralPath $system).Length -ne 0x28a00000){throw 'Image sizes do not match Y1 partitions.'}
    if((Get-FileHash -LiteralPath $boot -Algorithm SHA256).Hash -ne $bootHash -or (Get-FileHash -LiteralPath $system -Algorithm SHA256).Hash -ne $systemHash){throw 'Image checksum mismatch.'}
    $header=[byte[]]::new(8);$stream=[IO.File]::OpenRead($boot);try{if($stream.Read($header,0,8) -ne 8 -or [Text.Encoding]::ASCII.GetString($header) -ne 'ANDROID!'){throw 'Invalid boot header.'}}finally{$stream.Dispose()}
    $magic=[byte[]]::new(2);$stream=[IO.File]::OpenRead($system);try{$stream.Position=1080;if($stream.Read($magic,0,2) -ne 2 -or $magic[0] -ne 0x53 -or $magic[1] -ne 0xEF){throw 'Invalid ext4 system image.'}}finally{$stream.Dispose()}
}

function New-Config([string]$directory,[string]$command,[object[]]$ranges,[string]$scatterPath) {
    $doc=[xml]'<flashtool-config version="2.0"><general><chip-name>MT6572</chip-name><storage-type>EMMC</storage-type><download-agent/><scatter/><authentication/><certification/><rom-list/><connection type="BromUSB" high-speed="true" power="AutoDetect" timeout-count="3600000" com-port=""/><checksum-level>both</checksum-level><log-info log_on="true" log_path="" clean_hours="720"/></general><commands/></flashtool-config>'
    $doc.'flashtool-config'.general.'download-agent'=$daemon
    $doc.'flashtool-config'.general.scatter=$scatterPath
    $doc.'flashtool-config'.general.'log-info'.SetAttribute('log_path',$directory)
    if($command -eq 'readback') {
        $node=$doc.CreateElement('readback');$physical=$doc.CreateElement('physical-readback');$physical.SetAttribute('is-physical-readback','true');[void]$node.AppendChild($physical);$list=$doc.CreateElement('readback-list');[void]$node.AppendChild($list);[void]$doc.SelectSingleNode('/flashtool-config/commands').AppendChild($node)
        $index=0
        foreach($range in $ranges) {
            $item=$doc.CreateElement('readback-rom-item')
            foreach($pair in @(@('start-address',('0x{0:X}' -f $range.offset)),@('readback-length',('0x{0:X}' -f $range.length)),@('readback-index',([string]$index)),@('readback-enable','true'),@('readback-flag','NULL_READ_PAGE_SPARE'),@('addr-flag','NUTL_ADDR_PHYSICAL'),@('part-id','8'))) { $item.SetAttribute($pair[0],$pair[1]) }
            $item.InnerText=$range.file;[void]$list.AppendChild($item);$index++
        }
    } else {
        $node=$doc.CreateElement('download-only');[void]$node.AppendChild($doc.CreateElement('da-download-all'));[void]$doc.SelectSingleNode('/flashtool-config/commands').AppendChild($node)
        $source=[IO.File]::ReadAllText($scatterPath)
        $sections=[regex]::Split($source,'(?m)(?=^- partition_index:)')
        $enabled=0
        for($i=1;$i -lt $sections.Count;$i++) {
            $part=$sections[$i];$name=[regex]::Match($part,'partition_name:\s*(\S+)').Groups[1].Value
            $target=if($name -eq 'BOOTIMG'){$ranges[0].file}elseif($name -eq 'ANDROID'){$ranges[1].file}else{$null}
            $part=[regex]::Replace($part,'(?m)^  file_name:.*$',('  file_name: '+$(if($target){$target}else{'NONE'})))
            $part=[regex]::Replace($part,'(?m)^  is_download:.*$',('  is_download: '+$(if($target){'true'}else{'false'})))
            $sections[$i]=$part
            $rom=$doc.CreateElement('rom');$rom.SetAttribute('index',[regex]::Match($part,'partition_index:\s*SYS(\d+)').Groups[1].Value);$rom.SetAttribute('partition',$name);$rom.SetAttribute('enable',$(if($target){'true'}else{'false'}));if($target){$rom.InnerText=$target;$enabled++};[void]$doc.SelectSingleNode('/flashtool-config/general/rom-list').AppendChild($rom)
        }
        if($enabled -ne 2){throw 'Scatter must contain exactly BOOTIMG and ANDROID.'}
        $scatterCopy=Join-Path $directory 'MT6572_Android_scatter.txt';[IO.File]::WriteAllText($scatterCopy,($sections -join ''),[Text.UTF8Encoding]::new($false))
        $doc.'flashtool-config'.general.scatter=$scatterCopy
    }
    $schemas=[Xml.Schema.XmlSchemaSet]::new();[void]$schemas.Add('',$schemaPath);$doc.Schemas=$schemas;$doc.Validate({param($sender,$event)throw $event.Message})
    $config=Join-Path $directory ($command+'.xml');$doc.Save($config)
    if($command -ne 'readback' -and ($doc.SelectNodes('//rom[@enable="true"]').Count -ne 2 -or $doc.SelectNodes('//format|//firmware-upgrade|//write-memory|//readback').Count -ne 0)){throw 'Unsafe flash scope.'}
    return $config
}

function Invoke-FlashTool([string]$config,[string]$directory,[string]$successText) {
    Write-Host 'Power off the Y1 completely. The tool will wait; connect its USB data cable when asked.'
    $started=Get-Date
    $process=Start-Process -FilePath $flashExe -ArgumentList @('-i',('"'+$config+'"')) -WorkingDirectory $Updater -WindowStyle Hidden -RedirectStandardOutput (Join-Path $directory 'stdout.log') -RedirectStandardError (Join-Path $directory 'stderr.log') -PassThru
    Write-Host "Waiting for the Y1 (process $($process.Id)). Keep the cable connected until completion."
    if(-not $process.WaitForExit(3600000)){throw 'SP Flash Tool is still running. Do not disconnect the player; inspect its status.'}
    $nativeRoot='C:\ProgramData\SP_FT_Logs'
    $logs=@(Get-ChildItem -LiteralPath $nativeRoot -Directory -ErrorAction SilentlyContinue | Where-Object {$_.CreationTime -ge $started.AddSeconds(-5)} | ForEach-Object {Join-Path $_.FullName 'QT_FLASH_TOOL.log'} | Where-Object {Test-Path -LiteralPath $_})
    $matching=@($logs | Where-Object {(Select-String -LiteralPath $_ -SimpleMatch "FlashTool[$($process.Id)]" -Quiet) -and (Select-String -LiteralPath $_ -SimpleMatch $successText -Quiet)})
    if($matching.Count -ne 1){throw 'No unique successful native completion log. Keep the backup and inspect SP Flash Tool logs.'}
    Copy-Item -LiteralPath $matching[0] -Destination (Join-Path $directory 'QT_FLASH_TOOL.log') -Force
    Write-Host "Verified SP Flash Tool completion: $successText"
}

if($RestoreBackup) {
    $backup=(Resolve-Path -LiteralPath $RestoreBackup).Path
    $backupManifest=Get-Content -LiteralPath (Join-Path $backup 'backup.json') -Raw | ConvertFrom-Json
    if($backupManifest.status -ne 'verified_y1_type_a'){throw 'Unverified rollback backup.'}
    $bootPath=Join-Path $backup 'boot.img';$systemPath=Join-Path $backup 'system.img'
    Assert-Images $bootPath $systemPath $backupManifest.boot_sha256 $backupManifest.system_sha256
} else {
    $bootPath=Join-Path $package 'boot.img';$systemPath=Join-Path $package 'system.img'
    Assert-Images $bootPath $systemPath $manifest.boot_sha256 $manifest.system_sha256
    if($ValidateOnly) {
        $validation=Join-Path ([IO.Path]::GetTempPath()) ('onloopio-installer-validation-'+[Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $validation | Out-Null
        try {
            $sample=@(@{offset=0;length=0x8000000;file=(Join-Path $validation 'profile.bin')})
            [void](New-Config $validation 'readback' $sample $scatter)
            [void](New-Config $validation 'download-only' @(@{file=$bootPath},@{file=$systemPath}) $scatter)
            Write-Host 'Package hashes, headers, scatter and installer XML schemas validated. No device accessed.'
        } finally {
            $checked=[IO.Path]::GetFullPath($validation)
            if(-not $checked.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()),[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe cleanup path'}
            Remove-Item -LiteralPath $checked -Recurse -Force
        }
        return
    }
    Write-Host 'This package supports only the verified Y1 Type A hardware profile.'
    if((Read-Host 'Confirm your player is Innioasis Y1 Type A (type YES)') -cne 'YES'){throw 'Installation cancelled.'}
    $backupRootFull=[IO.Path]::GetFullPath($BackupRoot)
    $repoRoot=[IO.Path]::GetFullPath((Join-Path $package '..'))
    if($backupRootFull.StartsWith($repoRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Backup root must be outside the firmware package.'}
    New-Item -ItemType Directory -Force -Path $backupRootFull | Out-Null
    $backup=Join-Path $backupRootFull ('backup-'+(Get-Date -Format 'yyyyMMdd-HHmmss'));New-Item -ItemType Directory -Path $backup | Out-Null
    $ranges=@(
        @{offset=0;length=0x8000000;file=(Join-Path $backup 'profile.bin')},
        @{offset=0x3180000;length=0x600000;file=(Join-Path $backup 'boot.img')},
        @{offset=0x3780000;length=0x600000;file=(Join-Path $backup 'recovery.img')},
        @{offset=0x4b40000;length=0x28a00000;file=(Join-Path $backup 'system.img')}
    )
    $readConfig=New-Config $backup 'readback' $ranges $scatter
    Invoke-FlashTool $readConfig $backup 'Readback result: S_DONE(0)'
    foreach($range in $ranges){if((Get-Item -LiteralPath $range.file).Length -ne $range.length){throw 'Backup readback length mismatch.'}}
    $profile=[IO.File]::OpenRead($ranges[0].file)
    try {
        $profile.Position=0x1400000;$sector=[byte[]]::new(512);if($profile.Read($sector,0,512) -ne 512 -or $sector[510] -ne 0x55 -or $sector[511] -ne 0xAA){throw 'Y1 legacy MBR not found.'}
        $entry=446+3*16;$lba=[BitConverter]::ToUInt32($sector,$entry+8);$blocks=[BitConverter]::ToUInt32($sector,$entry+12)
        if($sector[$entry+4] -ne 0x83 -or 0x1400000+$lba*512 -ne 0x4b40000 -or $blocks*512 -ne 0x28a00000){throw 'System partition map differs from the verified Y1 profile.'}
    } finally {$profile.Dispose()}
    $backupBootHash=(Get-FileHash -LiteralPath (Join-Path $backup 'boot.img') -Algorithm SHA256).Hash.ToLowerInvariant()
    $backupSystemHash=(Get-FileHash -LiteralPath (Join-Path $backup 'system.img') -Algorithm SHA256).Hash.ToLowerInvariant()
    Assert-Images (Join-Path $backup 'boot.img') (Join-Path $backup 'system.img') $backupBootHash $backupSystemHash
    if([Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes((Join-Path $backup 'recovery.img')),0,8) -ne 'ANDROID!'){throw 'Recovery header differs from verified profile.'}
    @{status='verified_y1_type_a';boot_sha256=$backupBootHash;system_sha256=$backupSystemHash;profile_sha256=(Get-FileHash -LiteralPath (Join-Path $backup 'profile.bin') -Algorithm SHA256).Hash.ToLowerInvariant()} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'backup.json') -Encoding UTF8
    Write-Host "Private BOOT/SYSTEM backup verified at $backup"
}

$deployment=Join-Path $backup ('deployment-'+(Get-Date -Format 'yyyyMMdd-HHmmss'));New-Item -ItemType Directory -Path $deployment | Out-Null
$images=@(@{file=$bootPath},@{file=$systemPath})
$writeConfig=New-Config $deployment 'download-only' $images $scatter
Write-Host 'Only BOOTIMG and ANDROID will be written. USERDATA, NVRAM, preloader and partition tables remain untouched.'
if((Read-Host 'Type FLASH to write the selected images') -cne 'FLASH'){throw 'Flash cancelled. The backup is retained.'}
Invoke-FlashTool $writeConfig $deployment 'DADownloadAll::exec(): Download result: S_DONE(0)'
Write-Host 'Firmware write completed. Start the Y1, check the OnLoopio screen, then run setup-usb.ps1 if this is a fresh installation.'
