$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$exampleRoot = Join-Path $repoRoot "examples\apktool"
$tempRoot = Join-Path $repoRoot "temp\apktool-runtime-patch"
$sourceRoot = Join-Path $exampleRoot "runtime_patch_src"
$resourceDir = Join-Path $exampleRoot "resources\apktool"
$runtimeJarPath = Join-Path $resourceDir "apktool-runtime-android.jar"
$apktoolCliJarPath = Join-Path $tempRoot "apktool-cli-3.0.1.jar"
$patchedCliJarPath = Join-Path $tempRoot "apktool-cli-3.0.1-patched.jar"
$r8JarPath = Join-Path $tempRoot "r8.jar"
$extractDir = Join-Path $tempRoot "apktool-cli-extracted"
$compileDir = Join-Path $tempRoot "compiled-classes"
$d8OutputDir = Join-Path $tempRoot "d8-output"
$runtimeWorkDir = Join-Path $tempRoot "runtime-jar"
$javaBinDir = "D:\Program Files\Zulu\zulu-21\bin"
$javacPath = Join-Path $javaBinDir "javac.exe"
$jarToolPath = Join-Path $javaBinDir "jar.exe"
$javaPath = Join-Path $javaBinDir "java.exe"
$apktoolCliUrl = "https://repo1.maven.org/maven2/org/apktool/apktool-cli/3.0.1/apktool-cli-3.0.1.jar"
$r8Url = "https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.0.32/r8-9.0.32.jar"

function Require-File([string] $path) {
    if (-not (Test-Path $path -PathType Leaf)) {
        throw "Required file not found: $path"
    }
}

function Ensure-CleanDirectory([string] $path) {
    if (Test-Path $path) {
        Remove-Item -Recurse -Force $path
    }
    New-Item -ItemType Directory -Path $path | Out-Null
}

function Ensure-Download([string] $url, [string] $path) {
    if (-not (Test-Path $path -PathType Leaf)) {
        Invoke-WebRequest -UseBasicParsing $url -OutFile $path
    }
}

function Find-AndroidJar {
    $androidJar = Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter android.jar -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $androidJar) {
        throw "android.jar not found under Gradle caches"
    }
    return $androidJar
}

Require-File $javacPath
Require-File $jarToolPath
Require-File $javaPath

New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
Ensure-Download $apktoolCliUrl $apktoolCliJarPath
Ensure-Download $r8Url $r8JarPath

$androidJarPath = Find-AndroidJar

Ensure-CleanDirectory $extractDir
Ensure-CleanDirectory $compileDir
Ensure-CleanDirectory $d8OutputDir
Ensure-CleanDirectory $runtimeWorkDir

Copy-Item $apktoolCliJarPath $patchedCliJarPath -Force

& $javacPath `
    -encoding UTF-8 `
    -source 8 `
    -target 8 `
    -cp "$apktoolCliJarPath;$androidJarPath" `
    -d $compileDir `
    (Join-Path $sourceRoot "brut\androlib\res\decoder\ResNinePatchStreamDecoder.java")

Push-Location $compileDir
try {
    & $jarToolPath uf $patchedCliJarPath "brut/androlib/res/decoder/ResNinePatchStreamDecoder.class"
} finally {
    Pop-Location
}

$d8Args = @(
    "-cp", $r8JarPath,
    "com.android.tools.r8.D8",
    "--release",
    "--min-api", "26",
    "--output", $d8OutputDir,
    "--lib", $androidJarPath,
    "--classpath", $patchedCliJarPath
)
$d8Args += $patchedCliJarPath

& $javaPath @d8Args

Require-File (Join-Path $d8OutputDir "classes.dex")
Copy-Item (Join-Path $d8OutputDir "classes.dex") (Join-Path $runtimeWorkDir "classes.dex")

if (Test-Path $runtimeJarPath -PathType Leaf) {
    Remove-Item $runtimeJarPath -Force
}

Push-Location $runtimeWorkDir
try {
    & $jarToolPath cf $runtimeJarPath "classes.dex"
} finally {
    Pop-Location
}

Get-Item $runtimeJarPath | Select-Object FullName, Length, LastWriteTime
