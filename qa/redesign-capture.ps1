$ErrorActionPreference='Stop'
. "$PSScriptRoot/device-smoke.ps1" -FunctionsOnly
function Capture([string]$name){
    Start-Sleep -Seconds 2
    Device shell screencap -p /data/local/tmp/edcam-final.png
    Device pull /data/local/tmp/edcam-final.png (Join-Path $shots "$name.png") | Out-Null
}
Device shell settings put system font_scale 1.0
Device shell wm size 720x1600
Device shell wm density 280
Device shell am force-stop com.edcam
Device shell am start -n com.edcam/.MainActivity | Out-Null
Start-Sleep -Seconds 8
Capture 'redesign-phone'
Device shell input tap 270 1460
Start-Sleep -Seconds 3
$tree=UI
if(-not ($tree.SelectNodes('//node') | Where-Object text -eq 'Chọn chất ảnh')){throw 'Style screen not reached'}
Capture 'redesign-styles'
Tap 'Cài đặt'
foreach($name in @('Film kem','Đêm tối','Hiện đại','Hồng phấn','Xanh sage')){
    Tap 'Chọn giao diện';Tap $name
    Capture ('redesign-settings-'+[array]::IndexOf(@('Film kem','Đêm tối','Hiện đại','Hồng phấn','Xanh sage'),$name))
    if($name -eq 'Đêm tối'){Tap 'Chọn giao diện';Capture 'redesign-dark-dialog';Device shell input keyevent 4}
}
Tap 'Chụp ảnh'
Start-Sleep -Seconds 3
Device shell input tap 360 1325
Start-Sleep -Seconds 3
Device shell input tap 450 1460
Start-Sleep -Seconds 3
Capture 'redesign-album'
Device shell wm size 720x1280
Device shell wm density 320
Device shell am force-stop com.edcam
Device shell am start -n com.edcam/.MainActivity | Out-Null
Start-Sleep -Seconds 7
Capture 'redesign-compact'
Device shell settings put system font_scale 1.3
Start-Sleep -Seconds 4
Capture 'redesign-font130'
Device shell settings put system font_scale 1.0
Device shell wm size 720x1600
Device shell wm density 280
Device shell am force-stop com.edcam
Write-Output 'PASS: styles destination asserted; five palettes, dark dialog, capture/album, compact and 130 percent font states captured for visual review.'
