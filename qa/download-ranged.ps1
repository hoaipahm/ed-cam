param([Parameter(Mandatory=$true)][string]$Url,[Parameter(Mandatory=$true)][long]$Size,[Parameter(Mandatory=$true)][string]$Output,[int]$Connections=8)
$ErrorActionPreference='Stop'
$absoluteOutput=[IO.Path]::GetFullPath((Join-Path (Get-Location) $Output))
$partSize=[long][Math]::Ceiling($Size/[double]$Connections)
$jobs=@()
for($index=0;$index -lt $Connections;$index++) {
    $start=$index*$partSize
    $end=[Math]::Min($Size-1,$start+$partSize-1)
    $partPath="$absoluteOutput.part$index"
    $helper=Join-Path $PSScriptRoot 'download-part.ps1'
    $arguments=@('-NoProfile','-File',('"'+$helper+'"'),'-Url',('"'+$Url+'"'),'-Start',$start,'-End',$end,'-Part',('"'+$partPath+'"'))
    $process=Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $jobs+=@{ Process=$process; Path=$partPath; Expected=$end-$start+1 }
}
foreach($job in $jobs){$job.Process.WaitForExit();if($job.Process.ExitCode -ne 0){throw "Download failed: $($job.Path)"};if((Get-Item -LiteralPath $job.Path).Length -ne $job.Expected){throw "Invalid range size: $($job.Path)"}}
$destination=[IO.File]::Create($absoluteOutput)
try{foreach($job in $jobs){$part=[IO.File]::OpenRead($job.Path);try{$part.CopyTo($destination)}finally{$part.Dispose()}}}finally{$destination.Dispose()}
if((Get-Item -LiteralPath $absoluteOutput).Length -ne $Size){throw 'Invalid assembled size'}
Write-Output "Downloaded and size-checked: $Output ($Size bytes)"
