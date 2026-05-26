# Main Menu Design Spec

## Overview

A space-themed main menu for Galactic Odyssey using Scene2D.UI. Features a procedural animated starfield background, center-aligned button stack with sleek sci-fi styling, and ambient audio.

## Architecture

### Screen Management

- `GalacticOdyssey` converts from `ApplicationAdapter` to `Game`
- On `create()`: initializes shared resources (Skin via UiFactory, AudioManager), then calls `setScreen(new MainMenuScreen(this))`
- `MainMenuScreen` implements `Screen` and owns a Scene2D `Stage` with a `StretchViewport`
- Shared resources (Skin, AudioManager) live on the Game instance for cross-screen access

### Class Responsibilities

| Class | Package | Role |
|-------|---------|------|
| `GalacticOdyssey` | root | Game lifecycle, shared resource holder |
| `MainMenuScreen` | `ui` | Screen impl, Stage setup, table layout, input routing |
| `StarfieldBackground` | `ui` | Custom Actor, 3-layer parallax stars + nebula glow |
| `UiFactory` | `ui` | Builds Skin: generates fonts from TTF, creates Pixmap-based button drawables |
| `AudioManager` | `core` | Music + SFX playback, volume control, resource disposal |

## Menu Items

Centered vertical stack, top to bottom:

1. New Game
2. Continue
3. Multiplayer
4. Settings
5. Encyclopedia
6. Credits
7. Exit

All buttons are the same fixed width (~300px at reference resolution) for clean alignment.

## Animated Starfield Background

`StarfieldBackground` is a custom Scene2D Actor rendered behind all UI.

### Three Parallax Layers

| Layer | Star Count | Size | Brightness | Drift Speed |
|-------|-----------|------|-----------|-------------|
| Far | Many (~200) | Small (1-2px) | Dim (0.3-0.6 alpha) | 0.5 px/sec |
| Mid | Medium (~100) | Medium (2-3px) | Moderate (0.5-0.8) | 1.5 px/sec |
| Near | Few (~40) | Large (3-5px) | Bright (0.7-1.0) | 3.0 px/sec |

- Stars generated procedurally at screen creation with a seeded random
- Drift direction: diagonal (upper-left to lower-right)
- Stars wrap seamlessly at screen edges
- Twinkle effect: ~3-5 stars/sec randomly pulse brightness via sine interpolation

### Nebula Glow

- Soft radial gradient using a 1x1 white pixel texture scaled up
- Additive blending with purple/blue color tint
- Slowly drifts and rotates
- No external texture asset required

### Performance

- Star positions stored in flat arrays (x, y, size, brightness per star)
- Updated in `act(float delta)`, drawn in `draw(Batch, float parentAlpha)`
- Zero allocations per frame

## UI Layout

```
┌─────────────────────────────────┐
│                                 │
│                                 │
│       GALACTIC ODYSSEY          │  Title: large font, white
│                                 │
│        ── New Game ──           │  Buttons: vertical stack
│        ── Continue ──           │  ~12px padding between
│       ── Multiplayer ──         │
│        ── Settings ──           │
│       ── Encyclopedia ──        │
│        ── Credits ──            │
│          ── Exit ──             │
│                                 │
│                                 │
│              v0.1.0             │  Version: bottom-right, small dim text
└─────────────────────────────────┘
```

### Viewport

`FitViewport` at 1280x720 reference resolution. Letterboxes on non-16:9 displays rather than distorting content. The starfield fills the full window (drawn outside the viewport bounds) so letterbox bars still show stars.

## Button Styling

Built programmatically via Pixmap-generated NinePatch drawables (no external skin atlas needed).

### States

| State | Background | Border | Text |
|-------|-----------|--------|------|
| Default | Black @ 60% opacity | 1px cyan/blue | White |
| Hover | Black @ 70% opacity | 1px bright cyan | Cyan, scale 102% |
| Press | Black @ 80% opacity | 1px bright cyan pulse | Cyan |

### Hover Animation

- Scale tween to 102% via Scene2D Actions
- Applied on `ClickListener.enter()`, reversed on `exit()`

## Typography

**Font:** Orbitron (Google Fonts, SIL Open Font License) — geometric sans-serif with sci-fi feel.

**Generation:** libGDX `FreeTypeFontGenerator` at runtime from bundled TTF file.

| Usage | Size (at 720p ref) | Color |
|-------|-------------------|-------|
| Title | 48px | White |
| Buttons | 22px | White (default), Cyan (hover) |
| Version | 14px | White @ 50% alpha |

**Dependency:** `gdx-freetype` and `gdx-freetype-platform` added to build.gradle.kts.

## Audio

### AudioManager

- Lives on `GalacticOdyssey` game instance
- Manages `Music` (looping background) and `Sound` (one-shot SFX)
- Exposes `setMusicVolume(float)` and `setSfxVolume(float)`
- Disposes all resources on game shutdown

### Main Menu Sounds

| Sound | Trigger | File |
|-------|---------|------|
| Ambient loop | `MainMenuScreen.show()` | `audio/music/menu_ambient.ogg` |
| Hover tick | `ClickListener.enter()` | `audio/sfx/ui_hover.ogg` |
| Click confirm | `ClickListener.clicked()` | `audio/sfx/ui_click.ogg` |

### Placeholder Assets

All audio files are silent/minimal placeholders so code paths work end-to-end. Real assets can be dropped in later at the same paths.

## File Structure

```
core/src/main/java/com/galacticodyssey/
  GalacticOdyssey.java              (modified: ApplicationAdapter → Game)
  core/
    AudioManager.java               (new)
  ui/
    MainMenuScreen.java             (new)
    StarfieldBackground.java        (new)
    UiFactory.java                  (new)

core/src/main/resources/
  audio/
    music/menu_ambient.ogg          (new, placeholder)
    sfx/ui_hover.ogg                (new, placeholder)
    sfx/ui_click.ogg                (new, placeholder)
  fonts/
    Orbitron-Regular.ttf            (new, SIL OFL licensed)

build.gradle.kts                    (modified: add gdx-freetype dep)
```

## Dependencies Added

```kotlin
// In core module
implementation("com.badlogicgames.gdx:gdx-freetype:${gdxVersion}")

// In desktop module
implementation("com.badlogicgames.gdx:gdx-freetype-platform:${gdxVersion}:natives-desktop")
```

## Button Behavior (Initial)

For this first implementation, buttons log their action to console. No actual game screens exist yet to navigate to.

| Button | Action |
|--------|--------|
| New Game | Log "New Game pressed" |
| Continue | Log "Continue pressed" (disabled/dimmed if no save) |
| Multiplayer | Log "Multiplayer pressed" |
| Settings | Log "Settings pressed" |
| Encyclopedia | Log "Encyclopedia pressed" |
| Credits | Log "Credits pressed" |
| Exit | `Gdx.app.exit()` |

## Design Decisions

- **Programmatic drawables over skin atlas:** Avoids external art dependency. Swap to artist assets later by replacing UiFactory internals.
- **FreeType over pre-rendered .fnt:** Resolution-independent text without regenerating bitmap fonts for each target resolution.
- **FitViewport:** Preserves aspect ratio with letterboxing rather than distorting. Starfield drawn full-window so letterbox areas aren't empty black.
- **AudioManager on Game instance:** Centralizes audio lifecycle. Screens don't manage their own music — they request play/stop through the manager.
- **StarfieldBackground as Actor:** Participates in Scene2D's scene graph. Gets proper `act()`/`draw()` lifecycle. Can be reused on other screens.
