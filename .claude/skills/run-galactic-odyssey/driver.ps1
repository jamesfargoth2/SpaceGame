<#
.SYNOPSIS
    Driver script for building, launching, screenshotting, and controlling Galactic Odyssey.
    Lives at: .claude/skills/run-galactic-odyssey/driver.ps1
    Run from project root: C:\Users\james\IdeaProjects\SpaceGame

.DESCRIPTION
    Provides commands for agents and humans to interact with the running game:
      build        - Compile the desktop module
      launch       - Start the game window (non-blocking)
      screenshot   - Capture the game window to a PNG
      focus        - Bring game window to foreground
      sendkey      - Send a keystroke to the game window
      status       - Check if the game is running
      close        - Kill the game process
      smoke        - Full build -> launch -> wait -> screenshot -> close cycle

.EXAMPLE
    .\driver.ps1 build
    .\driver.ps1 launch
    .\driver.ps1 screenshot
    .\driver.ps1 smoke
#>

param(
    [Parameter(Position = 0)]
    [ValidateSet("build", "launch", "screenshot", "focus", "sendkey", "status", "close", "smoke")]
    [string]$Command = "status",

    [Parameter(Position = 1)]
    [string]$Arg1 = "",

    [Parameter(Position = 2)]
    [string]$Arg2 = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$ScreenshotDir = Join-Path $ProjectRoot ".claude"
$GameTitle = "Galactic Odyssey"

# --- JDK discovery ---
function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }
    $jdkDir = Join-Path $env:USERPROFILE ".jdks"
    if (Test-Path $jdkDir) {
        $jdk = Get-ChildItem $jdkDir -Directory | Sort-Object Name -Descending | Select-Object -First 1
        if ($jdk) { return $jdk.FullName }
    }
    $progJava = "C:\Program Files\Java"
    if (Test-Path $progJava) {
        $jdk = Get-ChildItem $progJava -Directory | Where-Object { $_.Name -like "jdk*" } | Sort-Object Name -Descending | Select-Object -First 1
        if ($jdk) { return $jdk.FullName }
    }
    throw "No JDK found. Set JAVA_HOME or install a JDK under ~/.jdks/"
}

function Set-JavaEnv {
    $env:JAVA_HOME = Find-JavaHome
    Write-Host "JAVA_HOME=$env:JAVA_HOME"
}

# --- Win32 interop for window management ---
Add-Type @"
using System;
using System.Runtime.InteropServices;

public class GameDriver {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }

    public const int SW_RESTORE = 9;
}
"@
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms

function Get-GameProcess {
    Get-Process -Name "java" -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -eq $GameTitle } |
        Select-Object -First 1
}

# --- Commands ---

function Invoke-Build {
    Set-JavaEnv
    Push-Location $ProjectRoot
    try {
        Write-Host "Building desktop module..."
        & .\gradlew.bat :desktop:classes
        if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
        Write-Host "BUILD OK"
    } finally { Pop-Location }
}

function Invoke-Launch {
    $existing = Get-GameProcess
    if ($existing) {
        Write-Host "Game already running (PID $($existing.Id))"
        return
    }
    Set-JavaEnv
    Push-Location $ProjectRoot
    try {
        Write-Host "Launching game..."
        Start-Process -FilePath ".\gradlew.bat" -ArgumentList ":desktop:run" -WindowStyle Normal
        $timeout = 30
        $elapsed = 0
        while ($elapsed -lt $timeout) {
            Start-Sleep -Seconds 1
            $elapsed++
            $proc = Get-GameProcess
            if ($proc) {
                Write-Host "Game window appeared (PID $($proc.Id)) after ${elapsed}s"
                Start-Sleep -Seconds 3
                return
            }
        }
        Write-Warning "Game window did not appear within ${timeout}s - check for errors"
    } finally { Pop-Location }
}

function Invoke-Screenshot {
    $outName = if ($Arg1) { $Arg1 } else { "game_screenshot_$(Get-Date -Format 'yyyyMMdd_HHmmss').png" }
    $outPath = Join-Path $ScreenshotDir $outName

    $proc = Get-GameProcess
    if (-not $proc) { throw "Game is not running. Use 'launch' first." }

    [GameDriver]::ShowWindow($proc.MainWindowHandle, [GameDriver]::SW_RESTORE)
    Start-Sleep -Milliseconds 300
    [GameDriver]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 500

    $rect = New-Object GameDriver+RECT
    [GameDriver]::GetWindowRect($proc.MainWindowHandle, [ref]$rect) | Out-Null
    $w = $rect.Right - $rect.Left
    $h = $rect.Bottom - $rect.Top
    if ($w -le 0 -or $h -le 0) { throw "Failed to get window dimensions" }

    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($rect.Left, $rect.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))
    $g.Dispose()
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Screenshot saved: $outPath"
    return $outPath
}

function Invoke-Focus {
    $proc = Get-GameProcess
    if (-not $proc) { throw "Game is not running." }
    [GameDriver]::ShowWindow($proc.MainWindowHandle, [GameDriver]::SW_RESTORE)
    [GameDriver]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
    Write-Host "Focused game window (PID $($proc.Id))"
}

function Invoke-SendKey {
    if (-not $Arg1) { throw "Usage: driver.ps1 sendkey <key> [count]" }
    $proc = Get-GameProcess
    if (-not $proc) { throw "Game is not running." }

    [GameDriver]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 200

    $count = if ($Arg2) { [int]$Arg2 } else { 1 }
    for ($i = 0; $i -lt $count; $i++) {
        [System.Windows.Forms.SendKeys]::SendWait($Arg1)
        Start-Sleep -Milliseconds 50
    }
    Write-Host "Sent key '$Arg1' x$count"
}

function Invoke-Status {
    $proc = Get-GameProcess
    if ($proc) {
        $mem = [math]::Round($proc.WorkingSet64 / 1MB, 0)
        Write-Host "RUNNING - PID $($proc.Id), Memory: ${mem}MB"
    } else {
        Write-Host "NOT RUNNING"
    }
}

function Invoke-Close {
    $procs = Get-Process -Name "java" -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -eq $GameTitle }
    if (-not $procs) {
        Write-Host "Game is not running."
        return
    }
    $procs | ForEach-Object {
        Write-Host "Closing PID $($_.Id)..."
        $_.CloseMainWindow() | Out-Null
    }
    Start-Sleep -Seconds 2
    $remaining = Get-GameProcess
    if ($remaining) {
        Write-Host "Force-killing remaining process..."
        $remaining | Stop-Process -Force
    }
    Write-Host "Game closed."
}

function Invoke-Smoke {
    Write-Host "=== SMOKE TEST ==="
    Invoke-Build
    Invoke-Launch
    Start-Sleep -Seconds 5
    $path = Invoke-Screenshot
    Invoke-Close
    Write-Host "=== SMOKE COMPLETE ==="
    Write-Host "Screenshot: $path"
}

# --- Dispatch ---
switch ($Command) {
    "build"      { Invoke-Build }
    "launch"     { Invoke-Launch }
    "screenshot" { Invoke-Screenshot }
    "focus"      { Invoke-Focus }
    "sendkey"    { Invoke-SendKey }
    "status"     { Invoke-Status }
    "close"      { Invoke-Close }
    "smoke"      { Invoke-Smoke }
}
