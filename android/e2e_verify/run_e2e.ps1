[CmdletBinding()]
param(
    [ValidateSet("all", "1", "2", "3", "4")]
    [string]$Tier = "all",
    [switch]$SkipGradle,
    [string]$JavaHome = ""
)

$ErrorActionPreference = "Stop"

$e2eDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$androidDir = Split-Path -Parent $e2eDir
$repoRoot = Split-Path -Parent $androidDir
$srcDir = Join-Path $e2eDir "src"
$buildDir = Join-Path $e2eDir "build"
$reportDir = Join-Path $e2eDir "reports"

function Resolve-Jdk21 {
    param([string]$ExplicitJavaHome, [string]$AndroidRoot)

    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($ExplicitJavaHome)) {
        $candidates.Add($ExplicitJavaHome)
    }

    $gradleProps = Join-Path $AndroidRoot "gradle.properties"
    if (Test-Path $gradleProps) {
        foreach ($line in Get-Content $gradleProps) {
            if ($line -match '^\s*org\.gradle\.java\.home\s*=\s*(.+)$') {
                $raw = $Matches[1].Trim()
                $unescaped = $raw.Replace('\\', '\').Replace('\:', ':')
                $candidates.Add($unescaped)
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add($env:JAVA_HOME)
    }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCmd) {
        $candidates.Add((Split-Path -Parent (Split-Path -Parent $javaCmd.Source)))
    }

    foreach ($cand in $candidates) {
        if ([string]::IsNullOrWhiteSpace($cand) -or -not (Test-Path $cand)) {
            continue
        }
        $javacPath = Join-Path $cand "bin\javac.exe"
        $javaPath = Join-Path $cand "bin\java.exe"
        if ((Test-Path $javacPath) -and (Test-Path $javaPath)) {
            $verOut = & $javacPath -version 2>&1 | Out-String
            if ($verOut -match 'javac\s+21(\.|\s|$)') {
                return [PSCustomObject]@{
                    JavaHome = $cand
                    Javac    = $javacPath
                    Java     = $javaPath
                    Version  = $verOut.Trim()
                }
            }
        }
    }

    throw "JDK 21 not found. Checked -JavaHome, android/gradle.properties (org.gradle.java.home), JAVA_HOME, and PATH."
}

$jdk = Resolve-Jdk21 -ExplicitJavaHome $JavaHome -AndroidRoot $androidDir
Write-Host "[E2E] Using $($jdk.Version) at $($jdk.JavaHome)"

if (Test-Path $buildDir) {
    Remove-Item -Recurse -Force $buildDir
}
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

$javaSources = Get-ChildItem -Path $srcDir -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
if ($javaSources.Count -eq 0) {
    throw "No Java sources found in $srcDir"
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$sourcesFile = Join-Path $buildDir "sources.txt"
[System.IO.File]::WriteAllLines($sourcesFile, $javaSources, $utf8NoBom)

Write-Host "[E2E] Compiling $($javaSources.Count) verification classes with javac --release 21..."
& $jdk.Javac --release 21 -Xlint:all -Werror -encoding UTF-8 -d $buildDir "@$sourcesFile"
if ($LASTEXITCODE -ne 0) {
    throw "Java compilation failed with exit code $LASTEXITCODE"
}

$buildExit = -1
$lintExit = -1

if (($Tier -eq "all" -or $Tier -eq "4") -and (-not $SkipGradle)) {
    $env:JAVA_HOME = $jdk.JavaHome
    $env:PATH = "$($jdk.JavaHome)\bin;$env:PATH"
    Push-Location $androidDir
    try {
        Write-Host "[E2E] Running .\gradlew.bat assembleDebug --no-daemon ..."
        & .\gradlew.bat assembleDebug --no-daemon
        $buildExit = $LASTEXITCODE

        Write-Host "[E2E] Running .\gradlew.bat lintDebug --no-daemon ..."
        & .\gradlew.bat lintDebug --no-daemon
        $lintExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}

$gitStatusFile = Join-Path $buildDir "git_status.txt"
$scopeViolationsFile = Join-Path $buildDir "scope_violations.txt"

Push-Location $repoRoot
try {
    $gitOut = & git status --porcelain 2>&1
    if ($LASTEXITCODE -eq 0) {
        [System.IO.File]::WriteAllLines($gitStatusFile, @($gitOut | ForEach-Object { "$_" }), $utf8NoBom)
    } else {
        [System.IO.File]::WriteAllText($gitStatusFile, "", $utf8NoBom)
    }

    $violations = New-Object System.Collections.Generic.List[string]
    $cutoffUtc = [DateTime]::Parse("2026-11-26T12:35:00Z").ToUniversalTime()
    foreach ($dirName in @("backend", "database", "docs", "frontend")) {
        $target = Join-Path $repoRoot $dirName
        if (Test-Path $target) {
            Get-ChildItem -Path $target -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
                if ($_.LastWriteTimeUtc -gt $cutoffUtc) {
                    $rel = $_.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
                    $violations.Add("MODIFIED_AFTER_CUTOFF: $rel ($($_.LastWriteTimeUtc.ToString('o')))")
                }
            }
        }
    }

    $diffTracked = & git diff --name-only 2>$null
    $diffStaged = & git diff --cached --name-only 2>$null
    foreach ($p in @($diffTracked) + @($diffStaged)) {
        if (-not [string]::IsNullOrWhiteSpace($p)) {
            $norm = $p.Trim().Replace('\', '/')
            if (-not $norm.StartsWith("android/")) {
                $violations.Add("GIT_MODIFIED_OUTSIDE_ANDROID: $norm")
            }
        }
    }

    [System.IO.File]::WriteAllLines($scopeViolationsFile, $violations, $utf8NoBom)
}
finally {
    Pop-Location
}

Write-Host "[E2E] Executing E2ERunner (tier=$Tier)..."
& $jdk.Java -cp $buildDir vn.edu.tdtu.phlbanking.e2e.E2ERunner `
    --android-dir $androidDir `
    --repo-root $repoRoot `
    --tier $Tier `
    --skip-gradle ($SkipGradle.IsPresent.ToString().ToLower()) `
    --build-exit $buildExit `
    --lint-exit $lintExit `
    --git-status-file $gitStatusFile `
    --scope-violations-file $scopeViolationsFile `
    --report-dir $reportDir `
    --jdk-version $jdk.Version

exit $LASTEXITCODE
