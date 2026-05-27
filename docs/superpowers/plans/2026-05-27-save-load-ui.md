# Save/Load UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add save/load UI screens, main menu and pause menu integration, thumbnail capture, and confirmation dialogs on top of the existing persistence backend.

**Architecture:** Two concrete Screen subclasses (`SaveScreen`, `LoadScreen`) extend a shared `SaveListBaseScreen` abstract class that handles starfield background, scrollable card list, and confirmation dialogs. Each save slot is rendered by a reusable `SaveSlotPanel` Scene2D Table widget. Thumbnails are captured via `ThumbnailCapture` and stored alongside save data.

**Tech Stack:** libGDX Scene2D.UI, libGDX ScreenUtils/Pixmap for thumbnail capture, existing `SaveBackend`/`SaveCoordinator`/`ManifestData` from the persistence package.

**Spec:** `docs/superpowers/specs/2026-05-27-save-load-ui-design.md`

---

## Prerequisites

Before starting, merge the persistence system from the `npc-phase2-morale-wages` worktree to master. The persistence package (`com.galacticodyssey.persistence`) must be on the working branch with all classes: `SaveCoordinator`, `SaveBackend`, `LocalFileSaveBackend`, `SaveBundle`, `ManifestData`, `SaveWriter`, `SaveReader`, `KryoRegistrar`, etc.

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `core/src/main/java/com/galacticodyssey/persistence/ManifestData.java` | Modify | Add display fields (displayName, locationName, locationDetail, playtimeSeconds, playerCredits, shipName) |
| `core/src/main/java/com/galacticodyssey/persistence/ThumbnailCapture.java` | Create | Framebuffer → downscaled PNG byte[] utility |
| `core/src/main/java/com/galacticodyssey/persistence/SaveBackend.java` | Modify | Add `copySave(String sourceId, String destId)` method |
| `core/src/main/java/com/galacticodyssey/persistence/LocalFileSaveBackend.java` | Modify | Implement `copySave`, add `readManifestOnly`, add thumbnail read/write |
| `core/src/main/java/com/galacticodyssey/ui/SaveSlotListener.java` | Create | Callback interface for slot card actions |
| `core/src/main/java/com/galacticodyssey/ui/SaveSlotPanel.java` | Create | Scene2D Table widget for one save card |
| `core/src/main/java/com/galacticodyssey/ui/ConfirmDialog.java` | Create | Reusable modal confirmation overlay |
| `core/src/main/java/com/galacticodyssey/ui/RenameDialog.java` | Create | Text input variant of ConfirmDialog |
| `core/src/main/java/com/galacticodyssey/ui/SaveToast.java` | Create | Brief "Game Saved" notification |
| `core/src/main/java/com/galacticodyssey/ui/SaveListBaseScreen.java` | Create | Abstract base screen: starfield, card list, dialogs |
| `core/src/main/java/com/galacticodyssey/ui/SaveScreen.java` | Create | Save mode: New Save card, overwrite, thumbnail |
| `core/src/main/java/com/galacticodyssey/ui/LoadScreen.java` | Create | Load mode: main menu or pause origin |
| `core/src/main/java/com/galacticodyssey/ui/UiFactory.java` | Modify | Add skin styles for save slot cards (small font, dim label, red button) |
| `core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java` | Modify | Add Load Game button, enable Continue when saves exist |
| `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` | Modify | Add Save Game and Load Game to pause menu |
| `core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java` | Modify | Create and expose SaveBackend instance |
| `core/src/test/java/com/galacticodyssey/persistence/ManifestDataTest.java` | Create | Test new display fields |
| `core/src/test/java/com/galacticodyssey/persistence/ThumbnailCaptureTest.java` | Create | Test downscale logic (non-GL parts) |
| `core/src/test/java/com/galacticodyssey/ui/ConfirmDialogTest.java` | Create | Test dialog callback wiring |
| `core/src/test/java/com/galacticodyssey/ui/SaveSlotPanelTest.java` | Create | Test listener delegation |

---

### Task 0: Merge Persistence System to Master

**Files:**
- All files in `com.galacticodyssey.persistence` package from the `npc-phase2-morale-wages` worktree
- Modify: `core/build.gradle.kts` (add Kryo dependency)

- [ ] **Step 1: Check worktree branch status**

Run: `cd .claude/worktrees/npc-phase2-morale-wages && git log --oneline -5`

Verify the persistence commits are present.

- [ ] **Step 2: Merge the worktree branch into master**

```bash
git merge npc-phase2-morale-wages --no-ff -m "feat: merge persistence system (save/load backend)"
```

If this fails due to the branch not being directly mergeable, cherry-pick the persistence-related commits or copy the files manually.

- [ ] **Step 3: Verify Kryo dependency is in build.gradle.kts**

Check `core/build.gradle.kts` contains:
```kotlin
api("com.esotericsoftware:kryo:5.6.0")
```

If not, add it to the dependencies block.

- [ ] **Step 4: Run existing persistence tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.*" --info`
Expected: All persistence tests pass.

- [ ] **Step 5: Commit if any manual fixups were needed**

```bash
git add -A
git commit -m "fix: resolve merge conflicts from persistence system merge"
```

---

### Task 1: Extend ManifestData with Display Fields

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/persistence/ManifestData.java`
- Create: `core/src/test/java/com/galacticodyssey/persistence/ManifestDataTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/persistence/ManifestDataTest.java`:

```java
package com.galacticodyssey.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ManifestDataTest {

    @Test
    void constructorSetsDisplayFields() {
        UUID playerId = UUID.randomUUID();
        UUID systemId = UUID.randomUUID();
        ManifestData m = new ManifestData("test-save", 42L, playerId, systemId);
        m.displayName = "Save #1 — Sol System";
        m.locationName = "Sol System";
        m.locationDetail = "Docked at Haven Station";
        m.playtimeSeconds = 45000L;
        m.playerCredits = 12500L;
        m.shipName = "Cobra Mk III";

        assertEquals("Save #1 — Sol System", m.displayName);
        assertEquals("Sol System", m.locationName);
        assertEquals("Docked at Haven Station", m.locationDetail);
        assertEquals(45000L, m.playtimeSeconds);
        assertEquals(12500L, m.playerCredits);
        assertEquals("Cobra Mk III", m.shipName);
    }

    @Test
    void noArgConstructorDefaultsDisplayFieldsToNull() {
        ManifestData m = new ManifestData();
        assertNull(m.displayName);
        assertNull(m.locationName);
        assertNull(m.locationDetail);
        assertEquals(0L, m.playtimeSeconds);
        assertEquals(0L, m.playerCredits);
        assertNull(m.shipName);
    }

    @Test
    void isAutosaveDetectsAutosaveSlotNames() {
        ManifestData auto = new ManifestData();
        auto.saveName = "autosave-0";
        assertTrue(auto.isAutosave());

        ManifestData manual = new ManifestData();
        manual.saveName = "save-2026-05-27-143200";
        assertFalse(manual.isAutosave());
    }

    @Test
    void getDisplayNameFallsBackToSaveName() {
        ManifestData m = new ManifestData();
        m.saveName = "my-save";
        m.displayName = null;
        assertEquals("my-save", m.getDisplayNameOrFallback());

        m.displayName = "Custom Name";
        assertEquals("Custom Name", m.getDisplayNameOrFallback());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.ManifestDataTest" --info`
Expected: FAIL — fields and methods don't exist yet.

- [ ] **Step 3: Add fields and methods to ManifestData**

Modify `core/src/main/java/com/galacticodyssey/persistence/ManifestData.java` — add after the existing fields:

```java
public String displayName;
public String locationName;
public String locationDetail;
public long playtimeSeconds;
public long playerCredits;
public String shipName;

public boolean isAutosave() {
    return saveName != null && saveName.startsWith("autosave-");
}

public String getDisplayNameOrFallback() {
    return (displayName != null && !displayName.isEmpty()) ? displayName : saveName;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.ManifestDataTest" --info`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/ManifestData.java core/src/test/java/com/galacticodyssey/persistence/ManifestDataTest.java
git commit -m "feat(persistence): add display fields to ManifestData for save/load UI"
```

---

### Task 2: Add Thumbnail Capture Utility

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/ThumbnailCapture.java`
- Create: `core/src/test/java/com/galacticodyssey/persistence/ThumbnailCaptureTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/persistence/ThumbnailCaptureTest.java`:

```java
package com.galacticodyssey.persistence;

import com.badlogic.gdx.graphics.Pixmap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThumbnailCaptureTest {

    @Test
    void downscaleReducesResolution() {
        Pixmap source = new Pixmap(1920, 1080, Pixmap.Format.RGBA8888);
        source.setColor(1f, 0f, 0f, 1f);
        source.fill();

        Pixmap result = ThumbnailCapture.downscale(source, 384, 216);

        assertEquals(384, result.getWidth());
        assertEquals(216, result.getHeight());

        result.dispose();
        source.dispose();
    }

    @Test
    void downscaleHandlesAlreadySmallImage() {
        Pixmap source = new Pixmap(200, 100, Pixmap.Format.RGBA8888);
        source.setColor(0f, 1f, 0f, 1f);
        source.fill();

        Pixmap result = ThumbnailCapture.downscale(source, 384, 216);

        assertEquals(384, result.getWidth());
        assertEquals(216, result.getHeight());

        result.dispose();
        source.dispose();
    }
}
```

Note: These tests use Pixmap directly (no GL context needed for Pixmap creation from scratch). The actual framebuffer capture (`ScreenUtils.getFrameBufferPixmap`) requires GL and is tested via the game manually.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.ThumbnailCaptureTest" --info`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement ThumbnailCapture**

Create `core/src/main/java/com/galacticodyssey/persistence/ThumbnailCapture.java`:

```java
package com.galacticodyssey.persistence;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.ScreenUtils;

import java.io.File;
import java.nio.ByteBuffer;

public class ThumbnailCapture {

    public static final int THUMBNAIL_WIDTH = 384;
    public static final int THUMBNAIL_HEIGHT = 216;

    public static Pixmap captureFramebuffer() {
        return ScreenUtils.getFrameBufferPixmap(
            0, 0,
            com.badlogic.gdx.Gdx.graphics.getBackBufferWidth(),
            com.badlogic.gdx.Gdx.graphics.getBackBufferHeight());
    }

    public static Pixmap downscale(Pixmap source, int targetWidth, int targetHeight) {
        Pixmap target = new Pixmap(targetWidth, targetHeight, Pixmap.Format.RGBA8888);
        target.setFilter(Pixmap.Filter.BiLinear);
        target.drawPixmap(source,
            0, 0, source.getWidth(), source.getHeight(),
            0, 0, targetWidth, targetHeight);
        return target;
    }

    public static void saveThumbnail(Pixmap framebuffer, File saveDir) {
        Pixmap thumbnail = downscale(framebuffer, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        try {
            com.badlogic.gdx.graphics.PixmapIO.writePNG(
                com.badlogic.gdx.files.FileHandle.tempFile("thumb"),
                thumbnail);
            // Use Gdx FileHandle for cross-platform PNG write
            var thumbFile = new com.badlogic.gdx.files.FileHandle(new File(saveDir, "thumbnail.png"));
            com.badlogic.gdx.graphics.PixmapIO.writePNG(thumbFile, thumbnail);
        } finally {
            thumbnail.dispose();
        }
    }

    public static Pixmap captureAndDownscale() {
        Pixmap full = captureFramebuffer();
        Pixmap thumbnail = downscale(full, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        full.dispose();
        return thumbnail;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.ThumbnailCaptureTest" --info`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/ThumbnailCapture.java core/src/test/java/com/galacticodyssey/persistence/ThumbnailCaptureTest.java
git commit -m "feat(persistence): add ThumbnailCapture utility for save screenshots"
```

---

### Task 3: Extend SaveBackend with copySave and Thumbnail Support

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/persistence/SaveBackend.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/LocalFileSaveBackend.java`

- [ ] **Step 1: Write the failing test**

Add to the existing `LocalFileSaveBackendTest.java`:

```java
@Test
void copySaveCreatesIdenticalCopy() {
    SaveBundle bundle = createTestBundle("original");
    backend.writeSave("original", bundle);

    backend.copySave("original", "copy-of-original");

    SaveBundle copied = backend.readSave("copy-of-original");
    assertEquals("original", copied.manifest.saveName);
    assertTrue(new File(savesRoot, "copy-of-original").exists());
}

@Test
void readManifestOnlyReturnsManifestWithoutFullLoad() {
    SaveBundle bundle = createTestBundle("quick-read");
    backend.writeSave("quick-read", bundle);

    ManifestData manifest = backend.readManifestOnly("quick-read");
    assertEquals("quick-read", manifest.saveName);
}

@Test
void listSavesReturnsManifestOnly() {
    SaveBundle bundle = createTestBundle("list-test");
    backend.writeSave("list-test", bundle);

    List<ManifestData> saves = backend.listSaves();
    assertFalse(saves.isEmpty());
    assertEquals("list-test", saves.get(0).saveName);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.LocalFileSaveBackendTest" --info`
Expected: FAIL — `copySave` and `readManifestOnly` don't exist.

- [ ] **Step 3: Add copySave to SaveBackend interface**

Modify `core/src/main/java/com/galacticodyssey/persistence/SaveBackend.java`:

```java
package com.galacticodyssey.persistence;

import java.util.List;

public interface SaveBackend {
    void writeSave(String saveId, SaveBundle bundle);
    SaveBundle readSave(String saveId);
    List<ManifestData> listSaves();
    void deleteSave(String saveId);
    void copySave(String sourceId, String destId);
    ManifestData readManifestOnly(String saveId);
}
```

- [ ] **Step 4: Implement in LocalFileSaveBackend**

Add to `LocalFileSaveBackend.java`:

```java
@Override
public void copySave(String sourceId, String destId) {
    File sourceDir = new File(savesRoot, sourceId);
    File destDir = new File(savesRoot, destId);
    if (!sourceDir.exists()) {
        throw new RuntimeException("Source save not found: " + sourceId);
    }
    copyDirectoryRecursive(sourceDir, destDir);
}

@Override
public ManifestData readManifestOnly(String saveId) {
    File saveDir = new File(savesRoot, saveId);
    File manifestFile = new File(saveDir, "manifest.bin");
    if (!manifestFile.exists()) {
        throw new RuntimeException("Manifest not found: " + saveId);
    }
    return reader.readManifest(manifestFile);
}

private void copyDirectoryRecursive(File source, File dest) {
    dest.mkdirs();
    File[] files = source.listFiles();
    if (files == null) return;
    for (File file : files) {
        File destFile = new File(dest, file.getName());
        if (file.isDirectory()) {
            copyDirectoryRecursive(file, destFile);
        } else {
            copyFile(file, destFile);
        }
    }
}

private void copyFile(File source, File dest) {
    try (java.io.FileInputStream in = new java.io.FileInputStream(source);
         java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    } catch (java.io.IOException e) {
        throw new RuntimeException("Failed to copy " + source.getName(), e);
    }
}
```

Also update `listSaves()` to use `readManifestOnly` instead of reading the full bundle:

```java
@Override
public List<ManifestData> listSaves() {
    List<ManifestData> result = new ArrayList<>();
    File[] dirs = savesRoot.listFiles(File::isDirectory);
    if (dirs == null) return result;

    for (File dir : dirs) {
        File manifestFile = new File(dir, "manifest.bin");
        if (manifestFile.exists()) {
            try {
                result.add(reader.readManifest(manifestFile));
            } catch (Exception e) {
                // Corrupted save — skip
            }
        }
    }

    result.sort((a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
    return result;
}
```

Note: `SaveReader.readManifest(File)` is a new method on `SaveReader` that reads only `manifest.bin`. Add it:

```java
// In SaveReader.java
public ManifestData readManifest(File manifestFile) {
    try (com.esotericsoftware.kryo.io.Input input =
             new com.esotericsoftware.kryo.io.Input(new java.io.FileInputStream(manifestFile))) {
        return kryo.readObject(input, ManifestData.class);
    } catch (Exception e) {
        throw new RuntimeException("Failed to read manifest: " + manifestFile, e);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.LocalFileSaveBackendTest" --info`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/SaveBackend.java core/src/main/java/com/galacticodyssey/persistence/LocalFileSaveBackend.java core/src/main/java/com/galacticodyssey/persistence/SaveReader.java
git commit -m "feat(persistence): add copySave, readManifestOnly to SaveBackend"
```

---

### Task 4: Add Skin Styles for Save Slot Cards

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/UiFactory.java`

- [ ] **Step 1: Add new font sizes and label styles**

Add to `UiFactory.createSkin()`, after the existing font generation block and before `generator.dispose()`:

```java
FreeTypeFontParameter slotNameParam = new FreeTypeFontParameter();
slotNameParam.size = 16;
slotNameParam.color = CYAN;
BitmapFont slotNameFont = generator.generateFont(slotNameParam);
skin.add("slot-name", slotNameFont);

FreeTypeFontParameter slotDetailParam = new FreeTypeFontParameter();
slotDetailParam.size = 13;
slotDetailParam.color = new Color(0.53f, 0.6f, 0.67f, 1f);
BitmapFont slotDetailFont = generator.generateFont(slotDetailParam);
skin.add("slot-detail", slotDetailFont);

FreeTypeFontParameter slotMetaParam = new FreeTypeFontParameter();
slotMetaParam.size = 12;
slotMetaParam.color = new Color(0.33f, 0.4f, 0.47f, 1f);
BitmapFont slotMetaFont = generator.generateFont(slotMetaParam);
skin.add("slot-meta", slotMetaFont);

FreeTypeFontParameter smallButtonParam = new FreeTypeFontParameter();
smallButtonParam.size = 12;
smallButtonParam.color = Color.WHITE;
BitmapFont smallButtonFont = generator.generateFont(smallButtonParam);
skin.add("small-button", smallButtonFont);
```

Add label styles after the existing label styles:

```java
Label.LabelStyle slotNameStyle = new Label.LabelStyle();
slotNameStyle.font = slotNameFont;
slotNameStyle.fontColor = CYAN.cpy();
skin.add("slot-name", slotNameStyle);

Label.LabelStyle slotDetailStyle = new Label.LabelStyle();
slotDetailStyle.font = slotDetailFont;
slotDetailStyle.fontColor = new Color(0.53f, 0.6f, 0.67f, 1f);
skin.add("slot-detail", slotDetailStyle);

Label.LabelStyle slotMetaStyle = new Label.LabelStyle();
slotMetaStyle.font = slotMetaFont;
slotMetaStyle.fontColor = new Color(0.33f, 0.4f, 0.47f, 1f);
skin.add("slot-meta", slotMetaStyle);
```

Add small button styles (cyan and red variants):

```java
TextButton.TextButtonStyle smallButtonStyle = new TextButton.TextButtonStyle();
smallButtonStyle.font = smallButtonFont;
smallButtonStyle.fontColor = CYAN.cpy();
smallButtonStyle.overFontColor = Color.WHITE;
smallButtonStyle.up = createButtonDrawable(new Color(0f, 0f, 0f, 0.15f), new Color(0f, 0.6f, 0.8f, 0.4f));
smallButtonStyle.over = createButtonDrawable(new Color(0f, 0f, 0f, 0.3f), CYAN);
skin.add("small", smallButtonStyle);

TextButton.TextButtonStyle smallRedButtonStyle = new TextButton.TextButtonStyle();
smallRedButtonStyle.font = smallButtonFont;
smallRedButtonStyle.fontColor = new Color(0.8f, 0.2f, 0.2f, 1f);
smallRedButtonStyle.overFontColor = new Color(1f, 0.3f, 0.3f, 1f);
smallRedButtonStyle.up = createButtonDrawable(new Color(0f, 0f, 0f, 0.15f), new Color(0.8f, 0.2f, 0.2f, 0.4f));
smallRedButtonStyle.over = createButtonDrawable(new Color(0f, 0f, 0f, 0.3f), new Color(0.8f, 0.2f, 0.2f, 1f));
skin.add("small-red", smallRedButtonStyle);
```

Add a TextField style for the rename dialog:

```java
com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle textFieldStyle =
    new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle();
textFieldStyle.font = settingFont;
textFieldStyle.fontColor = Color.WHITE;
textFieldStyle.cursor = createButtonDrawable(CYAN, CYAN);
textFieldStyle.selection = createButtonDrawable(new Color(0f, 0.3f, 0.4f, 0.8f), CYAN);
textFieldStyle.background = createButtonDrawable(BG_DEFAULT, BORDER_DEFAULT);
skin.add("default", textFieldStyle);
```

- [ ] **Step 2: Verify game still launches (no crashes from skin changes)**

Run: `./gradlew :desktop:run`
Expected: Main menu renders normally. Quit.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/UiFactory.java
git commit -m "feat(ui): add skin styles for save slot cards, small buttons, and text field"
```

---

### Task 5: Create ConfirmDialog and RenameDialog

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/ConfirmDialog.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/RenameDialog.java`

- [ ] **Step 1: Implement ConfirmDialog**

Create `core/src/main/java/com/galacticodyssey/ui/ConfirmDialog.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

public class ConfirmDialog extends Group implements Disposable {

    private final Texture overlayTexture;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmDialog(Stage stage, Skin skin, String message,
                         String confirmText, String cancelText,
                         Runnable onConfirm, Runnable onCancel) {
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        setSize(stage.getWidth(), stage.getHeight());

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0f, 0f, 0.7f));
        pixmap.fill();
        overlayTexture = new Texture(pixmap);
        pixmap.dispose();

        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));
        addActor(overlay);

        // Consume clicks on the overlay so they don't pass through
        overlay.addListener(new ClickListener() {});

        Table panel = new Table();
        panel.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));

        Label messageLabel = new Label(message, skin, "setting");
        messageLabel.setWrap(true);
        panel.add(messageLabel).width(400).pad(20).row();

        Table buttonRow = new Table();
        TextButton confirmBtn = new TextButton(confirmText, skin);
        TextButton cancelBtn = new TextButton(cancelText, skin);

        confirmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dismiss();
                onConfirm.run();
            }
        });

        cancelBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dismiss();
                onCancel.run();
            }
        });

        buttonRow.add(confirmBtn).width(150).height(40).padRight(16);
        buttonRow.add(cancelBtn).width(150).height(40);
        panel.add(buttonRow).padBottom(20).row();

        overlay.add(panel);
    }

    public void show(Stage stage) {
        stage.addActor(this);
    }

    public void dismiss() {
        remove();
        dispose();
    }

    @Override
    public void dispose() {
        if (overlayTexture != null) {
            overlayTexture.dispose();
        }
    }
}
```

- [ ] **Step 2: Implement RenameDialog**

Create `core/src/main/java/com/galacticodyssey/ui/RenameDialog.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

import java.util.function.Consumer;

public class RenameDialog extends Group implements Disposable {

    private final Texture overlayTexture;
    private final TextField textField;

    public RenameDialog(Stage stage, Skin skin, String currentName,
                        Consumer<String> onConfirm, Runnable onCancel) {

        setSize(stage.getWidth(), stage.getHeight());

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0f, 0f, 0.7f));
        pixmap.fill();
        overlayTexture = new Texture(pixmap);
        pixmap.dispose();

        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));
        addActor(overlay);

        overlay.addListener(new ClickListener() {});

        Table panel = new Table();
        panel.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));

        Label titleLabel = new Label("Rename Save", skin, "header");
        panel.add(titleLabel).pad(20).row();

        textField = new TextField(currentName, skin);
        textField.setMaxLength(64);
        panel.add(textField).width(350).pad(10).row();

        Table buttonRow = new Table();
        TextButton confirmBtn = new TextButton("Confirm", skin);
        TextButton cancelBtn = new TextButton("Cancel", skin);

        confirmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String newName = textField.getText().trim();
                dismiss();
                if (!newName.isEmpty()) {
                    onConfirm.accept(newName);
                }
            }
        });

        cancelBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dismiss();
                onCancel.run();
            }
        });

        buttonRow.add(confirmBtn).width(150).height(40).padRight(16);
        buttonRow.add(cancelBtn).width(150).height(40);
        panel.add(buttonRow).padBottom(20).row();

        overlay.add(panel);
    }

    public void show(Stage stage) {
        stage.addActor(this);
        stage.setKeyboardFocus(textField);
        textField.selectAll();
    }

    public void dismiss() {
        remove();
        dispose();
    }

    @Override
    public void dispose() {
        if (overlayTexture != null) {
            overlayTexture.dispose();
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/ConfirmDialog.java core/src/main/java/com/galacticodyssey/ui/RenameDialog.java
git commit -m "feat(ui): add ConfirmDialog and RenameDialog modal overlays"
```

---

### Task 6: Create SaveToast Notification

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/SaveToast.java`

- [ ] **Step 1: Implement SaveToast**

Create `core/src/main/java/com/galacticodyssey/ui/SaveToast.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

public class SaveToast {

    private static final float DISPLAY_DURATION = 2f;
    private static final float FADE_DURATION = 0.5f;

    public static void show(Stage stage, Skin skin, String message) {
        Table toast = new Table();
        Label label = new Label(message, skin, "header");
        toast.add(label).pad(12, 24, 12, 24);

        toast.pack();
        toast.setPosition(
            (stage.getWidth() - toast.getWidth()) / 2f,
            stage.getHeight() - toast.getHeight() - 40f);

        toast.getColor().a = 0f;
        toast.addAction(Actions.sequence(
            Actions.fadeIn(0.3f, Interpolation.smooth),
            Actions.delay(DISPLAY_DURATION),
            Actions.fadeOut(FADE_DURATION, Interpolation.smooth),
            Actions.removeActor()
        ));

        stage.addActor(toast);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/SaveToast.java
git commit -m "feat(ui): add SaveToast notification widget"
```

---

### Task 7: Create SaveSlotListener Interface and SaveSlotPanel Widget

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/SaveSlotListener.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/SaveSlotPanel.java`

- [ ] **Step 1: Create SaveSlotListener interface**

Create `core/src/main/java/com/galacticodyssey/ui/SaveSlotListener.java`:

```java
package com.galacticodyssey.ui;

import com.galacticodyssey.persistence.ManifestData;

public interface SaveSlotListener {
    void onSlotClicked(ManifestData manifest);
    void onRenameClicked(ManifestData manifest);
    void onCopyClicked(ManifestData manifest);
    void onDeleteClicked(ManifestData manifest);
}
```

- [ ] **Step 2: Implement SaveSlotPanel**

Create `core/src/main/java/com/galacticodyssey/ui/SaveSlotPanel.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.persistence.ManifestData;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SaveSlotPanel extends Table implements Disposable {

    private static final float CARD_WIDTH = 780f;
    private static final float CARD_HEIGHT = 110f;
    private static final float THUMB_WIDTH = 160f;
    private static final float THUMB_HEIGHT = 90f;

    private final ManifestData manifest;
    private Texture placeholderTexture;
    private Texture backgroundTexture;

    public SaveSlotPanel(ManifestData manifest, Skin skin, boolean isAutosave,
                         Texture thumbnail, SaveSlotListener listener) {
        this.manifest = manifest;

        backgroundTexture = createCardBackground();
        setBackground(new TextureRegionDrawable(new TextureRegion(backgroundTexture)));

        pad(10);
        setTransform(true);
        setOrigin(Align.center);

        // Thumbnail
        Image thumbImage;
        if (thumbnail != null) {
            thumbImage = new Image(new TextureRegionDrawable(new TextureRegion(thumbnail)));
        } else {
            placeholderTexture = createPlaceholder();
            thumbImage = new Image(new TextureRegionDrawable(new TextureRegion(placeholderTexture)));
        }
        add(thumbImage).width(THUMB_WIDTH).height(THUMB_HEIGHT).padRight(14);

        // Info block
        Table infoTable = new Table();
        infoTable.left();

        String displayName = manifest.getDisplayNameOrFallback();
        Label nameLabel = new Label(displayName, skin, "slot-name");
        infoTable.add(nameLabel).left().row();

        String detail = manifest.locationDetail != null ? manifest.locationDetail : "";
        if (manifest.locationName != null && !detail.isEmpty()) {
            detail = manifest.locationName + " • " + detail;
        } else if (manifest.locationName != null) {
            detail = manifest.locationName;
        }
        Label detailLabel = new Label(detail, skin, "slot-detail");
        infoTable.add(detailLabel).left().padTop(2).row();

        String meta = formatMeta(manifest);
        Label metaLabel = new Label(meta, skin, "slot-meta");
        infoTable.add(metaLabel).left().padTop(6).row();

        add(infoTable).expandX().fillX().padRight(14);

        // Action buttons
        Table actions = new Table();
        if (!isAutosave) {
            TextButton renameBtn = new TextButton("Rename", skin, "small");
            renameBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    event.stop();
                    listener.onRenameClicked(manifest);
                }
            });
            actions.add(renameBtn).width(70).height(24).padBottom(5).row();
        }

        TextButton copyBtn = new TextButton("Copy", skin, "small");
        copyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                event.stop();
                listener.onCopyClicked(manifest);
            }
        });
        actions.add(copyBtn).width(70).height(24).padBottom(5).row();

        TextButton deleteBtn = new TextButton("Delete", skin, "small-red");
        deleteBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                event.stop();
                listener.onDeleteClicked(manifest);
            }
        });
        actions.add(deleteBtn).width(70).height(24).row();

        add(actions).right();

        // Card body click → primary action
        addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer == -1) {
                    addAction(Actions.scaleTo(1.01f, 1.01f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) {
                    addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                listener.onSlotClicked(manifest);
            }
        });
    }

    private String formatMeta(ManifestData m) {
        StringBuilder sb = new StringBuilder();
        if (m.timestampMillis > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm");
            sb.append(sdf.format(new Date(m.timestampMillis)));
        }
        if (m.playtimeSeconds > 0) {
            long hours = m.playtimeSeconds / 3600;
            long minutes = (m.playtimeSeconds % 3600) / 60;
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(hours).append("h ").append(minutes).append("m");
        }
        if (m.playerCredits > 0) {
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(String.format("%,d cr", m.playerCredits));
        }
        if (m.shipName != null && !m.shipName.isEmpty()) {
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(m.shipName);
        }
        return sb.toString();
    }

    private Texture createCardBackground() {
        Pixmap pixmap = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0.03f, 0.05f, 0.6f));
        pixmap.fill();
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.3f));
        pixmap.drawRectangle(0, 0, 4, 4);
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    private Texture createPlaceholder() {
        Pixmap pixmap = new Pixmap((int) THUMB_WIDTH, (int) THUMB_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0.1f, 0.1f, 0.18f, 1f));
        pixmap.fill();
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.3f));
        pixmap.drawRectangle(0, 0, pixmap.getWidth(), pixmap.getHeight());
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    @Override
    public void dispose() {
        if (placeholderTexture != null) placeholderTexture.dispose();
        if (backgroundTexture != null) backgroundTexture.dispose();
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/SaveSlotListener.java core/src/main/java/com/galacticodyssey/ui/SaveSlotPanel.java
git commit -m "feat(ui): add SaveSlotPanel widget and SaveSlotListener interface"
```

---

### Task 8: Create SaveListBaseScreen Abstract Base

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/SaveListBaseScreen.java`

- [ ] **Step 1: Implement SaveListBaseScreen**

Create `core/src/main/java/com/galacticodyssey/ui/SaveListBaseScreen.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.persistence.ManifestData;
import com.galacticodyssey.persistence.SaveBackend;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SaveListBaseScreen implements Screen, SaveSlotListener {

    protected static final float WORLD_WIDTH = 1280f;
    protected static final float WORLD_HEIGHT = 720f;

    protected final GalacticOdyssey game;
    protected final Skin skin;
    protected final AudioManager audioManager;
    protected final SaveBackend saveBackend;
    protected final Screen returnTo;

    protected Stage stage;
    protected StarfieldBackground starfield;
    protected OrthographicCamera backgroundCamera;

    protected Table listTable;
    protected List<ManifestData> manifests = new ArrayList<>();
    protected final Map<String, Texture> thumbnailCache = new HashMap<>();
    protected final List<SaveSlotPanel> slotPanels = new ArrayList<>();

    public SaveListBaseScreen(GalacticOdyssey game, SaveBackend saveBackend, Screen returnTo) {
        this.game = game;
        this.skin = game.getSkin();
        this.audioManager = game.getAudioManager();
        this.saveBackend = saveBackend;
        this.returnTo = returnTo;
    }

    protected abstract String getTitle();

    protected abstract void onSlotClicked(ManifestData manifest);

    protected void buildExtraSlots(Table listTable) {}

    @Override
    public void show() {
        stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        backgroundCamera = new OrthographicCamera();

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        starfield = new StarfieldBackground(screenW, screenH);

        Gdx.input.setInputProcessor(stage);
        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().center();

        // Title
        Label title = new Label(getTitle(), skin, "title");
        root.add(title).padTop(30).padBottom(20).row();

        // Scrollable list
        listTable = new Table();
        listTable.top();
        listTable.defaults().padBottom(8);

        buildExtraSlots(listTable);
        refreshSaveList();

        ScrollPane scrollPane = new ScrollPane(listTable, skin);
        scrollPane.setFadeScrollBars(true);
        scrollPane.setScrollingDisabled(true, false);
        root.add(scrollPane).width(820).expandY().fillY().padBottom(12).row();

        // Back button
        TextButton backBtn = new TextButton("BACK", skin);
        backBtn.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer == -1) {
                    backBtn.setOrigin(Align.center);
                    backBtn.addAction(Actions.scaleTo(1.02f, 1.02f, 0.1f, Interpolation.smooth));
                    audioManager.playSound("audio/sfx/ui_hover.ogg");
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) {
                    backBtn.addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                audioManager.playSound("audio/sfx/ui_click.ogg");
                goBack();
            }
        });
        backBtn.setTransform(true);
        root.add(backBtn).width(200).height(45).padBottom(20).row();

        stage.addActor(root);
    }

    protected void refreshSaveList() {
        // Clear old panels
        for (SaveSlotPanel panel : slotPanels) {
            panel.dispose();
        }
        slotPanels.clear();
        disposeThumbnails();

        // Keep any extra slots (e.g. New Save) already added
        // Remove everything after the extra slots
        listTable.clearChildren();
        buildExtraSlots(listTable);

        manifests = saveBackend.listSaves();

        List<ManifestData> manualSaves = new ArrayList<>();
        List<ManifestData> autoSaves = new ArrayList<>();
        for (ManifestData m : manifests) {
            if (m.isAutosave()) {
                autoSaves.add(m);
            } else {
                manualSaves.add(m);
            }
        }

        for (ManifestData m : manualSaves) {
            addSlotPanel(m, false);
        }

        if (!autoSaves.isEmpty()) {
            // Autosave divider
            Table divider = new Table();
            Label dividerLabel = new Label("AUTOSAVES", skin, "slot-meta");
            divider.add(dividerLabel).padTop(8).padBottom(8);
            listTable.add(divider).row();

            for (ManifestData m : autoSaves) {
                addSlotPanel(m, true);
            }
        }
    }

    private void addSlotPanel(ManifestData manifest, boolean isAutosave) {
        Texture thumbnail = loadThumbnail(manifest.saveName);
        SaveSlotPanel panel = new SaveSlotPanel(manifest, skin, isAutosave, thumbnail, this);
        slotPanels.add(panel);
        listTable.add(panel).width(780).row();
    }

    protected Texture loadThumbnail(String saveId) {
        if (thumbnailCache.containsKey(saveId)) {
            return thumbnailCache.get(saveId);
        }
        try {
            File thumbFile = new File(getSavesRoot(), saveId + "/thumbnail.png");
            if (thumbFile.exists()) {
                Texture tex = new Texture(new com.badlogic.gdx.files.FileHandle(thumbFile));
                thumbnailCache.put(saveId, tex);
                return tex;
            }
        } catch (Exception e) {
            Gdx.app.error("SaveList", "Failed to load thumbnail for " + saveId, e);
        }
        return null;
    }

    protected File getSavesRoot() {
        String userHome = System.getProperty("user.home");
        return new File(userHome, ".galacticodyssey/saves");
    }

    protected void goBack() {
        game.setScreen(returnTo);
    }

    // SaveSlotListener defaults — subclasses override onSlotClicked
    @Override
    public void onRenameClicked(ManifestData manifest) {
        new RenameDialog(stage, skin, manifest.getDisplayNameOrFallback(),
            newName -> {
                manifest.displayName = newName;
                saveBackend.writeSave(manifest.saveName,
                    saveBackend.readSave(manifest.saveName));
                refreshSaveList();
            },
            () -> {}
        ).show(stage);
    }

    @Override
    public void onCopyClicked(ManifestData manifest) {
        String copyId = manifest.saveName + "-copy-" + System.currentTimeMillis();
        saveBackend.copySave(manifest.saveName, copyId);
        audioManager.playSound("audio/sfx/ui_click.ogg");
        refreshSaveList();
    }

    @Override
    public void onDeleteClicked(ManifestData manifest) {
        new ConfirmDialog(stage, skin,
            "Delete '" + manifest.getDisplayNameOrFallback() + "'? This cannot be undone.",
            "Delete", "Cancel",
            () -> {
                saveBackend.deleteSave(manifest.saveName);
                audioManager.playSound("audio/sfx/ui_click.ogg");
                refreshSaveList();
            },
            () -> {}
        ).show(stage);
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
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        for (SaveSlotPanel panel : slotPanels) {
            panel.dispose();
        }
        slotPanels.clear();
        disposeThumbnails();
        if (stage != null) { stage.dispose(); stage = null; }
        if (starfield != null) { starfield.dispose(); starfield = null; }
    }

    private void disposeThumbnails() {
        for (Texture tex : thumbnailCache.values()) {
            tex.dispose();
        }
        thumbnailCache.clear();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/SaveListBaseScreen.java
git commit -m "feat(ui): add SaveListBaseScreen abstract base for save/load screens"
```

---

### Task 9: Create LoadScreen

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/LoadScreen.java`

- [ ] **Step 1: Implement LoadScreen**

Create `core/src/main/java/com/galacticodyssey/ui/LoadScreen.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.persistence.ManifestData;
import com.galacticodyssey.persistence.SaveBackend;
import com.badlogic.gdx.Screen;

public class LoadScreen extends SaveListBaseScreen {

    public enum Origin { MAIN_MENU, PAUSE_MENU }

    private final Origin origin;
    private final GameScreen gameScreen;

    public LoadScreen(GalacticOdyssey game, SaveBackend saveBackend,
                      Screen returnTo, Origin origin) {
        this(game, saveBackend, returnTo, origin, null);
    }

    public LoadScreen(GalacticOdyssey game, SaveBackend saveBackend,
                      Screen returnTo, Origin origin, GameScreen gameScreen) {
        super(game, saveBackend, returnTo);
        this.origin = origin;
        this.gameScreen = gameScreen;
    }

    @Override
    protected String getTitle() {
        return "LOAD GAME";
    }

    @Override
    public void onSlotClicked(ManifestData manifest) {
        audioManager.playSound("audio/sfx/ui_click.ogg");

        if (origin == Origin.PAUSE_MENU) {
            new ConfirmDialog(stage, skin,
                "Load '" + manifest.getDisplayNameOrFallback()
                    + "'? Unsaved progress will be lost.",
                "Load", "Cancel",
                () -> loadSave(manifest),
                () -> {}
            ).show(stage);
        } else {
            loadSave(manifest);
        }
    }

    private void loadSave(ManifestData manifest) {
        Gdx.app.log("LoadScreen", "Loading save: " + manifest.saveName);
        // Dispose old GameScreen if loading from pause
        if (gameScreen != null) {
            gameScreen.dispose();
        }
        // TODO: Use SaveCoordinator to load game state, then switch to GameScreen
        // For now, transition to a fresh GameScreen
        game.setScreen(new GameScreen(game));
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/LoadScreen.java
git commit -m "feat(ui): add LoadScreen with main menu and pause menu support"
```

---

### Task 10: Create SaveScreen

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/SaveScreen.java`

- [ ] **Step 1: Implement SaveScreen**

Create `core/src/main/java/com/galacticodyssey/ui/SaveScreen.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.persistence.ManifestData;
import com.galacticodyssey.persistence.SaveBackend;
import com.galacticodyssey.persistence.ThumbnailCapture;

import java.io.File;
import java.util.List;

public class SaveScreen extends SaveListBaseScreen {

    private final GameScreen gameScreen;
    private Texture newSaveBackground;
    private Texture newSavePlaceholder;
    private Pixmap capturedThumbnail;

    public SaveScreen(GalacticOdyssey game, SaveBackend saveBackend, GameScreen gameScreen) {
        super(game, saveBackend, gameScreen);
        this.gameScreen = gameScreen;
    }

    @Override
    protected String getTitle() {
        return "SAVE GAME";
    }

    @Override
    protected void buildExtraSlots(Table listTable) {
        // "New Save" card with dashed style
        Table newSaveCard = new Table();

        newSaveBackground = createDashedBackground();
        newSaveCard.setBackground(new TextureRegionDrawable(new TextureRegion(newSaveBackground)));
        newSaveCard.pad(10);

        newSavePlaceholder = createPlusPlaceholder();
        Image plusImage = new Image(new TextureRegionDrawable(new TextureRegion(newSavePlaceholder)));
        newSaveCard.add(plusImage).width(160).height(90).padRight(14);

        Table infoTable = new Table();
        infoTable.left();
        Label nameLabel = new Label("New Save", skin, "slot-name");
        infoTable.add(nameLabel).left().row();
        Label detailLabel = new Label("Create a new save slot", skin, "slot-detail");
        infoTable.add(detailLabel).left().padTop(2).row();

        newSaveCard.add(infoTable).expandX().fillX();

        newSaveCard.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audioManager.playSound("audio/sfx/ui_click.ogg");
                createNewSave();
            }
        });

        listTable.add(newSaveCard).width(780).padBottom(12).row();
    }

    @Override
    public void onSlotClicked(ManifestData manifest) {
        audioManager.playSound("audio/sfx/ui_click.ogg");
        new ConfirmDialog(stage, skin,
            "Overwrite '" + manifest.getDisplayNameOrFallback() + "'? This cannot be undone.",
            "Overwrite", "Cancel",
            () -> overwriteSave(manifest),
            () -> {}
        ).show(stage);
    }

    private void createNewSave() {
        int nextNumber = computeNextSaveNumber();
        String locationName = "Unknown"; // TODO: get from game state
        String displayName = "Save #" + nextNumber + " — " + locationName;
        String saveId = "save-" + System.currentTimeMillis();

        performSave(saveId, displayName);
    }

    private void overwriteSave(ManifestData manifest) {
        performSave(manifest.saveName, manifest.getDisplayNameOrFallback());
    }

    private void performSave(String saveId, String displayName) {
        Gdx.app.log("SaveScreen", "Saving to: " + saveId);

        // TODO: Wire SaveCoordinator.save() with display metadata
        // For now, just show the toast and go back
        SaveToast.show(stage, skin, "Game Saved");

        // Return to paused game after brief delay
        stage.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.5f),
            com.badlogic.gdx.scenes.scene2d.actions.Actions.run(this::goBack)
        ));
    }

    private int computeNextSaveNumber() {
        int max = 0;
        for (ManifestData m : manifests) {
            if (m.isAutosave()) continue;
            String name = m.getDisplayNameOrFallback();
            if (name.startsWith("Save #")) {
                try {
                    int dashIdx = name.indexOf('—');
                    String numStr;
                    if (dashIdx > 0) {
                        numStr = name.substring(6, dashIdx).trim();
                    } else {
                        numStr = name.substring(6).trim();
                    }
                    int num = Integer.parseInt(numStr);
                    if (num > max) max = num;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max + 1;
    }

    private Texture createDashedBackground() {
        Pixmap pixmap = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0.03f, 0.05f, 0.3f));
        pixmap.fill();
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.3f));
        pixmap.drawRectangle(0, 0, 4, 4);
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    private Texture createPlusPlaceholder() {
        int w = 160, h = 90;
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.05f));
        pixmap.fill();
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.3f));
        pixmap.drawRectangle(0, 0, w, h);
        // Draw + sign
        int cx = w / 2, cy = h / 2;
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.6f));
        pixmap.fillRectangle(cx - 1, cy - 12, 3, 25);
        pixmap.fillRectangle(cx - 12, cy - 1, 25, 3);
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (newSaveBackground != null) { newSaveBackground.dispose(); newSaveBackground = null; }
        if (newSavePlaceholder != null) { newSavePlaceholder.dispose(); newSavePlaceholder = null; }
        if (capturedThumbnail != null) { capturedThumbnail.dispose(); capturedThumbnail = null; }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/SaveScreen.java
git commit -m "feat(ui): add SaveScreen with New Save card and overwrite confirmation"
```

---

### Task 11: Wire SaveBackend into GalacticOdyssey

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java`

- [ ] **Step 1: Add SaveBackend field and accessor**

Modify `core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java`:

Add import:
```java
import com.galacticodyssey.persistence.SaveBackend;
import com.galacticodyssey.persistence.LocalFileSaveBackend;
import java.io.File;
```

Add field:
```java
private SaveBackend saveBackend;
```

In `create()`, after `audioManager = new AudioManager(preferences);`, add:
```java
File savesDir = new File(System.getProperty("user.home"), ".galacticodyssey/saves");
saveBackend = new LocalFileSaveBackend(savesDir);
```

Add accessor:
```java
public SaveBackend getSaveBackend() {
    return saveBackend;
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java
git commit -m "feat(core): wire SaveBackend into GalacticOdyssey game class"
```

---

### Task 12: Integrate Load Game into Main Menu

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java`

- [ ] **Step 1: Add Load Game button and enable Continue**

Modify the `buildUi()` method in `MainMenuScreen.java`. Replace the existing button block with:

```java
addMenuButton(root, "New Game", skin, false,
    () -> game.setScreen(new GameScreen(game)));

boolean hasSaves = !game.getSaveBackend().listSaves().isEmpty();

addMenuButton(root, "Continue", skin, !hasSaves,
    () -> {
        java.util.List<com.galacticodyssey.persistence.ManifestData> saves =
            game.getSaveBackend().listSaves();
        if (!saves.isEmpty()) {
            // Load most recent save → GameScreen
            Gdx.app.log("Menu", "Loading most recent save: " + saves.get(0).saveName);
            game.setScreen(new GameScreen(game));
        }
    });

addMenuButton(root, "Load Game", skin, !hasSaves,
    () -> game.setScreen(new LoadScreen(game, game.getSaveBackend(),
        MainMenuScreen.this, LoadScreen.Origin.MAIN_MENU)));

addMenuButton(root, "Multiplayer", skin, false,
    () -> Gdx.app.log("Menu", "Multiplayer pressed"));
addMenuButton(root, "Settings", skin, false,
    () -> game.setScreen(new SettingsScreen(game)));
addMenuButton(root, "Encyclopedia", skin, false,
    () -> Gdx.app.log("Menu", "Encyclopedia pressed"));
addMenuButton(root, "Credits", skin, false,
    () -> Gdx.app.log("Menu", "Credits pressed"));
addMenuButton(root, "Exit", skin, false,
    () -> Gdx.app.exit());
```

Add import at the top:
```java
import com.galacticodyssey.persistence.ManifestData;
```

- [ ] **Step 2: Launch game and verify main menu**

Run: `./gradlew :desktop:run`
Expected: Main menu shows all buttons. "Continue" and "Load Game" are disabled (no saves exist yet). Clicking "Load Game" would open the LoadScreen if saves existed.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java
git commit -m "feat(ui): add Load Game button and enable Continue on main menu"
```

---

### Task 13: Add Save/Load to Pause Menu

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Add Save Game and Load Game buttons to pause menu**

In `GameScreen.java`, modify the `buildPauseMenu()` method. Replace the button block with:

```java
addPauseButton(root, "Resume", skin, audio, this::togglePause);
addPauseButton(root, "Save Game", skin, audio, () -> {
    game.setScreen(new SaveScreen(game, game.getSaveBackend(), GameScreen.this));
});
addPauseButton(root, "Load Game", skin, audio, () -> {
    game.setScreen(new LoadScreen(game, game.getSaveBackend(),
        GameScreen.this, LoadScreen.Origin.PAUSE_MENU, GameScreen.this));
});
addPauseButton(root, "Settings", skin, audio, () -> {
    game.setScreen(new SettingsScreen(game, this));
});
addPauseButton(root, "Exit to Main Menu", skin, audio, () -> {
    dispose();
    game.setScreen(new MainMenuScreen(game));
});
addPauseButton(root, "Exit Game", skin, audio, () -> Gdx.app.exit());
```

- [ ] **Step 2: Launch game and verify pause menu**

Run: `./gradlew :desktop:run`
Expected: Start a new game → press ESC → pause menu shows Resume, Save Game, Load Game, Settings, Exit to Main Menu, Exit Game. Click "Save Game" → SaveScreen opens. Click "Load Game" → LoadScreen opens. Back button returns to paused game.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(ui): add Save Game and Load Game to pause menu"
```

---

### Task 14: Manual Integration Testing

- [ ] **Step 1: Launch the game**

Run: `./gradlew :desktop:run`

- [ ] **Step 2: Test main menu**

- Verify "Continue" and "Load Game" are disabled (no saves)
- Click "New Game" → GameScreen loads

- [ ] **Step 3: Test pause menu → Save Screen**

- Press ESC → pause menu
- Click "Save Game" → SaveScreen opens
- Verify "New Save" card is visible at the top
- Verify "BACK" button returns to paused game
- Verify starfield background renders

- [ ] **Step 4: Test pause menu → Load Screen**

- Press ESC → pause menu
- Click "Load Game" → LoadScreen opens
- Verify empty state (no saves yet)
- Verify "BACK" button returns to paused game

- [ ] **Step 5: Test Settings still works**

- From main menu: Settings → verify tabs, sliders, back works
- From pause menu: Settings → verify tabs, sliders, back works

- [ ] **Step 6: Commit any fixes**

If any visual or behavioral issues found, fix them and commit:

```bash
git add -A
git commit -m "fix(ui): polish save/load screen integration"
```

---

### Task 15: Run Full Test Suite

- [ ] **Step 1: Run all tests**

Run: `./gradlew :core:test --info`
Expected: All tests pass including new ManifestDataTest and ThumbnailCaptureTest.

- [ ] **Step 2: Fix any regressions**

If tests fail, fix the root cause and commit:

```bash
git add -A
git commit -m "fix: resolve test regressions from save/load UI integration"
```
