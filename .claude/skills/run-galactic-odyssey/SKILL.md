---
name: run-galactic-odyssey
description: Build, launch, screenshot, and drive the Galactic Odyssey game. Use when asked to run the game, start the app, take a screenshot, verify a visual change, test gameplay, or confirm the game builds and launches correctly.
---

# Run Galactic Odyssey

Native libGDX desktop game (Java 21+, LWJGL3, Bullet physics). Driven via `driver.ps1` — a PowerShell script that builds, launches, captures screenshots, sends input, and closes the game window.

All paths below are relative to the project root (`C:\Users\james\IdeaProjects\SpaceGame`).

## Prerequisites

- **JDK 21+** installed under `~/.jdks/` (IntelliJ convention) or `JAVA_HOME` set. The driver auto-discovers JDKs from `~/.jdks/` and `C:\Program Files\Java\`.
- **Gradle wrapper** (`gradlew.bat`) — already in the repo, no install needed.
- **GPU with OpenGL support** — this is a 3D game with custom shaders.

## Run (Agent Path) — Use the Driver

The driver lives at `.claude/skills/run-galactic-odyssey/driver.ps1`. Run it from any directory — it resolves the project root automatically.

### Commands

```powershell
# Check if the game is already running
.\.claude\skills\run-galactic-odyssey\driver.ps1 status

# Build the desktop module (compiles core + desktop)
.\.claude\skills\run-galactic-odyssey\driver.ps1 build

# Launch the game (waits for window to appear, up to 30s)
.\.claude\skills\run-galactic-odyssey\driver.ps1 launch

# Capture a screenshot of the game window
.\.claude\skills\run-galactic-odyssey\driver.ps1 screenshot
.\.claude\skills\run-galactic-odyssey\driver.ps1 screenshot "my_test.png"

# Send keystrokes to the game (e.g. W to walk forward, Escape to pause)
.\.claude\skills\run-galactic-odyssey\driver.ps1 sendkey "w" 10

# Bring game window to foreground
.\.claude\skills\run-galactic-odyssey\driver.ps1 focus

# Close the game
.\.claude\skills\run-galactic-odyssey\driver.ps1 close

# Full smoke test: build -> launch -> wait -> screenshot -> close
.\.claude\skills\run-galactic-odyssey\driver.ps1 smoke
```

Screenshots save to `.claude/` by default (timestamped PNGs). Use `Read` tool to view them.

### Typical Agent Workflow

```powershell
# 1. Build after making code changes
.\.claude\skills\run-galactic-odyssey\driver.ps1 build

# 2. Launch the game
.\.claude\skills\run-galactic-odyssey\driver.ps1 launch

# 3. Wait a few seconds for world to load, then screenshot
Start-Sleep -Seconds 5
.\.claude\skills\run-galactic-odyssey\driver.ps1 screenshot "verification.png"

# 4. View the screenshot with Read tool to verify changes

# 5. Close when done
.\.claude\skills\run-galactic-odyssey\driver.ps1 close
```

## Run (Human Path)

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :desktop:run
```

Opens a 1280x720 window. WASD to move, mouse to look, Escape to pause. Close via pause menu or Alt+F4.

## Build

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :desktop:classes
```

Compiles `core` and `desktop` modules. Working directory for the game is set to `core/src/main/resources/` (configured in `desktop/build.gradle.kts`).

## Test

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test
```

## Current Game State

When launched, the game shows:
- **Main Menu** with New Game / Settings / Exit
- **GameScreen** spawns the player on a procedurally generated planet (cube-sphere terrain with biomes)
- **FPS mode** (ON_FOOT_EXTERIOR) — WASD movement, mouse look, sprint with Shift
- **Debug HUD** shows FPS, player mode, coordinates, velocity, ground state, stamina
- **A procedural ship** is spawned near the player on the planet surface
- No ship piloting yet — ships are static props

## Gotchas

- **Stale Gradle daemons**: If the build fails with mysterious compilation errors that don't match the source on disk, run `.\gradlew.bat --stop` to kill cached daemons, then rebuild. This happens when worktree agents leave daemons running with different classpaths.
- **`2>&1` in PowerShell 5.1**: Never redirect stderr with `2>&1` on native executables in PowerShell 5.1 — it wraps each stderr line in an ErrorRecord and sets `$?` to `$false` even on success. The driver avoids this.
- **Window capture**: The driver captures the game window by its Win32 rect. If the window is minimized or behind other windows, it calls `ShowWindow(SW_RESTORE)` + `SetForegroundWindow` first. Occasionally the first screenshot after launch captures the window mid-render — add a 2-3 second delay after `launch` before `screenshot`.
- **Cursor lock**: The game captures the mouse cursor in gameplay mode. The driver's `sendkey` command focuses the window first. Press Escape to toggle pause (releases cursor).
- **Multiple java processes**: Other Gradle daemons or worktree builds may spawn `java.exe` processes. The driver identifies the game window specifically by its title "Galactic Odyssey".
- **File locks on clean**: `.\gradlew.bat :core:clean` may fail if a Gradle daemon holds `core-0.1.0.jar` open. Stop daemons first with `.\gradlew.bat --stop`.
