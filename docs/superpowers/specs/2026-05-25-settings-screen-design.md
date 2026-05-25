# Settings Screen Design Spec

## Overview

A tabbed settings screen accessible from the main menu with two tabs: Display and Audio. Built entirely with Scene2D.UI widgets extending the existing UiFactory cyan-on-dark sci-fi theme. Audio changes apply live; display changes require Apply with a 10-second revert timer.

## Components

### GamePreferences

**File:** `core/src/main/java/com/galacticodyssey/core/GamePreferences.java`

Wraps `Gdx.app.getPreferences("GalacticOdyssey")` as the single source of truth for all user settings.

**Stored values:**

| Key | Type | Default | Notes |
|-----|------|---------|-------|
| `displayMode` | String (enum) | `WINDOWED` | One of: `WINDOWED`, `FULLSCREEN`, `BORDERLESS` |
| `resolutionWidth` | int | 1280 | Ignored when fullscreen |
| `resolutionHeight` | int | 720 | Ignored when fullscreen |
| `vsync` | boolean | true | |
| `masterVolume` | float | 1.0 | Range 0.0–1.0 |
| `musicVolume` | float | 0.7 | Range 0.0–1.0 |
| `sfxVolume` | float | 0.8 | Range 0.0–1.0 |

**API:**

- Getters and setters for each value (setters clamp floats to [0, 1])
- `save()` — flushes preferences to disk
- `load()` — reads from disk, called once at startup
- `getEffectiveMusicVolume()` — returns `masterVolume * musicVolume`
- `getEffectiveSfxVolume()` — returns `masterVolume * sfxVolume`

**Display mode enum:** `GamePreferences.DisplayMode` with values `WINDOWED`, `FULLSCREEN`, `BORDERLESS`.

### AudioManager Changes

**File:** `core/src/main/java/com/galacticodyssey/core/AudioManager.java`

- Constructor takes `GamePreferences` and reads initial volumes from `getEffectiveMusicVolume()` / `getEffectiveSfxVolume()`
- Add `setMasterVolume(float)` — stores in preferences, updates currently playing music volume immediately
- `setMusicVolume(float)` / `setSfxVolume(float)` — also recompute effective volume and update currently playing music in real time
- All volume methods compute effective volume as `master * channel` when applying to playback

No mute toggle — master volume at 0 serves the same purpose.

### UiFactory Extensions

**File:** `core/src/main/java/com/galacticodyssey/ui/UiFactory.java`

New widget styles added to `createSkin()`, all using Pixmap-generated drawables (no new texture assets):

**Slider ("default"):**
- Background: dark horizontal bar (~4px tall), same dark fill as button up-state
- Knob: ~16px cyan-bordered square, matching button border color (0, 0.6, 0.8)

**CheckBox ("default"):**
- Unchecked: dark bordered square matching button style
- Checked: same square with cyan fill
- Font: "button" font (22px Orbitron)

**SelectBox ("default"):**
- Background: dark fill with cyan border
- List popup: dark background, cyan highlight on hover
- Font: "button" font

**Label "header" style:**
- Orbitron ~26px, cyan-colored, for tab section headings

**Label "setting" style:**
- Orbitron ~18px, white, for individual setting labels

### SettingsScreen

**File:** `core/src/main/java/com/galacticodyssey/ui/SettingsScreen.java`

Implements `Screen`, same pattern as `MainMenuScreen`. Reuses `StarfieldBackground` for visual consistency.

**Layout:**

```
┌──────────────────────────────────────┐
│        StarfieldBackground           │
│  ┌────────────────────────────────┐  │
│  │  SETTINGS (title)              │  │
│  │                                │  │
│  │  [Display]  [Audio]   (tabs)   │  │
│  │  ┌──────────────────────────┐  │  │
│  │  │                          │  │  │
│  │  │   (active tab content)   │  │  │
│  │  │                          │  │  │
│  │  └──────────────────────────┘  │  │
│  │                                │  │
│  │  [Back]              [Apply]   │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

**Tab switching:** Two `TextButton`s at the top. Active tab has brighter styling. Each tab maps to a `Table`; only the active table is visible. Clicking a tab hides the current content table and shows the selected one.

**Display tab:**

| Setting | Widget | Behavior |
|---------|--------|----------|
| Display Mode | `SelectBox` | Options: Windowed, Fullscreen, Borderless |
| Resolution | `SelectBox` | Populated from `Gdx.graphics.getDisplayModes()`, filtered to unique w×h, sorted descending. Disabled when Fullscreen is selected. |
| VSync | `CheckBox` | Toggle |

**Audio tab:**

| Setting | Widget | Behavior |
|---------|--------|----------|
| Master Volume | `Slider` (0–100%) | Updates audio in real time, percentage label updates live |
| Music Volume | `Slider` (0–100%) | Updates audio in real time, percentage label updates live |
| SFX Volume | `Slider` (0–100%) | Updates audio in real time, percentage label updates live |

**Bottom buttons:**

- **Back** — returns to `MainMenuScreen`. Unsaved display changes are discarded. Audio changes revert to last saved values.
- **Apply** — for audio-only changes, saves preferences immediately. For display changes, applies the new mode and shows a confirmation dialog.

### Display Mode Application

Display changes at runtime use LWJGL3-specific APIs via `Gdx.graphics`:

- **Windowed:** `Gdx.graphics.setUndecorated(false)` (if coming from borderless) + `Gdx.graphics.setWindowedMode(width, height)`
- **Fullscreen:** `Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode())`
- **Borderless:** `Gdx.graphics.setUndecorated(true)` + `Gdx.graphics.setWindowedMode(displayWidth, displayHeight)`

**VSync:** `Gdx.graphics.setVSync(boolean)` — applies immediately, no revert needed.

**Revert timer flow:**

1. User clicks Apply with display changes pending
2. Previous display mode and resolution are cached
3. New mode is applied
4. Confirmation dialog appears with 10-second countdown: "Keep these settings? Reverting in 10s..."
5. [Keep] clicked → `preferences.save()`, done
6. [Revert] clicked or timer expires → restore cached mode, discard changes

### Desktop Launcher Integration

**File:** `desktop/src/main/java/com/galacticodyssey/desktop/DesktopLauncher.java`

Reads `GamePreferences` at boot to configure `Lwjgl3ApplicationConfiguration`:

- Sets initial window mode (windowed/fullscreen/borderless) from saved preference
- Sets initial resolution from saved preference
- Sets vsync from saved preference

Since `Gdx.app` is not available before the application is created, the launcher reads the preferences file directly. The LWJGL3 backend stores preferences as XML files at `<user-home>/.prefs/GalacticOdyssey`. The launcher parses this file using `javax.xml.parsers` to extract display mode, resolution, and vsync values. If the file doesn't exist (first launch), the launcher uses the hardcoded defaults (1280x720 windowed, vsync on).

### MainMenuScreen Integration

**File:** `core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java`

The "Settings" button callback changes from:
```java
() -> Gdx.app.log("Menu", "Settings pressed")
```
to:
```java
() -> game.setScreen(new SettingsScreen(game))
```

### GalacticOdyssey Integration

**File:** `core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java`

- Create `GamePreferences` in `create()` before `AudioManager`
- Pass `GamePreferences` to `AudioManager` constructor
- Add `getPreferences()` accessor so screens can read/write settings
