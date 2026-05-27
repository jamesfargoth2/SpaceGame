# Save/Load UI Design

**Date:** 2026-05-27
**Status:** Approved
**Approach:** Abstract Base + Concrete Screens (Approach A)
**Depends on:** Save/Persistence System (merge from npc-phase2-morale-wages worktree first)

## Requirements

- Main menu "Continue" button loads most recent save directly into GameScreen.
- Main menu "Load Game" button opens a Load screen with all save slots.
- Pause menu gains "Save Game" and "Load Game" buttons.
- Save and Load screens display save slots as horizontal cards with thumbnails.
- Save slots support: primary action (save/load), rename, copy, delete.
- Thumbnail captured at save time; displayed on save/load screens.

---

## 1. Screen Architecture

### Abstract Base: `SaveListBaseScreen`

Shared layout and behavior for both Save and Load screens. Implements `Screen`.

**Layout (top to bottom):**
- Starfield background (reuses `StarfieldBackground` from MainMenuScreen)
- Centered title label ("SAVE GAME" or "LOAD GAME")
- Scrollable vertical list of `SaveSlotPanel` widgets inside a `ScrollPane`
- Manual saves sorted newest-first above an "AUTOSAVES" divider
- Autosave slots grouped below the divider, sorted newest-first
- "BACK" button at the bottom, returns to the originating screen

**Responsibilities:**
- Loads save manifests from `SaveBackend.listSaves()` on `show()`
- Builds the slot list, partitioning manual vs autosave entries
- Handles confirmation dialogs (overwrite, delete, load-with-unsaved-warning)
- Handles rename dialog (text input pre-filled with current name)
- Plays UI hover/click sounds via `AudioManager`

**Abstract methods for subclasses:**
- `getTitle()` — returns the screen title string
- `onSlotClicked(ManifestData manifest)` — primary action when a card body is clicked
- `buildExtraSlots(Table listTable)` — hook for the Save screen to prepend a "New Save" card
- `canRenameAutosaves()` — returns `false`; autosaves are system-named

### `SaveScreen extends SaveListBaseScreen`

Opened from the pause menu only (requires an active GameScreen).

- Title: "SAVE GAME"
- Prepends a dashed "New Save" card at the top of the list via `buildExtraSlots()`
- "New Save" click: saves to a new slot with auto-generated name `"Save #N — <location>"`
- Existing slot click: shows overwrite confirmation → overwrites save
- After save completes: shows "Game Saved" toast (2 seconds, fade out), returns to paused GameScreen
- Captures a thumbnail before saving via `ThumbnailCapture`

### `LoadScreen extends SaveListBaseScreen`

Opened from main menu or pause menu. Tracks its origin to adjust behavior.

- Title: "LOAD GAME"
- No "New Save" card
- Slot click from main menu: loads directly, transitions to GameScreen
- Slot click from pause menu: shows "Unsaved progress will be lost" confirmation → loads, reloads GameScreen
- Back button returns to MainMenuScreen or paused GameScreen depending on origin

---

## 2. Save Slot Card: `SaveSlotPanel`

A reusable Scene2D `Table` widget representing one save slot in the list.

**Layout (horizontal, left to right):**
- **Thumbnail** (160×90): `Image` actor displaying the save's `thumbnail.png` as a Texture. Falls back to a dark placeholder with location text if missing.
- **Info block** (flex):
  - Line 1: Save display name (cyan, Orbitron font) — e.g., "Save #3 — Sol System"
  - Line 2: Location detail (muted) — e.g., "Docked at Haven Station"
  - Line 3: Timestamp, playtime, credits, ship name (dim, small)
- **Action buttons** (right, stacked vertically):
  - Rename (cyan border) — opens rename dialog. Hidden for autosaves.
  - Copy (cyan border) — duplicates the save to a new slot.
  - Delete (red border) — shows delete confirmation.

**Interactions:**
- Hover: background brightens, border glows cyan, subtle scale (1.01×)
- Click on card body: triggers the primary action (delegated to the parent screen)
- Click on action button: triggers the specific action, does NOT trigger primary

**Constructor:** `SaveSlotPanel(ManifestData manifest, Skin skin, boolean isAutosave, SaveSlotListener listener)`

**Listener interface:**
```java
public interface SaveSlotListener {
    void onSlotClicked(ManifestData manifest);
    void onRenameClicked(ManifestData manifest);
    void onCopyClicked(ManifestData manifest);
    void onDeleteClicked(ManifestData manifest);
}
```

---

## 3. Menu Integration

### Main Menu Changes (`MainMenuScreen`)

Current button order: New Game, Continue, Multiplayer, Settings, Encyclopedia, Credits, Exit

New button order:
1. **New Game** — unchanged
2. **Continue** — enabled when saves exist; loads most recent save by timestamp, transitions to GameScreen
3. **Load Game** — new button; opens `LoadScreen(game, origin=MAIN_MENU)`. Disabled if no saves.
4. Multiplayer — unchanged
5. Settings — unchanged
6. Encyclopedia — unchanged
7. Credits — unchanged
8. Exit — unchanged

On `show()`, query `SaveBackend.listSaves()` to determine whether Continue and Load Game should be enabled or disabled.

### Pause Menu Changes (`GameScreen.buildPauseMenu()`)

Current button order: Resume, Settings, Exit to Main Menu, Exit Game

New button order:
1. Resume — unchanged
2. **Save Game** — new; opens `SaveScreen(game, gameScreen)`
3. **Load Game** — new; opens `LoadScreen(game, origin=PAUSE_MENU, gameScreen)`
4. Settings — unchanged
5. Exit to Main Menu — unchanged
6. Exit Game — unchanged

---

## 4. Confirmation Dialogs

All dialogs are modal Scene2D overlays: dark semi-transparent background (70% black), centered panel with the existing sci-fi skin styling.

| Dialog | Message | Confirm Button | Cancel Button |
|--------|---------|----------------|---------------|
| Overwrite | "Overwrite '[name]'? This cannot be undone." | "Overwrite" | "Cancel" |
| Delete | "Delete '[name]'? This cannot be undone." | "Delete" | "Cancel" |
| Load (from pause) | "Load '[name]'? Unsaved progress will be lost." | "Load" | "Cancel" |
| Rename | Text input pre-filled with current name | "Confirm" | "Cancel" |

Implementation: A reusable `ConfirmDialog` Scene2D group added to the screen's stage. For rename, a `RenameDialog` variant with a `TextField`.

---

## 5. Thumbnail Capture

### `ThumbnailCapture` Utility

Captures a downscaled screenshot of the game viewport for embedding in save files.

**Flow:**
1. At save time, `SaveScreen` (or `SaveCoordinator` for autosaves) calls `ThumbnailCapture.capture()`
2. Reads the current framebuffer into a `Pixmap` via `ScreenUtils.getFrameBufferPixmap()`
3. Downscales to 384×216 (16:9, 2× the card display size for sharpness on high-DPI)
4. Writes as PNG bytes via `PixmapIO.writePNG()`
5. Returns the PNG byte array for the `SaveBackend` to store as `thumbnail.png`

**For autosaves:** `SaveCoordinator` captures the thumbnail on the game thread before handing off to the background save thread.

**Loading thumbnails:** `SaveListBaseScreen` loads each save's `thumbnail.png` as a `Texture` on screen show. Uses a small cache (`Map<String, Texture>`) disposed on screen hide. Missing thumbnails fall back to a solid dark placeholder.

---

## 6. ManifestData Additions

The existing `ManifestData` class needs additional fields for display purposes:

```java
// Existing fields
public String saveName;
public long timestampMillis;
public int saveVersion;
public long galaxySeed;
public UUID playerEntityId;
public UUID currentSystemId;

// New fields
public String displayName;       // Player-visible name, editable via Rename
public String locationName;      // e.g., "Sol System"
public String locationDetail;    // e.g., "Docked at Haven Station"
public long playtimeSeconds;     // Total playtime at save time
public long playerCredits;       // Credit balance snapshot
public String shipName;          // Current ship name or class
```

**Auto-generated display name format:** `"Save #<N> — <locationName>"`

The `saveName` field (used as the folder name / save ID) remains the internal identifier. `displayName` is purely cosmetic.

**Save numbering:** The next manual save number is derived from the highest existing manual save number + 1. Parsed from `displayName` prefix, or tracked as a counter in `GamePreferences`.

---

## 7. Navigation Flow

```
MainMenuScreen
  ├── "New Game" → GameScreen (fresh)
  ├── "Continue" → load most recent → GameScreen
  ├── "Load Game" → LoadScreen(MAIN_MENU)
  │     ├── slot click → load → GameScreen
  │     └── "Back" → MainMenuScreen
  └── ...

GameScreen (paused)
  ├── "Save Game" → SaveScreen
  │     ├── "New Save" → save → toast → GameScreen (paused)
  │     ├── slot click → confirm overwrite → save → toast → GameScreen (paused)
  │     └── "Back" → GameScreen (paused)
  ├── "Load Game" → LoadScreen(PAUSE_MENU)
  │     ├── slot click → confirm unsaved warning → load → GameScreen (reloaded)
  │     └── "Back" → GameScreen (paused)
  └── ...
```

---

## 8. New Classes Summary

| Class | Package | Purpose |
|-------|---------|---------|
| `SaveListBaseScreen` | `ui` | Abstract base: starfield, scrollable card list, dialogs |
| `SaveScreen` | `ui` | Save mode: "New Save" card, overwrite, thumbnail capture |
| `LoadScreen` | `ui` | Load mode: main menu or pause menu origin |
| `SaveSlotPanel` | `ui` | Reusable Scene2D widget for one save card |
| `SaveSlotListener` | `ui` | Callback interface for slot actions |
| `ConfirmDialog` | `ui` | Reusable modal confirmation overlay |
| `RenameDialog` | `ui` | Text input variant of ConfirmDialog |
| `ThumbnailCapture` | `persistence` | Framebuffer → downscaled PNG byte array |
| `SaveToast` | `ui` | Brief "Game Saved" notification overlay |

---

## 9. Key Invariants

1. **Save backend is the single source of truth.** Screens always re-query `listSaves()` on show — never cache stale manifest lists across screen transitions.
2. **Thumbnails are display-only.** Missing or corrupted thumbnails never block save/load — fall back to placeholder.
3. **No save operations on the main menu.** The main menu only reads (list + load). Save operations require an active GameScreen.
4. **Confirmation before destructive actions.** Overwrite, delete, and load-from-pause always show a confirmation dialog.
5. **Autosaves cannot be renamed or overwritten.** Only Copy and Delete are available. This prevents players from accidentally losing their safety net.
6. **Screen disposal.** All Textures (thumbnails, overlays), Stages, and StarfieldBackground are disposed on screen hide/dispose. Thumbnail cache is cleared.
