param([switch]$Install, [string]$VersionName = "1.0.0", [int]$VersionCode = 1)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSCommandPath
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$aj   = "$sdk\platforms\android-36\android.jar"
$aapt2 = "$sdk\build-tools\36.0.0\aapt2.exe"
$d8    = "$sdk\build-tools\36.0.0\d8.bat"
$zipalign = "$sdk\build-tools\36.0.0\zipalign.exe"
$apksigner= "$sdk\build-tools\36.0.0\apksigner.bat"

$b = "$root\build"
Remove-Item "$b\res","$b\gen","$b\obj","$b\dex" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$b\res","$b\gen","$b\obj","$b\dex" | Out-Null

Write-Host "1/6 aapt2 compile"
& $aapt2 compile --dir "$root\app\res" -o "$b\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "2/6 aapt2 link"
& $aapt2 link -o "$b\base-unsigned.apk" -I $aj --manifest "$root\app\AndroidManifest.xml" `
    --java "$b\gen" --min-sdk-version 31 --target-sdk-version 36 `
    --version-code $VersionCode --version-name $VersionName "$b\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "3/6 javac"
$srcs = @(Get-ChildItem "$root\app\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$srcs += @(Get-ChildItem "$b\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& javac -source 8 -target 8 -nowarn -bootclasspath $aj -d "$b\obj" $srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "4/6 d8"
$classes = @(Get-ChildItem "$b\obj" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& cmd /c "`"$d8`" --lib `"$aj`" --min-api 31 --output `"$b\dex`" $($classes -join ' ')"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "5/6 package"
Copy-Item "$b\base-unsigned.apk" "$b\base-dex.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$b\base-dex.apk", 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$b\dex\classes.dex", "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
$zip.Dispose()
& $zipalign -f -p 4 "$b\base-dex.apk" "$b\base-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "6/6 sign"
# The keystore lives outside build/ so rebuilding never changes the app signature.
$ks = "$root\signing\calwall.keystore"
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $ks) | Out-Null
    & keytool -genkeypair -keystore $ks -storepass calwall -keypass calwall -alias calwall `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=CalWall,OU=DSH,O=DSH,L=Seoul,S=Seoul,C=KR" | Out-Null
}
& $apksigner sign --ks $ks --ks-pass pass:calwall --key-pass pass:calwall --out "$b\calwall.apk" "$b\base-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

$size = (Get-Item "$b\calwall.apk").Length
Copy-Item "$b\calwall.apk" "$root\CalWall-$VersionName.apk" -Force
Write-Host "built: $b\calwall.apk ($size bytes)"

if ($Install) {
    $adb = "$sdk\platform-tools\adb.exe"
    & $adb install -r "$b\calwall.apk"
    if ($LASTEXITCODE -ne 0) { throw "install failed" }
    & $adb shell am start -n dev.dsh.calwall/.MainActivity
}

