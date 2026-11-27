param([string]$Serial='emulator-5554',[switch]$FunctionsOnly)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $project '.tools/android-sdk/platform-tools/adb.exe'
$shots=Join-Path $project '.impeccable/review'
New-Item -ItemType Directory -Force $shots | Out-Null
function Device { $ErrorActionPreference='Continue'; $output=& $adb -s $Serial @args 2>&1; if($LASTEXITCODE -ne 0){throw ($output -join "`n")}; $output | ForEach-Object { "$_" } }
function UI {
    Device shell uiautomator dump /data/local/tmp/edcam-ui.xml | Out-Null
    Device pull /data/local/tmp/edcam-ui.xml (Join-Path $PSScriptRoot 'ui.xml') | Out-Null
    [xml](Get-Content (Join-Path $PSScriptRoot 'ui.xml') -Raw -Encoding UTF8)
}
function Tap([string]$label){
    $doc=UI
    $node=$doc.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq $label -and $_.clickable -eq 'true' } | Select-Object -First 1
    if(-not $node){$node=$doc.SelectNodes('//node') | Where-Object { $_.text -eq $label } | Select-Object -First 1}
    if(-not $node){throw "UI action not found: $label"}
    $numbers=[regex]::Matches($node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    Device shell input tap ([int](($numbers[0]+$numbers[2])/2)) ([int](($numbers[1]+$numbers[3])/2))
    Start-Sleep -Milliseconds 1800
}
function Shot([string]$name){ UI | Out-Null; Start-Sleep -Seconds 2; Device shell screencap -p /data/local/tmp/edcam-shot.png;Device pull /data/local/tmp/edcam-shot.png (Join-Path $shots "$name.png") | Out-Null}
if($FunctionsOnly){return}
Device shell pm grant com.edcam android.permission.CAMERA
Device shell am start -n com.edcam/.MainActivity | Out-Null
Start-Sleep -Seconds 3
Tap 'Chọn giao diện';Tap 'Film kem';Shot 'phone-film'
Tap 'Style';Shot 'phone-styles'
Tap 'Album';Shot 'phone-album'
Tap 'Cài đặt';Shot 'phone-settings'
$names=@('Film kem','Đêm tối','Hiện đại','Hồng phấn','Xanh sage')
for($index=0;$index -lt $names.Length;$index++){
    Tap 'Chọn giao diện';Tap $names[$index];Tap 'Chụp ảnh';Shot "phone-theme-$index"
}
Tap 'Chụp ảnh ngay'
Start-Sleep -Seconds 3
Tap 'Album';Shot 'phone-album-captured'
Write-Output 'PASS: navigation and five theme switch flows completed; inspect screenshots and capture behavior separately.'
