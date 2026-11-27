param([string]$Url,[long]$Start,[long]$End,[string]$Part)
$ErrorActionPreference='Stop'
for($attempt=0;$attempt -lt 40;$attempt++){
    $existing=if(Test-Path -LiteralPath $Part){(Get-Item -LiteralPath $Part).Length}else{0}
    $offset=$Start+$existing
    if($offset -gt $End){exit 0}
    $chunk="$Part.chunk"
    $headers="$Part.headers"
    $requestUrl=$Url+'?edcam='+$Start+'-'+$attempt+'-'+[DateTime]::UtcNow.Ticks
    $ErrorActionPreference='Continue'
    & curl.exe --silent --show-error --location --fail --range "$offset-$End" --max-time 35 --speed-time 12 --speed-limit 30000 --dump-header $headers --output $chunk $requestUrl 2>$null
    $ErrorActionPreference='Stop'
    if((Test-Path -LiteralPath $chunk) -and (Get-Content -LiteralPath $headers -Raw) -match "(?im)^content-range:\s*bytes $offset-"){
        $chunkLength=(Get-Item -LiteralPath $chunk).Length
        if($chunkLength -gt 0 -and $chunkLength -le ($End-$offset+1)){
            $destination=[IO.File]::Open($Part,[IO.FileMode]::Append)
            $source=[IO.File]::OpenRead($chunk)
            try{$source.CopyTo($destination)}finally{$source.Dispose();$destination.Dispose()}
        }
    }
}
exit 1
