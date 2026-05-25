# Settings Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a tabbed settings screen (Display + Audio) accessible from the main menu, with live audio preview, display mode switching, and a 10-second revert timer for display changes.

**Architecture:** `GamePreferences` wraps libGDX `Preferences` for persistence. `AudioManager` delegates volume storage to `GamePreferences` and applies effective volumes (master × channel) to playback. `UiFactory` is extended with Slider, CheckBox, SelectBox, and new Label styles. `SettingsScreen` is a Scene2D-based screen with tab switching, wired into `MainMenuScreen` via `game.setScreen()`.

**Tech Stack:** libGDX Scene2D.UI, FreeType fonts, libGDX Preferences API, LWJGL3 Graphics API

**Prerequisite:** The main menu plan must be fully applied — `GalacticOdyssey` must extend `Game` (not `ApplicationAdapter`) with `getSkin()`, `getAudioManager()`, and `setScreen()` working. If that conversion hasn't been completed, do it first.

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `core/src/main/java/com/galacticodyssey/core/GamePreferences.java` | Settings persistence model — defaults, clamping, effective volume calculation, load/save via libGDX Preferences |
| Create | `core/src/test/java/com/galacticodyssey/core/GamePreferencesTest.java` | Tests for defaults, clamping, effective volumes |
| Modify | `core/src/main/java/com/galacticodyssey/core/AudioManager.java` | Accept `GamePreferences`, delegate volume storage, add master volume, apply effective volumes |
| Modify | `core/src/test/java/com/galacticodyssey/core/AudioManagerTest.java` | Update to construct with `GamePreferences`, add master volume tests |
| Modify | `core/src/main/java/com/galacticodyssey/ui/UiFactory.java` | Add Slider, CheckBox, SelectBox styles and header/setting Label styles |
| Create | `core/src/main/java/com/galacticodyssey/ui/SettingsScreen.java` | Tabbed settings screen with Display and Audio tabs, Apply/Back buttons, revert timer |
| Modify | `core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java` | Wire Settings button to `game.setScreen(new SettingsScreen(game))` |
| Modify | `core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java` | Create `GamePreferences` in `create()`, pass to `AudioManager`, add `getPreferences()` |
| Modify | `desktop/src/main/java/com/galacticodyssey/desktop/DesktopLauncher.java` | Read preferences XML at boot to configure initial window mode/resolution/vsync |

---

### Task 1: GamePreferences — Defaults, Clamping, Effective Volumes

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/GamePreferences.java`
- Create: `core/src/test/java/com/galacticodyssey/core/GamePreferencesTest.java`

- [ ] **Step 1: Write failing tests for default values**

```java
package com.galacticodyssey.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GamePreferencesTest {

    @Test
    void defaultDisplayModeIsWindowed() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(GamePreferences.DisplayMode.WINDOWED, prefs.getDisplayMode());
    }

    @Test
    void defaultResolutionIs1280x720() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(1280, prefs.getResolutionWidth());
        assertEquals(720, prefs.getResolutionHeight());
    }

    @Test
    void defaultVsyncIsTrue() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(true, prefs.isVsync());
    }

    @Test
    void defaultMasterVolumeIs1() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(1.0f, prefs.getMasterVolume(), 0.001f);
    }

    @Test
    void defaultMusicVolumeIs07() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(0.7f, prefs.getMusicVolume(), 0.001f);
    }

    @Test
    void defaultSfxVolumeIs08() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(0.8f, prefs.getSfxVolume(), 0.001f);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.GamePreferencesTest" --info`
Expected: Compilation failure — `GamePreferences` does not exist yet.

- [ ] **Step 3: Implement GamePreferences with defaults**

```java
package com.galacticodyssey.core;

public class GamePreferences {

    public enum DisplayMode { WINDOWED, FULLSCREEN, BORDERLESS }

    private DisplayMode displayMode = DisplayMode.WINDOWED;
    private int resolutionWidth = 1280;
    private int resolutionHeight = 720;
    private boolean vsync = true;
    private float masterVolume = 1.0f;
    private float musicVolume = 0.7f;
    private float sfxVolume = 0.8f;

    public DisplayMode getDisplayMode() { return displayMode; }
    public void setDisplayMode(DisplayMode displayMode) { this.displayMode = displayMode; }

    public int getResolutionWidth() { return resolutionWidth; }
    public void setResolutionWidth(int width) { this.resolutionWidth = width; }

    public int getResolutionHeight() { return resolutionHeight; }
    public void setResolutionHeight(int height) { this.resolutionHeight = height; }

    public boolean isVsync() { return vsync; }
    public void setVsync(boolean vsync) { this.vsync = vsync; }

    public float getMasterVolume() { return masterVolume; }

    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getMusicVolume() { return musicVolume; }

    public void setMusicVolume(float volume) {
        this.musicVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getSfxVolume() { return sfxVolume; }

    public void setSfxVolume(float volume) {
        this.sfxVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getEffectiveMusicVolume() {
        return masterVolume * musicVolume;
    }

    public float getEffectiveSfxVolume() {
        return masterVolume * sfxVolume;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.GamePreferencesTest" --info`
Expected: All 6 tests PASS.

- [ ] **Step 5: Add clamping and effective volume tests**

Append to `GamePreferencesTest.java`:

```java
    @Test
    void setMasterVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(1.5f);
        assertEquals(1.0f, prefs.getMasterVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(-0.5f);
        assertEquals(0.0f, prefs.getMasterVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMusicVolume(2.0f);
        assertEquals(1.0f, prefs.getMusicVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMusicVolume(-1.0f);
        assertEquals(0.0f, prefs.getMusicVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        prefs.setSfxVolume(3.0f);
        assertEquals(1.0f, prefs.getSfxVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        prefs.setSfxVolume(-0.1f);
        assertEquals(0.0f, prefs.getSfxVolume(), 0.001f);
    }

    @Test
    void effectiveMusicVolumeMultipliesMasterAndMusic() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(0.5f);
        prefs.setMusicVolume(0.6f);
        assertEquals(0.3f, prefs.getEffectiveMusicVolume(), 0.001f);
    }

    @Test
    void effectiveSfxVolumeMultipliesMasterAndSfx() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(0.5f);
        prefs.setSfxVolume(0.4f);
        assertEquals(0.2f, prefs.getEffectiveSfxVolume(), 0.001f);
    }

    @Test
    void effectiveVolumeIsZeroWhenMasterIsZero() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(0.0f);
        assertEquals(0.0f, prefs.getEffectiveMusicVolume(), 0.001f);
        assertEquals(0.0f, prefs.getEffectiveSfxVolume(), 0.001f);
    }
```

- [ ] **Step 6: Run all GamePreferences tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.GamePreferencesTest" --info`
Expected: All 15 tests PASS (the implementation from Step 3 already handles clamping and effective volumes).

- [ ] **Step 7: Add load/save methods**

Add these imports and methods to `GamePreferences.java`:

```java
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
```

Add at the end of the class, before the closing brace:

```java
    private static final String PREFS_NAME = "GalacticOdyssey";

    public void load() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        try {
            displayMode = DisplayMode.valueOf(prefs.getString("displayMode", "WINDOWED"));
        } catch (IllegalArgumentException e) {
            displayMode = DisplayMode.WINDOWED;
        }
        resolutionWidth = prefs.getInteger("resolutionWidth", 1280);
        resolutionHeight = prefs.getInteger("resolutionHeight", 720);
        vsync = prefs.getBoolean("vsync", true);
        masterVolume = Math.max(0f, Math.min(1f, prefs.getFloat("masterVolume", 1.0f)));
        musicVolume = Math.max(0f, Math.min(1f, prefs.getFloat("musicVolume", 0.7f)));
        sfxVolume = Math.max(0f, Math.min(1f, prefs.getFloat("sfxVolume", 0.8f)));
    }

    public void save() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.putString("displayMode", displayMode.name());
        prefs.putInteger("resolutionWidth", resolutionWidth);
        prefs.putInteger("resolutionHeight", resolutionHeight);
        prefs.putBoolean("vsync", vsync);
        prefs.putFloat("masterVolume", masterVolume);
        prefs.putFloat("musicVolume", musicVolume);
        prefs.putFloat("sfxVolume", sfxVolume);
        prefs.flush();
    }
```

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GamePreferences.java core/src/test/java/com/galacticodyssey/core/GamePreferencesTest.java
git commit -m "feat: add GamePreferences with defaults, clamping, effective volumes, and persistence"
```

---

### Task 2: AudioManager — Integrate with GamePreferences

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/AudioManager.java`
- Modify: `core/src/test/java/com/galacticodyssey/core/AudioManagerTest.java`

- [ ] **Step 1: Update AudioManager tests to use GamePreferences**

Replace the entire contents of `AudioManagerTest.java`:

```java
package com.galacticodyssey.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioManagerTest {

    @Test
    void defaultMusicVolumeMatchesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        assertEquals(0.7f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void defaultSfxVolumeMatchesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        assertEquals(0.8f, audio.getSfxVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMusicVolume(1.5f);
        assertEquals(1.0f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMusicVolume(-0.5f);
        assertEquals(0.0f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeAcceptsValidValue() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMusicVolume(0.4f);
        assertEquals(0.4f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setSfxVolume(2.0f);
        assertEquals(1.0f, audio.getSfxVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setSfxVolume(-1.0f);
        assertEquals(0.0f, audio.getSfxVolume(), 0.001f);
    }

    @Test
    void masterVolumeDefaultsToOne() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        assertEquals(1.0f, audio.getMasterVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMasterVolume(1.5f);
        assertEquals(1.0f, audio.getMasterVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMasterVolume(-0.5f);
        assertEquals(0.0f, audio.getMasterVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeUpdatesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMasterVolume(0.5f);
        assertEquals(0.5f, prefs.getMasterVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeUpdatesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMusicVolume(0.3f);
        assertEquals(0.3f, prefs.getMusicVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeUpdatesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setSfxVolume(0.6f);
        assertEquals(0.6f, prefs.getSfxVolume(), 0.001f);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.AudioManagerTest" --info`
Expected: Compilation failure — `AudioManager` has no constructor that takes `GamePreferences`.

- [ ] **Step 3: Refactor AudioManager to use GamePreferences**

Replace the entire contents of `AudioManager.java`:

```java
package com.galacticodyssey.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

public class AudioManager implements Disposable {

    private final GamePreferences preferences;
    private Music currentMusic;
    private final ObjectMap<String, Sound> soundCache = new ObjectMap<>();

    public AudioManager(GamePreferences preferences) {
        this.preferences = preferences;
    }

    public void playMusic(String path) {
        stopMusic();
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            Gdx.app.log("AudioManager", "Music file not found: " + path);
            return;
        }
        currentMusic = Gdx.audio.newMusic(file);
        currentMusic.setLooping(true);
        currentMusic.setVolume(preferences.getEffectiveMusicVolume());
        currentMusic.play();
    }

    public void stopMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.dispose();
            currentMusic = null;
        }
    }

    public void playSound(String path) {
        Sound sound = soundCache.get(path);
        if (sound == null) {
            FileHandle file = Gdx.files.internal(path);
            if (!file.exists()) {
                return;
            }
            sound = Gdx.audio.newSound(file);
            soundCache.put(path, sound);
        }
        sound.play(preferences.getEffectiveSfxVolume());
    }

    public void setMasterVolume(float volume) {
        preferences.setMasterVolume(volume);
        applyMusicVolume();
    }

    public void setMusicVolume(float volume) {
        preferences.setMusicVolume(volume);
        applyMusicVolume();
    }

    public void setSfxVolume(float volume) {
        preferences.setSfxVolume(volume);
    }

    public float getMasterVolume() {
        return preferences.getMasterVolume();
    }

    public float getMusicVolume() {
        return preferences.getMusicVolume();
    }

    public float getSfxVolume() {
        return preferences.getSfxVolume();
    }

    private void applyMusicVolume() {
        if (currentMusic != null) {
            currentMusic.setVolume(preferences.getEffectiveMusicVolume());
        }
    }

    @Override
    public void dispose() {
        stopMusic();
        for (Sound sound : soundCache.values()) {
            sound.dispose();
        }
        soundCache.clear();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.AudioManagerTest" --info`
Expected: All 13 tests PASS.

- [ ] **Step 5: Run all core tests to check for regressions**

Run: `./gradlew :core:test --info`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/AudioManager.java core/src/test/java/com/galacticodyssey/core/AudioManagerTest.java
git commit -m "refactor: AudioManager delegates volume storage to GamePreferences, adds master volume"
```

---

### Task 3: UiFactory — Add Slider, CheckBox, SelectBox, and Label Styles

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/UiFactory.java`

No unit tests for this task — these are visual widget styles that require a GL context. Verified visually in Task 6.

- [ ] **Step 1: Add new imports to UiFactory**

Add these imports at the top of `UiFactory.java`:

```java
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
```

- [ ] **Step 2: Add new font generation inside `createSkin()`**

After the `versionFont` generation (and before `generator.dispose()`), add:

```java
        FreeTypeFontParameter headerParam = new FreeTypeFontParameter();
        headerParam.size = 26;
        headerParam.color = CYAN;
        BitmapFont headerFont = generator.generateFont(headerParam);
        skin.add("header", headerFont);

        FreeTypeFontParameter settingParam = new FreeTypeFontParameter();
        settingParam.size = 18;
        settingParam.color = Color.WHITE;
        BitmapFont settingFont = generator.generateFont(settingParam);
        skin.add("setting", settingFont);
```

- [ ] **Step 3: Add Label styles for "header" and "setting" inside `createSkin()`**

After the existing `versionStyle` block (before the `return skin;`), add:

```java
        Label.LabelStyle headerStyle = new Label.LabelStyle();
        headerStyle.font = headerFont;
        headerStyle.fontColor = CYAN.cpy();
        skin.add("header", headerStyle);

        Label.LabelStyle settingStyle = new Label.LabelStyle();
        settingStyle.font = settingFont;
        settingStyle.fontColor = Color.WHITE;
        skin.add("setting", settingStyle);
```

- [ ] **Step 4: Add Slider style inside `createSkin()`**

After the Label styles added in Step 3, add:

```java
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = createSliderBackground();
        sliderStyle.knob = createSliderKnob();
        skin.add("default", sliderStyle);
```

- [ ] **Step 5: Add `createSliderBackground()` and `createSliderKnob()` helper methods**

Add these methods to the `UiFactory` class:

```java
    private static Drawable createSliderBackground() {
        Pixmap pixmap = new Pixmap(20, 4, Pixmap.Format.RGBA8888);
        pixmap.setColor(BG_DEFAULT);
        pixmap.fill();
        pixmap.setColor(BORDER_DEFAULT);
        pixmap.drawRectangle(0, 0, 20, 4);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        NinePatch ninePatch = new NinePatch(new TextureRegion(texture), 2, 2, 0, 0);
        NinePatchDrawable drawable = new NinePatchDrawable(ninePatch);
        drawable.setMinHeight(4);
        return drawable;
    }

    private static Drawable createSliderKnob() {
        int knobSize = 16;
        Pixmap pixmap = new Pixmap(knobSize, knobSize, Pixmap.Format.RGBA8888);
        pixmap.setColor(BORDER_DEFAULT);
        pixmap.fill();
        pixmap.setColor(BG_DEFAULT);
        pixmap.fillRectangle(2, 2, knobSize - 4, knobSize - 4);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }
```

- [ ] **Step 6: Add CheckBox style inside `createSkin()`**

After the Slider style, add:

```java
        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOff = createCheckboxDrawable(false);
        checkBoxStyle.checkboxOn = createCheckboxDrawable(true);
        checkBoxStyle.font = settingFont;
        checkBoxStyle.fontColor = Color.WHITE;
        skin.add("default", checkBoxStyle);
```

- [ ] **Step 7: Add `createCheckboxDrawable()` helper method**

Add this method to the `UiFactory` class:

```java
    private static Drawable createCheckboxDrawable(boolean checked) {
        int size = 20;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(BORDER_DEFAULT);
        pixmap.fill();
        if (checked) {
            pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.8f));
            pixmap.fillRectangle(2, 2, size - 4, size - 4);
        } else {
            pixmap.setColor(BG_DEFAULT);
            pixmap.fillRectangle(2, 2, size - 4, size - 4);
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }
```

- [ ] **Step 8: Add SelectBox and List styles inside `createSkin()`**

After the CheckBox style, add:

```java
        NinePatchDrawable selectBg = createButtonDrawable(BG_DEFAULT, BORDER_DEFAULT);
        NinePatchDrawable selectBgOver = createButtonDrawable(BG_HOVER, CYAN);

        List.ListStyle listStyle = new List.ListStyle();
        listStyle.font = settingFont;
        listStyle.fontColorSelected = CYAN.cpy();
        listStyle.fontColorUnselected = Color.WHITE;
        listStyle.selection = createButtonDrawable(new Color(0f, 0.3f, 0.4f, 0.8f), CYAN);
        listStyle.over = createButtonDrawable(BG_HOVER, BORDER_DEFAULT);
        listStyle.background = createButtonDrawable(BG_PRESS, BORDER_DEFAULT);
        skin.add("default", listStyle);

        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        skin.add("default", scrollPaneStyle);

        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle();
        selectBoxStyle.font = settingFont;
        selectBoxStyle.fontColor = Color.WHITE;
        selectBoxStyle.overFontColor = CYAN.cpy();
        selectBoxStyle.background = selectBg;
        selectBoxStyle.backgroundOver = selectBgOver;
        selectBoxStyle.listStyle = listStyle;
        selectBoxStyle.scrollStyle = scrollPaneStyle;
        skin.add("default", selectBoxStyle);
```

- [ ] **Step 9: Verify build compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/UiFactory.java
git commit -m "feat: add Slider, CheckBox, SelectBox, and header/setting Label styles to UiFactory"
```

---

### Task 4: SettingsScreen — Scaffold with Tabs

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/SettingsScreen.java`

- [ ] **Step 1: Create the SettingsScreen skeleton with tabbed layout**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.core.GamePreferences;

public class SettingsScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private final GalacticOdyssey game;
    private final Stage stage;
    private final StarfieldBackground starfield;
    private final OrthographicCamera backgroundCamera;
    private final GamePreferences preferences;
    private final AudioManager audio;

    private Table displayTable;
    private Table audioTable;
    private TextButton displayTabBtn;
    private TextButton audioTabBtn;

    private SelectBox<String> displayModeBox;
    private SelectBox<String> resolutionBox;
    private CheckBox vsyncCheckBox;
    private final Array<int[]> resolutions = new Array<>();

    private Slider masterSlider;
    private Slider musicSlider;
    private Slider sfxSlider;
    private Label masterValueLabel;
    private Label musicValueLabel;
    private Label sfxValueLabel;

    private GamePreferences.DisplayMode savedDisplayMode;
    private int savedResolutionWidth;
    private int savedResolutionHeight;
    private boolean savedVsync;
    private float savedMasterVolume;
    private float savedMusicVolume;
    private float savedSfxVolume;

    public SettingsScreen(GalacticOdyssey game) {
        this.game = game;
        this.preferences = game.getPreferences();
        this.audio = game.getAudioManager();
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.backgroundCamera = new OrthographicCamera();

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        this.starfield = new StarfieldBackground(screenW, screenH);

        snapshotSavedState();
        buildUi();
    }

    private void snapshotSavedState() {
        savedDisplayMode = preferences.getDisplayMode();
        savedResolutionWidth = preferences.getResolutionWidth();
        savedResolutionHeight = preferences.getResolutionHeight();
        savedVsync = preferences.isVsync();
        savedMasterVolume = preferences.getMasterVolume();
        savedMusicVolume = preferences.getMusicVolume();
        savedSfxVolume = preferences.getSfxVolume();
    }

    private void buildUi() {
        Skin skin = game.getSkin();

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(30);

        Label title = new Label("SETTINGS", skin, "title");
        root.add(title).padBottom(30).row();

        Table tabRow = new Table();
        displayTabBtn = new TextButton("Display", skin);
        audioTabBtn = new TextButton("Audio", skin);
        tabRow.add(displayTabBtn).width(200).height(45).padRight(10);
        tabRow.add(audioTabBtn).width(200).height(45);
        root.add(tabRow).padBottom(20).row();

        displayTable = buildDisplayTab(skin);
        audioTable = buildAudioTab(skin);

        Stack contentStack = new Stack();
        contentStack.add(displayTable);
        contentStack.add(audioTable);
        audioTable.setVisible(false);

        root.add(contentStack).width(700).height(300).row();

        Table bottomRow = new Table();
        TextButton backBtn = new TextButton("Back", skin);
        TextButton applyBtn = new TextButton("Apply", skin);

        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                revertUnsavedChanges();
                game.setScreen(new MainMenuScreen(game));
            }
        });

        applyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                applySettings();
            }
        });

        bottomRow.add(backBtn).width(200).height(50).expandX().left();
        bottomRow.add(applyBtn).width(200).height(50).expandX().right();
        root.add(bottomRow).width(700).padTop(30).row();

        stage.addActor(root);

        setupTabListeners(skin);
        selectTab(true);
    }

    private void setupTabListeners(Skin skin) {
        displayTabBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                selectTab(true);
            }
        });

        audioTabBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                selectTab(false);
            }
        });
    }

    private void selectTab(boolean displayActive) {
        displayTable.setVisible(displayActive);
        audioTable.setVisible(!displayActive);
        displayTabBtn.setDisabled(displayActive);
        audioTabBtn.setDisabled(!displayActive);
    }

    private Table buildDisplayTab(Skin skin) {
        Table table = new Table();
        table.top().padTop(20);

        table.add(new Label("Display Mode", skin, "setting")).left().padRight(20);
        displayModeBox = new SelectBox<>(skin);
        displayModeBox.setItems("Windowed", "Fullscreen", "Borderless");
        displayModeBox.setSelectedIndex(preferences.getDisplayMode().ordinal());
        table.add(displayModeBox).width(250).height(35).left().row();

        table.add().height(15).row();

        table.add(new Label("Resolution", skin, "setting")).left().padRight(20);
        resolutionBox = new SelectBox<>(skin);
        populateResolutions();
        resolutionBox.setDisabled(preferences.getDisplayMode() == GamePreferences.DisplayMode.FULLSCREEN);
        table.add(resolutionBox).width(250).height(35).left().row();

        table.add().height(15).row();

        table.add(new Label("VSync", skin, "setting")).left().padRight(20);
        vsyncCheckBox = new CheckBox("", skin);
        vsyncCheckBox.setChecked(preferences.isVsync());
        table.add(vsyncCheckBox).left().row();

        displayModeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean isFullscreen = displayModeBox.getSelectedIndex() == 1;
                resolutionBox.setDisabled(isFullscreen);
            }
        });

        return table;
    }

    private void populateResolutions() {
        resolutions.clear();
        Graphics.DisplayMode[] modes = Gdx.graphics.getDisplayModes();
        Array<String> seen = new Array<>();
        Array<String> items = new Array<>();

        for (Graphics.DisplayMode mode : modes) {
            String key = mode.width + "x" + mode.height;
            if (!seen.contains(key, false)) {
                seen.add(key);
                resolutions.add(new int[]{mode.width, mode.height});
                items.add(mode.width + " x " + mode.height);
            }
        }

        resolutions.sort((a, b) -> b[0] != a[0] ? b[0] - a[0] : b[1] - a[1]);
        items.clear();
        for (int[] res : resolutions) {
            items.add(res[0] + " x " + res[1]);
        }

        resolutionBox.setItems(items);

        String currentRes = preferences.getResolutionWidth() + " x " + preferences.getResolutionHeight();
        int idx = items.indexOf(currentRes, false);
        if (idx >= 0) {
            resolutionBox.setSelectedIndex(idx);
        }
    }

    private Table buildAudioTab(Skin skin) {
        Table table = new Table();
        table.top().padTop(20);

        masterSlider = new Slider(0f, 1f, 0.01f, false, skin);
        masterSlider.setValue(preferences.getMasterVolume());
        masterValueLabel = new Label(toPercent(preferences.getMasterVolume()), skin, "setting");
        addVolumeRow(table, skin, "Master Volume", masterSlider, masterValueLabel);

        musicSlider = new Slider(0f, 1f, 0.01f, false, skin);
        musicSlider.setValue(preferences.getMusicVolume());
        musicValueLabel = new Label(toPercent(preferences.getMusicVolume()), skin, "setting");
        addVolumeRow(table, skin, "Music Volume", musicSlider, musicValueLabel);

        sfxSlider = new Slider(0f, 1f, 0.01f, false, skin);
        sfxSlider.setValue(preferences.getSfxVolume());
        sfxValueLabel = new Label(toPercent(preferences.getSfxVolume()), skin, "setting");
        addVolumeRow(table, skin, "SFX Volume", sfxSlider, sfxValueLabel);

        masterSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.setMasterVolume(masterSlider.getValue());
                masterValueLabel.setText(toPercent(masterSlider.getValue()));
            }
        });

        musicSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.setMusicVolume(musicSlider.getValue());
                musicValueLabel.setText(toPercent(musicSlider.getValue()));
            }
        });

        sfxSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.setSfxVolume(sfxSlider.getValue());
                sfxValueLabel.setText(toPercent(sfxSlider.getValue()));
            }
        });

        return table;
    }

    private void addVolumeRow(Table table, Skin skin, String label, Slider slider, Label valueLabel) {
        table.add(new Label(label, skin, "setting")).left().width(200);
        table.add(slider).width(300).padLeft(10).padRight(10);
        table.add(valueLabel).width(60).right();
        table.row().padTop(15);
    }

    private String toPercent(float value) {
        return Math.round(value * 100) + "%";
    }

    private void applySettings() {
        GamePreferences.DisplayMode selectedMode =
            GamePreferences.DisplayMode.values()[displayModeBox.getSelectedIndex()];
        int selectedWidth = preferences.getResolutionWidth();
        int selectedHeight = preferences.getResolutionHeight();
        if (resolutionBox.getSelectedIndex() >= 0 && resolutionBox.getSelectedIndex() < resolutions.size) {
            int[] res = resolutions.get(resolutionBox.getSelectedIndex());
            selectedWidth = res[0];
            selectedHeight = res[1];
        }
        boolean selectedVsync = vsyncCheckBox.isChecked();

        boolean displayChanged =
            selectedMode != savedDisplayMode ||
            selectedWidth != savedResolutionWidth ||
            selectedHeight != savedResolutionHeight;

        preferences.setDisplayMode(selectedMode);
        preferences.setResolutionWidth(selectedWidth);
        preferences.setResolutionHeight(selectedHeight);
        preferences.setVsync(selectedVsync);

        Gdx.graphics.setVSync(selectedVsync);

        if (displayChanged) {
            applyDisplayMode(selectedMode, selectedWidth, selectedHeight);
            showRevertDialog();
        } else {
            preferences.save();
            snapshotSavedState();
        }
    }

    private void applyDisplayMode(GamePreferences.DisplayMode mode, int width, int height) {
        switch (mode) {
            case WINDOWED:
                Gdx.graphics.setUndecorated(false);
                Gdx.graphics.setWindowedMode(width, height);
                break;
            case FULLSCREEN:
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                break;
            case BORDERLESS:
                Graphics.DisplayMode desktop = Gdx.graphics.getDisplayMode();
                Gdx.graphics.setUndecorated(true);
                Gdx.graphics.setWindowedMode(desktop.width, desktop.height);
                break;
        }
    }

    private float revertTimer;
    private Table revertDialogTable;
    private Label revertCountdownLabel;
    private boolean revertPending;

    private void showRevertDialog() {
        revertPending = true;
        revertTimer = 10f;
        Skin skin = game.getSkin();

        revertDialogTable = new Table();
        revertDialogTable.setFillParent(true);
        revertDialogTable.center();

        Table dialogContent = new Table();
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(new Color(0f, 0f, 0f, 0.85f));
        bgPixmap.fill();
        dialogContent.setBackground(new TextureRegionDrawable(
            new TextureRegion(new Texture(bgPixmap))));
        bgPixmap.dispose();
        dialogContent.pad(30);

        revertCountdownLabel = new Label("Keep these settings?\nReverting in 10s...", skin, "setting");
        revertCountdownLabel.setAlignment(Align.center);
        dialogContent.add(revertCountdownLabel).padBottom(20).row();

        Table dialogButtons = new Table();
        TextButton keepBtn = new TextButton("Keep", skin);
        TextButton revertBtn = new TextButton("Revert", skin);

        keepBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                confirmDisplayChange();
            }
        });

        revertBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                revertDisplayChange();
            }
        });

        dialogButtons.add(keepBtn).width(150).height(45).padRight(20);
        dialogButtons.add(revertBtn).width(150).height(45);
        dialogContent.add(dialogButtons);

        revertDialogTable.add(dialogContent);
        stage.addActor(revertDialogTable);
    }

    private void confirmDisplayChange() {
        revertPending = false;
        if (revertDialogTable != null) {
            revertDialogTable.remove();
            revertDialogTable = null;
        }
        preferences.save();
        snapshotSavedState();
    }

    private void revertDisplayChange() {
        revertPending = false;
        if (revertDialogTable != null) {
            revertDialogTable.remove();
            revertDialogTable = null;
        }
        preferences.setDisplayMode(savedDisplayMode);
        preferences.setResolutionWidth(savedResolutionWidth);
        preferences.setResolutionHeight(savedResolutionHeight);
        applyDisplayMode(savedDisplayMode, savedResolutionWidth, savedResolutionHeight);

        displayModeBox.setSelectedIndex(savedDisplayMode.ordinal());
        String savedRes = savedResolutionWidth + " x " + savedResolutionHeight;
        Array<String> items = new Array<>();
        for (int[] r : resolutions) items.add(r[0] + " x " + r[1]);
        int idx = items.indexOf(savedRes, false);
        if (idx >= 0) resolutionBox.setSelectedIndex(idx);
    }

    private void revertUnsavedChanges() {
        preferences.setMasterVolume(savedMasterVolume);
        preferences.setMusicVolume(savedMusicVolume);
        preferences.setSfxVolume(savedSfxVolume);
        audio.setMasterVolume(savedMasterVolume);
        audio.setMusicVolume(savedMusicVolume);
        audio.setSfxVolume(savedSfxVolume);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        starfield.update(delta);

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        backgroundCamera.setToOrtho(false, screenW, screenH);

        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(backgroundCamera.combined);
        batch.begin();
        starfield.render(batch);
        batch.end();

        if (revertPending) {
            revertTimer -= delta;
            int remaining = Math.max(0, (int) Math.ceil(revertTimer));
            revertCountdownLabel.setText("Keep these settings?\nReverting in " + remaining + "s...");
            if (revertTimer <= 0) {
                revertDisplayChange();
            }
        }

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        starfield.resize(width, height);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        stage.dispose();
        starfield.dispose();
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL. (If `GalacticOdyssey` doesn't have `getPreferences()` yet, this step may need Task 5 first — in that case, verify after Task 5.)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/SettingsScreen.java
git commit -m "feat: add SettingsScreen with tabbed Display/Audio layout, live audio preview, and display revert timer"
```

---

### Task 5: Integration — GalacticOdyssey and MainMenuScreen

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java`

- [ ] **Step 1: Add GamePreferences to GalacticOdyssey**

In `GalacticOdyssey.java`, add a field:

```java
    private GamePreferences preferences;
```

In the `create()` method, add these lines before the `audioManager = new AudioManager();` line:

```java
        preferences = new GamePreferences();
        preferences.load();
```

Change the AudioManager construction from:

```java
        audioManager = new AudioManager();
```

to:

```java
        audioManager = new AudioManager(preferences);
```

Add the accessor method:

```java
    public GamePreferences getPreferences() {
        return preferences;
    }
```

- [ ] **Step 2: Wire the Settings button in MainMenuScreen**

In `MainMenuScreen.java`, add this import:

```java
import com.galacticodyssey.ui.SettingsScreen;
```

Change the Settings button callback from:

```java
        addMenuButton(root, "Settings", skin, false,
            () -> Gdx.app.log("Menu", "Settings pressed"));
```

to:

```java
        addMenuButton(root, "Settings", skin, false,
            () -> game.setScreen(new SettingsScreen(game)));
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all tests**

Run: `./gradlew :core:test --info`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java
git commit -m "feat: wire SettingsScreen into GalacticOdyssey and MainMenuScreen"
```

---

### Task 6: Desktop Launcher — Read Preferences on Boot

**Files:**
- Modify: `desktop/src/main/java/com/galacticodyssey/desktop/DesktopLauncher.java`

- [ ] **Step 1: Update DesktopLauncher to read preferences at boot**

Replace the entire contents of `DesktopLauncher.java`:

```java
package com.galacticodyssey.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.galacticodyssey.core.GalacticOdyssey;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class DesktopLauncher {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Galactic Odyssey");
        config.setForegroundFPS(60);

        applyDisplayPreferences(config);

        new Lwjgl3Application(new GalacticOdyssey(), config);
    }

    private static void applyDisplayPreferences(Lwjgl3ApplicationConfiguration config) {
        Properties props = loadPreferencesFile();
        if (props == null) {
            config.setWindowedMode(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            config.useVsync(true);
            return;
        }

        String displayMode = props.getProperty("displayMode", "WINDOWED");
        int width = getIntProperty(props, "resolutionWidth", DEFAULT_WIDTH);
        int height = getIntProperty(props, "resolutionHeight", DEFAULT_HEIGHT);
        boolean vsync = Boolean.parseBoolean(props.getProperty("vsync", "true"));

        config.useVsync(vsync);

        switch (displayMode) {
            case "FULLSCREEN":
                config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
                break;
            case "BORDERLESS":
                config.setDecorated(false);
                var desktop = Lwjgl3ApplicationConfiguration.getDisplayMode();
                config.setWindowedMode(desktop.width, desktop.height);
                break;
            default:
                config.setWindowedMode(width, height);
                break;
        }
    }

    private static Properties loadPreferencesFile() {
        String userHome = System.getProperty("user.home");
        File prefsFile = new File(userHome, ".prefs" + File.separator + "GalacticOdyssey");
        if (!prefsFile.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(prefsFile)) {
            Properties props = new Properties();
            props.loadFromXML(fis);
            return props;
        } catch (Exception e) {
            return null;
        }
    }

    private static int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :desktop:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/java/com/galacticodyssey/desktop/DesktopLauncher.java
git commit -m "feat: DesktopLauncher reads saved preferences to configure initial window mode and resolution"
```

---

### Task 7: Manual Verification

- [ ] **Step 1: Run the game**

Run: `./gradlew :desktop:run`

- [ ] **Step 2: Verify main menu shows Settings button**

Click "Settings" — the settings screen should appear with the starfield background, "SETTINGS" title, Display/Audio tabs, and Back/Apply buttons.

- [ ] **Step 3: Verify Display tab**

- Display Mode dropdown should show Windowed/Fullscreen/Borderless
- Resolution dropdown should list available monitor resolutions
- Selecting Fullscreen should disable the Resolution dropdown
- VSync checkbox should be checked by default

- [ ] **Step 4: Verify Audio tab**

- Click the "Audio" tab — three volume sliders should appear
- Master Volume should be at 100%, Music at 70%, SFX at 80%
- Dragging the Music slider should change the background music volume in real time
- Dragging the Master slider should also affect the music volume
- Percentage labels should update as you drag

- [ ] **Step 5: Verify Apply + revert timer for display changes**

- Change Display Mode to Fullscreen and click Apply
- A dialog should appear: "Keep these settings? Reverting in 10s..."
- Click "Revert" — the window should return to its previous state
- Test again and let the timer expire — should also revert

- [ ] **Step 6: Verify Back button discards unsaved changes**

- Change a volume slider, then click Back without clicking Apply
- Re-enter Settings — the slider should be at its original saved value

- [ ] **Step 7: Verify persistence across restarts**

- Change some audio settings and click Apply
- Quit the game, restart it
- Re-enter Settings — the saved values should be restored
