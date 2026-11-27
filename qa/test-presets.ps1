$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $projectRoot '.tools/preset-tests'
New-Item -ItemType Directory -Force $classes | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $projectRoot 'app/src/main/java/com/edcam/Presets.java') (Join-Path $PSScriptRoot 'PresetTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& java -cp $classes PresetTest
if ($LASTEXITCODE -ne 0) { throw 'Preset verification failed' }
& javac -encoding UTF-8 -d $classes (Join-Path $projectRoot 'app/src/main/java/com/edcam/ZoomMath.java') (Join-Path $projectRoot 'app/src/main/java/com/edcam/CaptureCountdown.java') (Join-Path $PSScriptRoot 'CameraCoreTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Camera core compilation failed' }
& java -cp $classes CameraCoreTest
if ($LASTEXITCODE -ne 0) { throw 'Camera core verification failed' }
