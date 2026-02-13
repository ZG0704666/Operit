$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$dataDir = Join-Path $projectRoot "data"
$logsDir = Join-Path $projectRoot "logs"
$configPath = Join-Path $dataDir "config.json"
$runtimePath = Join-Path $dataDir "runtime.json"
$pidPath = Join-Path $dataDir "agent.pid"
$activeLaunchPath = Join-Path $dataDir "active_launch.id"
$launcherLog = Join-Path $logsDir "launcher.log"
$outLog = Join-Path $logsDir "agent.out.log"
$errLog = Join-Path $logsDir "agent.err.log"

if (-not (Test-Path $dataDir)) { New-Item -ItemType Directory -Path $dataDir -Force | Out-Null }
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "$ts [$Level] $Message"
    Write-Host $line
    Add-Content -Path $launcherLog -Value $line -Encoding UTF8
}

function Format-HostForUrl {
    param([string]$InputHost)

    $value = [string]$InputHost
    if ([string]::IsNullOrWhiteSpace($value)) {
        return "127.0.0.1"
    }

    $trimmed = $value.Trim()
    if ($trimmed.Contains(":") -and -not ($trimmed.StartsWith("[") -and $trimmed.EndsWith("]"))) {
        return "[$trimmed]"
    }

    return $trimmed
}

function Test-AgentReady {
    param([string]$ReadyUrl)

    try {
        $resp = Invoke-RestMethod -Uri $ReadyUrl -Method Get -TimeoutSec 2
        return ($resp -and $resp.port -and [int]$resp.port -ge 1)
    }
    catch {
        return $false
    }
}

function Get-ListeningPid {
    param([int]$Port)

    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn -and $conn.OwningProcess) {
            return [int]$conn.OwningProcess
        }
    }
    catch {
        # ignore
    }

    return $null
}

function Stop-ExistingAgent {
    param([int]$Port)

    if (Test-Path $pidPath) {
        try {
            $pidText = (Get-Content -Raw $pidPath).Trim()
            if ($pidText -match '^\d+$') {
                $pidValue = [int]$pidText
                Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
                Write-Log "INFO" "Stopped PID from pid file: $pidValue"
            }
        }
        catch {
            Write-Log "WARN" "Failed to stop pid-file process: $($_.Exception.Message)"
        }
    }

    try {
        $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($conns) {
            $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
            foreach ($p in $pids) {
                if ($p -gt 0) {
                    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
                    Write-Log "INFO" "Stopped process listening on port ${Port}: PID $p"
                }
            }
        }
    }
    catch {
        $rows = netstat -ano | Select-String ":$Port .*LISTENING"
        foreach ($row in $rows) {
            $parts = ($row.ToString() -split '\s+') | Where-Object { $_ -ne '' }
            if ($parts.Count -ge 5 -and $parts[4] -match '^\d+$') {
                $p = [int]$parts[4]
                Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
                Write-Log "INFO" "Stopped process from netstat on port ${Port}: PID $p"
            }
        }
    }

    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $runtimePath -Force -ErrorAction SilentlyContinue
}

$config = $null
$port = 58321
$bindAddress = "127.0.0.1"

try {
    if (Test-Path $configPath) {
        $config = Get-Content -Raw $configPath | ConvertFrom-Json

        if ($config -and $config.port) {
            $parsedPort = [int]$config.port
            if ($parsedPort -ge 1 -and $parsedPort -le 65535) {
                $port = $parsedPort
            }
        }

        if ($config -and $config.bindAddress) {
            $parsedBindAddress = [string]$config.bindAddress
            if (-not [string]::IsNullOrWhiteSpace($parsedBindAddress)) {
                $bindAddress = $parsedBindAddress.Trim()
            }
        }
    }
}
catch {
    Write-Log "WARN" "Failed to read config.json, fallback host/port: $($_.Exception.Message)"
}

if ([string]::IsNullOrWhiteSpace($bindAddress)) {
    $bindAddress = "127.0.0.1"
}

$launchHost = $bindAddress
if ($launchHost -eq "0.0.0.0" -or $launchHost -eq "::") {
    $launchHost = "127.0.0.1"
}

$readyHosts = [System.Collections.Generic.List[string]]::new()
$readyHosts.Add($bindAddress)

if (-not $readyHosts.Contains("127.0.0.1")) {
    $readyHosts.Add("127.0.0.1")
}

if (-not $readyHosts.Contains("localhost")) {
    $readyHosts.Add("localhost")
}

$url = "http://$(Format-HostForUrl -InputHost $launchHost):$port"
$readyUrls = @()
foreach ($readyHost in $readyHosts) {
    $readyUrls += "http://$(Format-HostForUrl -InputHost $readyHost):$port/api/config"
}
$launchId = [guid]::NewGuid().ToString()
Set-Content -Path $activeLaunchPath -Value $launchId -Encoding ASCII

$mutexName = "Local\OperitPcAgentLauncher"
$mutex = New-Object System.Threading.Mutex($false, $mutexName)
$hasLock = $false

try {
    $hasLock = $mutex.WaitOne(0)
    if (-not $hasLock) {
        Write-Log "WARN" "Another launcher instance is running."
        exit 0
    }

    Write-Log "INFO" "===== operit_pc_agent.bat ====="
    Write-Log "INFO" "Launch ID: $launchId"
    Write-Log "INFO" "Working directory: $projectRoot"
    Write-Log "INFO" "Target bind: $bindAddress"
    Write-Log "INFO" "Target port: $port"
    Write-Log "INFO" "Ready probe URLs: $($readyUrls -join ', ')"

    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if (-not $nodeCmd) {
        Write-Log "ERROR" "Node.js not found. Please install Node.js 18+."
        exit 1
    }

    Write-Log "INFO" "Node version: $(node -v)"

    $originalNodeOptions = $env:NODE_OPTIONS
    $env:NODE_OPTIONS = ""

    Write-Log "INFO" "Force restart: cleaning previous running process..."
    Stop-ExistingAgent -Port $port
    Remove-Item -LiteralPath $runtimePath -Force -ErrorAction SilentlyContinue

    Write-Log "INFO" "Launching node process (hidden, NODE_OPTIONS cleared)."
    Start-Process -FilePath "node" -ArgumentList "src/server.js" -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog

    $maxWaitMs = 15000
    $stepMs = 250
    $readyUrlHit = $null
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

    while ($stopwatch.ElapsedMilliseconds -lt $maxWaitMs) {
        foreach ($candidateReadyUrl in $readyUrls) {
            if (Test-AgentReady -ReadyUrl $candidateReadyUrl) {
                $readyUrlHit = $candidateReadyUrl
                break
            }
        }

        if ($readyUrlHit) {
            break
        }

        Start-Sleep -Milliseconds $stepMs
    }

    $stopwatch.Stop()

    if (-not $readyUrlHit) {
        Write-Log "ERROR" "agent readiness endpoint not ready after startup (${maxWaitMs}ms). candidates: $($readyUrls -join ', ')"
        if (Test-Path $errLog) {
            $errTail = Get-Content -Tail 50 $errLog
            foreach ($line in $errTail) {
                Write-Log "ERROR" "agent.err.log: $line"
            }
        }
        exit 1
    }

    Write-Log "INFO" "Ready endpoint confirmed: $readyUrlHit"

    $resolvedPid = $null

    if (Test-Path $runtimePath) {
        try {
            $runtime = Get-Content -Raw $runtimePath | ConvertFrom-Json
            if ($runtime.pid) {
                $resolvedPid = [int]$runtime.pid
                Write-Log "INFO" "Runtime PID: $resolvedPid"
            }
        }
        catch {
            Write-Log "WARN" "Failed to parse runtime.json: $($_.Exception.Message)"
        }
    }

    if (-not $resolvedPid) {
        $listeningPid = Get-ListeningPid -Port $port
        if ($listeningPid) {
            $resolvedPid = [int]$listeningPid
            Write-Log "INFO" "Listening PID: $resolvedPid"
        }
    }

    if ($resolvedPid) {
        Set-Content -Path $pidPath -Value ([string]$resolvedPid) -Encoding ASCII
    }

    Write-Host "[OK] Operit PC Agent started on $url"
    Write-Log "OK" "Agent started: $url"

    $latestLaunchId = ""
    if (Test-Path $activeLaunchPath) {
        $latestLaunchId = (Get-Content -Raw $activeLaunchPath).Trim()
    }

    if ($latestLaunchId -eq $launchId) {
        try {
            Write-Log "INFO" "Opening browser: $url"
            Start-Process $url
        }
        catch {
            Write-Log "WARN" "Failed to open browser automatically: $($_.Exception.Message)"
            Write-Host "[WARN] Failed to open browser automatically. Open this URL manually: $url"
        }
    }
    else {
        Write-Log "INFO" "Browser open skipped - superseded by newer launch ID."
    }

    exit 0
}
finally {
    if ($null -eq $originalNodeOptions) {
        Remove-Item Env:NODE_OPTIONS -ErrorAction SilentlyContinue
    }
    else {
        $env:NODE_OPTIONS = $originalNodeOptions
    }

    if ($hasLock) {
        $mutex.ReleaseMutex() | Out-Null
    }
    $mutex.Dispose()
}
