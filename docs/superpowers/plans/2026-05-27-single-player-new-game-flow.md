# Single Player New Game Flow — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the main menu into a single-player entry flow: sub-menu → galaxy config screen → async flavour-text loading screen → seeded world with player on a terran planet, starter ship nearby.

**Architecture:** A `GameSession` POJO carries config (seed, galaxy type, size, region) through the screen chain. `GalaxyGenerationPipeline.run(session)` executes on a worker thread in `GalaxyLoadingScreen`, populating the session's galaxy/planet/spawn fields. `GameScreen` accepts the completed session and uses it instead of hardcoded values.

**Tech Stack:** libGDX 1.13+, Scene2D UI, Ashley ECS, existing `GalaxyManager` / `StarSystemGenerator` / `OrbitalLayoutGenerator` / `PlanetGenerator` / `TerrainGenerator` infrastructure.

**Spec:** `docs/superpowers/specs/2026-05-27-single-player-new-game-flow-design.md`

---

## File Map

| Action | Path |
|--------|------|
| Create | `core/src/main/java/com/galacticodyssey/galaxy/GalaxySize.java` |
| Create | `core/src/main/java/com/galacticodyssey/galaxy/StartingRegion.java` |
| Create | `core/src/main/java/com/galacticodyssey/data/GameSession.java` |
| Create | `core/src/main/java/com/galacticodyssey/galaxy/GalaxyGenerationPipeline.java` |
| Create | `core/src/main/java/com/galacticodyssey/ui/SinglePlayerMenuScreen.java` |
| Create | `core/src/main/java/com/galacticodyssey/ui/GameSetupScreen.java` |
| Create | `core/src/main/java/com/galacticodyssey/ui/GalaxyLoadingScreen.java` |
| Modify | `core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java` |
| Modify | `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` |
| Create | `core/src/test/java/com/galacticodyssey/data/GameSessionTest.java` |
| Create | `core/src/test/java/com/galacticodyssey/galaxy/GalaxyGenerationPipelineTest.java` |

---

## Task 1: GalaxySize and StartingRegion enums

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxySize.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StartingRegion.java`

- [ ] **Step 1: Create GalaxySize.java**

```java
package com.galacticodyssey.galaxy;

public enum GalaxySize {
    SMALL(500),
    MEDIUM(2000),
    LARGE(10000);

    public final int starCount;

    GalaxySize(int starCount) {
        this.starCount = starCount;
    }
}
```

- [ ] **Step 2: Create StartingRegion.java**

Maps each player-facing region choice to the internal `GalaxyRegion` value and a normalised radial position used to aim the initial chunk load.

```java
package com.galacticodyssey.galaxy;

public enum StartingRegion {
    CORE(GalaxyRegion.CORE, 0.07),
    INNER_RIM(GalaxyRegion.INNER_RIM, 0.275),
    FRONTIER(GalaxyRegion.OUTER_RIM, 0.65);

    public final GalaxyRegion galaxyRegion;
    /** Fraction of galaxy radius used to position the initial chunk-load view. */
    public final double normalizedRadius;

    StartingRegion(GalaxyRegion galaxyRegion, double normalizedRadius) {
        this.galaxyRegion = galaxyRegion;
        this.normalizedRadius = normalizedRadius;
    }
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :core:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/GalaxySize.java \
        core/src/main/java/com/galacticodyssey/galaxy/StartingRegion.java
git commit -m "feat(galaxy): add GalaxySize and StartingRegion enums"
```

---

## Task 2: GameSession POJO

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/data/GameSessionTest.java`
- Create: `core/src/main/java/com/galacticodyssey/data/GameSession.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.galacticodyssey.data;

import com.galacticodyssey.galaxy.GalaxySize;
import com.galacticodyssey.galaxy.GalaxyType;
import com.galacticodyssey.galaxy.StartingRegion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {

    @Test
    void configFieldsPreservedAfterConstruction() {
        GameSession s = new GameSession(999L, "Test Galaxy",
            GalaxyType.SPIRAL, GalaxySize.SMALL, StartingRegion.INNER_RIM);
        assertEquals(999L, s.seed);
        assertEquals("Test Galaxy", s.galaxyName);
        assertEquals(GalaxyType.SPIRAL, s.galaxyType);
        assertEquals(GalaxySize.SMALL, s.galaxySize);
        assertEquals(StartingRegion.INNER_RIM, s.startingRegion);
        assertFalse(s.complete);
        assertFalse(s.failed);
        assertNull(s.error);
        assertTrue(s.log.isEmpty());
    }

    @Test
    void terrainSeedFormulaIsDeterministic() {
        long seed = 777L;
        float mass = 0.8f;
        float day = 100.0f;
        long s1 = seed ^ Long.reverse(Float.floatToRawIntBits(mass)) ^ Float.floatToRawIntBits(day);
        long s2 = seed ^ Long.reverse(Float.floatToRawIntBits(mass)) ^ Float.floatToRawIntBits(day);
        assertEquals(s1, s2);
    }

    @Test
    void terrainSeedFormulaDistinctForDistinctPlanets() {
        long seed = 12345L;
        long s1 = seed ^ Long.reverse(Float.floatToRawIntBits(1.0f)) ^ Float.floatToRawIntBits(24.0f);
        long s2 = seed ^ Long.reverse(Float.floatToRawIntBits(1.5f)) ^ Float.floatToRawIntBits(36.0f);
        assertNotEquals(s1, s2);
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.GameSessionTest"
```

Expected: compilation error — `GameSession` does not exist yet.

- [ ] **Step 3: Create GameSession.java**

```java
package com.galacticodyssey.data;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.GalaxyManager;
import com.galacticodyssey.galaxy.GalaxySize;
import com.galacticodyssey.galaxy.GalaxyType;
import com.galacticodyssey.galaxy.StarSystem;
import com.galacticodyssey.galaxy.StartingRegion;
import com.galacticodyssey.planet.Planet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameSession {

    // --- Input config (set by GameSetupScreen) ---
    public final long seed;
    public final String galaxyName;
    public final GalaxyType galaxyType;
    public final GalaxySize galaxySize;
    public final StartingRegion startingRegion;

    // --- Generated results (populated by GalaxyGenerationPipeline on worker thread) ---
    public volatile GalaxyManager galaxy;
    public volatile StarSystem startingSystem;
    public volatile Planet startingPlanet;
    public volatile long terrainSeed;
    public volatile Vector3 playerSpawnPos;
    public volatile Vector3 shipSpawnPos;

    // --- Thread coordination ---
    public volatile boolean complete = false;
    public volatile boolean failed = false;
    public volatile Throwable error = null;
    public final List<String> log = Collections.synchronizedList(new ArrayList<>());

    public GameSession(long seed, String galaxyName, GalaxyType galaxyType,
                       GalaxySize galaxySize, StartingRegion startingRegion) {
        this.seed = seed;
        this.galaxyName = galaxyName;
        this.galaxyType = galaxyType;
        this.galaxySize = galaxySize;
        this.startingRegion = startingRegion;
    }
}
```

- [ ] **Step 4: Run tests — confirm pass**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.GameSessionTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/GameSession.java \
        core/src/test/java/com/galacticodyssey/data/GameSessionTest.java
git commit -m "feat(data): add GameSession POJO with thread-coordination fields"
```

---

## Task 3: GalaxyGenerationPipeline

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/galaxy/GalaxyGenerationPipelineTest.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyGenerationPipeline.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.galacticodyssey.galaxy;

import com.galacticodyssey.data.GameSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GalaxyGenerationPipelineTest {

    private static GameSession session(long seed) {
        return new GameSession(seed, "TestGalaxy",
            GalaxyType.SPIRAL, GalaxySize.SMALL, StartingRegion.INNER_RIM);
    }

    @Test
    void sameSeedPicksSameStartingSystemAndPlanet() {
        GameSession s1 = session(777L);
        GameSession s2 = session(777L);
        GalaxyGenerationPipeline.run(s1);
        GalaxyGenerationPipeline.run(s2);
        assertEquals(s1.startingSystem.uniqueId, s2.startingSystem.uniqueId,
            "Same seed must select the same star");
        assertEquals(s1.startingPlanet.seed, s2.startingPlanet.seed,
            "Same seed must select the same planet");
        assertEquals(s1.terrainSeed, s2.terrainSeed,
            "Same seed must derive the same terrain seed");
    }

    @Test
    void pipelineAlwaysProducesStartingPlanet() {
        for (long seed = 1L; seed <= 20L; seed++) {
            GameSession s = session(seed);
            GalaxyGenerationPipeline.run(s);
            assertNotNull(s.startingPlanet, "No planet for seed " + seed);
            assertNotNull(s.playerSpawnPos, "No spawn pos for seed " + seed);
            assertNotNull(s.shipSpawnPos, "No ship spawn pos for seed " + seed);
        }
    }

    @Test
    void pipelineLogsAllFivePhases() {
        GameSession s = session(42L);
        GalaxyGenerationPipeline.run(s);
        assertEquals(5, s.log.size(), "Expected exactly 5 log entries");
    }

    @Test
    void differentSeedsProduceDifferentTerrainSeeds() {
        GameSession s1 = session(1L);
        GameSession s2 = session(2L);
        GalaxyGenerationPipeline.run(s1);
        GalaxyGenerationPipeline.run(s2);
        assertNotEquals(s1.terrainSeed, s2.terrainSeed,
            "Different seeds should produce different terrain seeds");
    }

    @Test
    void shipSpawnIs75mEastOfPlayer() {
        GameSession s = session(100L);
        GalaxyGenerationPipeline.run(s);
        assertEquals(75f, s.shipSpawnPos.x - s.playerSpawnPos.x, 0.01f,
            "Ship should be 75m east of player");
        assertEquals(0f, s.shipSpawnPos.z - s.playerSpawnPos.z, 0.01f,
            "Ship Z should match player Z");
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyGenerationPipelineTest"
```

Expected: compilation error — `GalaxyGenerationPipeline` does not exist yet.

- [ ] **Step 3: Create GalaxyGenerationPipeline.java**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.data.GameSession;
import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.data.names.SpaceNameGenerator;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetGenerator;
import com.galacticodyssey.planet.PlanetType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class GalaxyGenerationPipeline {

    private static final int TERRAIN_VERTS = 257;
    private static final float TERRAIN_SIZE = 500f;

    private GalaxyGenerationPipeline() {}

    /** Runs all five generation phases synchronously, populating {@code session}'s result fields. */
    public static void run(GameSession session) {

        // Phase 1 — galaxy layout
        GalaxyConfig config = buildConfig(session.galaxySize, session.galaxyType);
        GalaxyManager manager = new GalaxyManager(session.seed, config);
        session.galaxy = manager;
        session.log.add("Mapping " + session.galaxySize.starCount
            + " star systems across " + session.galaxyName + "…");

        // Phase 2 — find starting G/K main-sequence star deterministically
        Random seedRng = new Random(session.seed);
        double angle = seedRng.nextDouble() * 2 * Math.PI;
        double viewRadius = session.startingRegion.normalizedRadius * config.radiusLY;
        double viewX = Math.cos(angle) * viewRadius;
        double viewY = Math.sin(angle) * viewRadius;
        manager.updateView(viewX, viewY, config.chunkSizeLY * 3f);

        StarSystemGenerator starGen = new StarSystemGenerator(session.seed);
        GalaxyRegion targetRegion = session.startingRegion.galaxyRegion;
        SpaceNameGenerator nameGen = new SpaceNameGenerator();

        List<StarPosition> candidates = collectCandidates(manager, starGen, targetRegion);
        candidates.sort(Comparator.comparingLong(s -> s.uniqueId));
        int chosenIdx = (int) (Math.abs(session.seed) % candidates.size());
        StarPosition chosenStar = candidates.get(chosenIdx);
        StarSystem chosenSystem = starGen.generate(chosenStar, targetRegion);
        session.startingSystem = chosenSystem;

        String starName = nameGen.starName(new Random(chosenStar.uniqueId ^ session.seed));
        session.log.add("Habitable zone identified around "
            + starName + " (" + chosenSystem.spectralClass + ")…");

        // Phase 3 — terran planet selection
        OrbitalLayoutGenerator layoutGen = new OrbitalLayoutGenerator();
        List<OrbitalSlot> slots = layoutGen.generate(chosenSystem);
        PlanetGenerator planetGen = new PlanetGenerator(session.seed);

        Planet planet = findPlanet(slots, chosenSystem, planetGen, PlanetType.TERRAN);
        if (planet == null) planet = findPlanet(slots, chosenSystem, planetGen, PlanetType.ARID);
        if (planet == null) planet = findPlanet(slots, chosenSystem, planetGen, PlanetType.OCEAN);
        if (planet == null) {
            for (OrbitalSlot slot : slots) {
                Planet p = planetGen.generate(slot, chosenSystem);
                if (p.type.hasSurface()) { planet = p; break; }
            }
        }
        if (planet == null) {
            planet = planetGen.generate(slots.get(0), chosenSystem);
        }
        session.startingPlanet = planet;

        String planetName = nameGen.planetName(new Random(planet.seed));
        session.log.add("Terran world " + planetName + " selected as origin point…");

        // Phase 4 — terrain seed derived from planet properties
        session.terrainSeed = session.seed
            ^ Long.reverse(Float.floatToRawIntBits(planet.mass))
            ^ Float.floatToRawIntBits(planet.dayLength);
        session.log.add("Surveying surface of " + planetName + "…");

        // Phase 5 — spawn coordinates
        float[] hmap = TerrainGenerator.generateHeightmap(
            TERRAIN_VERTS, TERRAIN_VERTS, TERRAIN_SIZE, TERRAIN_SIZE, session.terrainSeed);
        float groundH = TerrainGenerator.getHeightAt(
            hmap, TERRAIN_VERTS, TERRAIN_VERTS, TERRAIN_SIZE, TERRAIN_SIZE, 0f, 0f);
        session.playerSpawnPos = new Vector3(0f, groundH + 2f, 0f);
        session.shipSpawnPos = new Vector3(75f, groundH + 0.5f, 0f);
        session.log.add("Origin point confirmed. Welcome to " + session.galaxyName + ".");
    }

    private static List<StarPosition> collectCandidates(
            GalaxyManager manager, StarSystemGenerator starGen, GalaxyRegion region) {

        List<StarPosition> gk = new ArrayList<>();
        List<StarPosition> mainSeq = new ArrayList<>();
        List<StarPosition> any = new ArrayList<>();

        for (StarPosition star : manager.getLoadedStars()) {
            StarSystem sys = starGen.generate(star, region);
            any.add(star);
            if (sys.luminosityClass == LuminosityClass.MAIN_SEQUENCE) {
                mainSeq.add(star);
                if (sys.spectralClass == SpectralClass.G || sys.spectralClass == SpectralClass.K) {
                    gk.add(star);
                }
            }
        }

        if (!gk.isEmpty()) return gk;
        if (!mainSeq.isEmpty()) return mainSeq;
        return any;
    }

    private static Planet findPlanet(List<OrbitalSlot> slots, StarSystem system,
                                     PlanetGenerator gen, PlanetType type) {
        for (OrbitalSlot slot : slots) {
            Planet p = gen.generate(slot, system);
            if (p.type == type) return p;
        }
        return null;
    }

    private static GalaxyConfig buildConfig(GalaxySize size, GalaxyType type) {
        GalaxyConfig cfg = new GalaxyConfig();
        cfg.type = type;
        cfg.targetStarCount = size.starCount;
        return cfg;
    }
}
```

- [ ] **Step 4: Run tests — confirm pass**

```bash
./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyGenerationPipelineTest"
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Run the full test suite to check for regressions**

```bash
./gradlew :core:test
```

Expected: `BUILD SUCCESSFUL`, no new failures.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/GalaxyGenerationPipeline.java \
        core/src/test/java/com/galacticodyssey/galaxy/GalaxyGenerationPipelineTest.java
git commit -m "feat(galaxy): add GalaxyGenerationPipeline with TDD tests"
```

---

## Task 4: SinglePlayerMenuScreen

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/SinglePlayerMenuScreen.java`

This screen follows the exact same structure as `MainMenuScreen` (starfield background, centred button table, same `addMenuButton` helper). Read `MainMenuScreen.java` before implementing to copy the rendering approach.

- [ ] **Step 1: Create SinglePlayerMenuScreen.java**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;

public class SinglePlayerMenuScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private final GalacticOdyssey game;
    private final Skin skin;
    private final AudioManager audioManager;
    private final Stage stage;
    private final StarfieldBackground starfield;
    private final OrthographicCamera backgroundCamera;

    public SinglePlayerMenuScreen(GalacticOdyssey game) {
        this.game = game;
        this.skin = game.getSkin();
        this.audioManager = game.getAudioManager();
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.backgroundCamera = new OrthographicCamera();
        this.starfield = new StarfieldBackground(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.center();

        root.add(new Label("SINGLE PLAYER", skin, "title")).padBottom(50).row();

        boolean hasSaves = !game.getSaveBackend().listSaves().isEmpty();

        addMenuButton(root, "New Game", false,
            () -> game.setScreen(new GameSetupScreen(game)));

        addMenuButton(root, "Continue", !hasSaves, () -> {
            java.util.List<com.galacticodyssey.persistence.ManifestData> saves =
                game.getSaveBackend().listSaves();
            if (!saves.isEmpty()) {
                game.setScreen(new GameScreen(game));
            }
        });

        addMenuButton(root, "Load Game", !hasSaves,
            () -> game.setScreen(new LoadScreen(game, game.getSaveBackend(),
                SinglePlayerMenuScreen.this, LoadScreen.Origin.MAIN_MENU)));

        addMenuButton(root, "Back", false,
            () -> game.setScreen(new MainMenuScreen(game)));

        stage.addActor(root);
    }

    private void addMenuButton(Table table, String text, boolean disabled, Runnable action) {
        TextButton button = new TextButton(text, skin);
        button.setDisabled(disabled);
        button.setTransform(true);
        AudioManager audio = audioManager;
        button.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer == -1 && !button.isDisabled()) {
                    button.setOrigin(Align.center);
                    button.addAction(Actions.scaleTo(1.02f, 1.02f, 0.1f, Interpolation.smooth));
                    audio.playSound("audio/sfx/ui_hover.ogg");
                }
            }
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) button.addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.smooth));
            }
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (button.isDisabled()) return;
                audio.playSound("audio/sfx/ui_click.ogg");
                action.run();
            }
        });
        table.add(button).width(300).height(50).padBottom(12).row();
    }

    @Override public void show() { Gdx.input.setInputProcessor(stage); }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);
        starfield.update(delta);
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        backgroundCamera.setToOrtho(false, w, h);
        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(backgroundCamera.combined);
        batch.begin();
        starfield.render(batch);
        batch.end();
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        starfield.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }

    @Override
    public void dispose() {
        stage.dispose();
        starfield.dispose();
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :core:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/SinglePlayerMenuScreen.java
git commit -m "feat(ui): add SinglePlayerMenuScreen"
```

---

## Task 5: GameSetupScreen

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/GameSetupScreen.java`

- [ ] **Step 1: Create GameSetupScreen.java**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.data.GameSession;
import com.galacticodyssey.data.names.SpaceNameGenerator;
import com.galacticodyssey.galaxy.GalaxySize;
import com.galacticodyssey.galaxy.GalaxyType;
import com.galacticodyssey.galaxy.StartingRegion;

import java.util.Random;

public class GameSetupScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private static final String[] GALAXY_SUFFIXES =
        { "Expanse", "Galaxy", "Reaches", "Cluster", "Nebula", "Rift", "Arm", "Void" };

    private final GalacticOdyssey game;
    private final Skin skin;
    private final AudioManager audioManager;
    private final Stage stage;
    private final OrthographicCamera backgroundCamera;
    private final StarfieldBackground starfield;

    private TextField galaxyNameField;
    private TextField seedField;
    private SelectBox<String> galaxyTypeBox;

    // Size selection: 0=SMALL, 1=MEDIUM, 2=LARGE
    private int selectedSizeIndex = 1;
    private final TextButton[] sizeButtons = new TextButton[3];

    // Region selection: 0=CORE, 1=INNER_RIM, 2=FRONTIER
    private int selectedRegionIndex = 1;
    private final TextButton[] regionButtons = new TextButton[3];

    public GameSetupScreen(GalacticOdyssey game) {
        this.game = game;
        this.skin = game.getSkin();
        this.audioManager = game.getAudioManager();
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.backgroundCamera = new OrthographicCamera();
        this.starfield = new StarfieldBackground(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        buildUi();
    }

    private void buildUi() {
        long defaultSeed = System.nanoTime();
        String defaultGalaxyName = generateGalaxyName(defaultSeed);

        Table root = new Table();
        root.setFillParent(true);
        root.center();

        root.add(new Label("NEW GAME", skin, "title")).colspan(2).padBottom(30).row();

        // Galaxy Name
        root.add(new Label("Galaxy Name", skin)).right().padRight(10);
        galaxyNameField = new TextField(defaultGalaxyName, skin);
        root.add(galaxyNameField).width(400).row();

        // Seed
        root.add(new Label("Seed", skin)).right().padRight(10);
        seedField = new TextField(String.valueOf(defaultSeed), skin);
        root.add(seedField).width(400).row();

        // Galaxy Type
        root.add(new Label("Galaxy Type", skin)).right().padRight(10);
        galaxyTypeBox = new SelectBox<>(skin);
        galaxyTypeBox.setItems("Spiral", "Barred Spiral", "Elliptical", "Irregular");
        root.add(galaxyTypeBox).width(400).row();

        // Galaxy Size
        root.add(new Label("Galaxy Size", skin)).right().padRight(10);
        Table sizeRow = new Table();
        String[] sizeLabels = { "Small", "Medium", "Large" };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            sizeButtons[i] = new TextButton(sizeLabels[i], skin);
            if (i == selectedSizeIndex) sizeButtons[i].setColor(Color.YELLOW);
            sizeButtons[i].addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    selectedSizeIndex = idx;
                    for (int j = 0; j < 3; j++)
                        sizeButtons[j].setColor(j == idx ? Color.YELLOW : Color.WHITE);
                }
            });
            sizeRow.add(sizeButtons[i]).width(120).height(40).padRight(8);
        }
        root.add(sizeRow).row();

        // Starting Region
        root.add(new Label("Starting Region", skin)).right().padRight(10);
        Table regionRow = new Table();
        String[] regionLabels = { "Core", "Inner Rim", "Frontier" };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            regionButtons[i] = new TextButton(regionLabels[i], skin);
            if (i == selectedRegionIndex) regionButtons[i].setColor(Color.YELLOW);
            regionButtons[i].addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    selectedRegionIndex = idx;
                    for (int j = 0; j < 3; j++)
                        regionButtons[j].setColor(j == idx ? Color.YELLOW : Color.WHITE);
                }
            });
            regionRow.add(regionButtons[i]).width(120).height(40).padRight(8);
        }
        root.add(regionRow).row();

        // Buttons
        Table btnRow = new Table();
        TextButton createBtn = new TextButton("Create Game", skin);
        createBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                audioManager.playSound("audio/sfx/ui_click.ogg");
                GameSession session = buildSession();
                game.setScreen(new GalaxyLoadingScreen(game, session));
            }
        });
        TextButton backBtn = new TextButton("Back", skin);
        backBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                audioManager.playSound("audio/sfx/ui_click.ogg");
                game.setScreen(new SinglePlayerMenuScreen(game));
            }
        });
        btnRow.add(backBtn).width(200).height(50).padRight(20);
        btnRow.add(createBtn).width(200).height(50);
        root.add(btnRow).colspan(2).padTop(30).row();

        stage.addActor(root);
    }

    private GameSession buildSession() {
        long seed = parseSeed(seedField.getText());
        String name = galaxyNameField.getText().trim().isEmpty()
            ? generateGalaxyName(seed) : galaxyNameField.getText().trim();
        GalaxyType type = GalaxyType.values()[galaxyTypeBox.getSelectedIndex()];
        GalaxySize size = GalaxySize.values()[selectedSizeIndex];
        StartingRegion region = StartingRegion.values()[selectedRegionIndex];
        return new GameSession(seed, name, type, size, region);
    }

    private static long parseSeed(String text) {
        if (text == null || text.trim().isEmpty()) return System.nanoTime();
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return (long) text.trim().hashCode() * 2654435761L;
        }
    }

    private static String generateGalaxyName(long seed) {
        SpaceNameGenerator gen = new SpaceNameGenerator();
        String base = gen.factionName(new Random(seed));
        String suffix = GALAXY_SUFFIXES[(int) (Math.abs(seed) % GALAXY_SUFFIXES.length)];
        return base + " " + suffix;
    }

    @Override public void show() { Gdx.input.setInputProcessor(stage); }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);
        starfield.update(delta);
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        backgroundCamera.setToOrtho(false, w, h);
        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(backgroundCamera.combined);
        batch.begin(); starfield.render(batch); batch.end();
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        starfield.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }

    @Override public void dispose() {
        stage.dispose();
        starfield.dispose();
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :core:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameSetupScreen.java
git commit -m "feat(ui): add GameSetupScreen with seed, type, size, and region fields"
```

---

## Task 6: GalaxyLoadingScreen

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/GalaxyLoadingScreen.java`

The loading screen starts a worker thread from `show()`. The render loop polls `session.complete` and `session.failed` (both `volatile`) and animates new log lines as they appear. On completion it transitions to `GameScreen`; on failure it reveals a Back button.

- [ ] **Step 1: Create GalaxyLoadingScreen.java**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.data.GameSession;
import com.galacticodyssey.galaxy.GalaxyGenerationPipeline;

public class GalaxyLoadingScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final int PHASE_COUNT = 5;

    private final GalacticOdyssey game;
    private final GameSession session;
    private final Skin skin;
    private final Stage stage;
    private final OrthographicCamera camera;

    private ProgressBar progressBar;
    private Table logTable;
    private TextButton backButton;
    private Label errorLabel;

    private final Array<Label> logLabels = new Array<>();
    private int displayedLogCount = 0;
    private boolean transitioned = false;

    public GalaxyLoadingScreen(GalacticOdyssey game, GameSession session) {
        this.game = game;
        this.session = session;
        this.skin = game.getSkin();
        this.camera = new OrthographicCamera();
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.center();

        root.add(new Label(session.galaxyName, skin, "title")).padBottom(20).row();

        progressBar = new ProgressBar(0f, PHASE_COUNT, 1f, false, skin);
        root.add(progressBar).width(600).padBottom(30).row();

        logTable = new Table();
        root.add(logTable).padBottom(20).row();

        root.add(new Label("Please wait…", skin)).padBottom(30).row();

        errorLabel = new Label("", skin);
        errorLabel.setColor(Color.RED);
        errorLabel.setVisible(false);
        root.add(errorLabel).padBottom(10).row();

        backButton = new TextButton("Back", skin);
        backButton.setVisible(false);
        backButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new GameSetupScreen(game));
            }
        });
        root.add(backButton).width(200).height(50).row();

        stage.addActor(root);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        Thread worker = new Thread(() -> {
            try {
                GalaxyGenerationPipeline.run(session);
                session.complete = true;
            } catch (Throwable t) {
                session.error = t;
                session.failed = true;
                Gdx.app.error("GalaxyLoading", "Generation failed", t);
            }
        }, "galaxy-gen");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        // Fade in new log lines as they arrive
        int currentLogSize = session.log.size();
        while (displayedLogCount < currentLogSize) {
            String line = session.log.get(displayedLogCount);
            Label lbl = new Label(line, skin);
            lbl.getColor().a = 0f;
            lbl.addAction(Actions.fadeIn(0.5f));
            logTable.add(lbl).padBottom(4).row();
            logLabels.add(lbl);
            progressBar.setValue(displayedLogCount + 1);
            displayedLogCount++;
        }

        if (session.complete && !transitioned) {
            transitioned = true;
            game.setScreen(new GameScreen(game, session));
            return;
        }

        if (session.failed) {
            String msg = session.error != null ? session.error.getMessage() : "Unknown error";
            errorLabel.setText("Generation failed: " + msg + "\nTry a different seed.");
            errorLabel.setVisible(true);
            backButton.setVisible(true);
        }

        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }

    @Override public void dispose() {
        stage.dispose();
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :core:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GalaxyLoadingScreen.java
git commit -m "feat(ui): add GalaxyLoadingScreen with async generation and flavour text"
```

---

## Task 7: Rename MainMenuScreen "New Game" → "Single Player"

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java` (line 58–59)

- [ ] **Step 1: Read the file**

Read `core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java` to confirm current content at lines 58–59 before editing.

- [ ] **Step 2: Change the button text and target screen**

Replace:
```java
addMenuButton(root, "New Game", skin, false,
    () -> game.setScreen(new GameScreen(game)));
```

With:
```java
addMenuButton(root, "Single Player", skin, false,
    () -> game.setScreen(new SinglePlayerMenuScreen(game)));
```

- [ ] **Step 3: Compile**

```bash
./gradlew :core:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run full tests**

```bash
./gradlew :core:test
```

Expected: `BUILD SUCCESSFUL`, no failures.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/MainMenuScreen.java
git commit -m "feat(ui): rename main menu 'New Game' to 'Single Player', wire to SinglePlayerMenuScreen"
```

---

## Task 8: Modify GameScreen to accept GameSession

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

`GameScreen` currently has one constructor and hardcoded seed/spawn. Add a `GameSession`-accepting constructor and use the session values when non-null, preserving the existing no-arg behaviour for the load-game flow.

- [ ] **Step 1: Read the file**

Read `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` lines 65–185 to confirm the current constructor and `initializeWorld()` before editing.

- [ ] **Step 2: Add the session field and new constructor**

After the class-level constants block and before the existing `public GameScreen(GalacticOdyssey game)` constructor, add:

```java
private final GameSession session; // null when using load-game flow
```

Change the existing constructor from:
```java
public GameScreen(GalacticOdyssey game) {
    this.game = game;
}
```

To:
```java
public GameScreen(GalacticOdyssey game) {
    this(game, null);
}

public GameScreen(GalacticOdyssey game, GameSession session) {
    this.game = game;
    this.session = session;
}
```

Also add the import at the top of the file:
```java
import com.galacticodyssey.data.GameSession;
```

- [ ] **Step 3: Use session.terrainSeed in initializeWorld()**

In `initializeWorld()`, replace:
```java
heightmap = TerrainGenerator.generateHeightmap(
    TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, TERRAIN_SEED);
```

With:
```java
long terrainSeed = (session != null) ? session.terrainSeed : TERRAIN_SEED;
heightmap = TerrainGenerator.generateHeightmap(
    TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, terrainSeed);
```

And replace:
```java
populatedWorld = WorldPopulator.populate(
    heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, TERRAIN_SEED);
```

With:
```java
populatedWorld = WorldPopulator.populate(
    heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, terrainSeed);
```

- [ ] **Step 4: Use session.playerSpawnPos for the player entity**

Replace:
```java
float spawnHeight = TerrainGenerator.getHeightAt(
    heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, 0, 0) + 2f;
gameWorld.createPlayerEntity(0, spawnHeight, 0);
```

With:
```java
if (session != null && session.playerSpawnPos != null) {
    gameWorld.createPlayerEntity(
        session.playerSpawnPos.x, session.playerSpawnPos.y, session.playerSpawnPos.z);
} else {
    float spawnHeight = TerrainGenerator.getHeightAt(
        heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, 0, 0) + 2f;
    gameWorld.createPlayerEntity(0, spawnHeight, 0);
}
```

- [ ] **Step 5: Spawn starter ship (session flow) or legacy test ships (load-game flow)**

Replace the entire block that spawns `smallShip`, `medShip`, and `largeShip` (currently lines ~152–170) with:

```java
shipFactory = new ShipFactory(gameWorld.getEngine(), gameWorld.getBulletPhysicsSystem());

if (session != null && session.shipSpawnPos != null) {
    float shipY = TerrainGenerator.getHeightAt(
        heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH,
        session.shipSpawnPos.x, session.shipSpawnPos.z) + 0.5f;
    Entity starterShip = shipFactory.createShip(
        session.seed, ShipSizeClass.SMALL,
        session.shipSpawnPos.x, shipY, session.shipSpawnPos.z);
    shipEntities.add(starterShip);
} else {
    float smallX = 10f, smallZ = 10f;
    float smallY = TerrainGenerator.getHeightAt(
        heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, smallX, smallZ) + 2f;
    shipEntities.add(shipFactory.createShip(42L, ShipSizeClass.SMALL, smallX, smallY, smallZ));

    float medX = 40f, medZ = 40f;
    float medY = TerrainGenerator.getHeightAt(
        heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, medX, medZ) + 4f;
    shipEntities.add(shipFactory.createShip(123L, ShipSizeClass.MEDIUM, medX, medY, medZ));

    float lgX = -60f, lgZ = -60f;
    float lgY = TerrainGenerator.getHeightAt(
        heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, lgX, lgZ) + 6f;
    shipEntities.add(shipFactory.createShip(999L, ShipSizeClass.LARGE, lgX, lgY, lgZ));
}
```

- [ ] **Step 6: Dispose the GalaxyManager in GameScreen.dispose()**

In the existing `dispose()` method, before the final brace, add:

```java
if (session != null && session.galaxy != null) {
    session.galaxy.dispose();
}
```

- [ ] **Step 7: Compile**

```bash
./gradlew :core:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Run full test suite**

```bash
./gradlew :core:test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(ui): GameScreen accepts GameSession for seeded planet spawn"
```

---

## Task 9: Final integration verification

- [ ] **Step 1: Run the full project build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Launch the game and walk through the flow**

Use the `run-galactic-odyssey` skill or launch directly:

```bash
./gradlew :desktop:run
```

Walk through:
1. Main menu shows "Single Player" (not "New Game")
2. Click "Single Player" → `SinglePlayerMenuScreen` with New Game / Continue (greyed if no saves) / Load Game / Back
3. Click "New Game" → `GameSetupScreen` with all fields populated
4. Change the seed to `12345` and click "Create Game"
5. `GalaxyLoadingScreen` appears; flavour text lines appear one-by-one
6. Game transitions to `GameScreen`; player is on a planet surface
7. One SMALL ship is visible ~75m to the east

- [ ] **Step 3: Verify Back navigation**

From `GameSetupScreen`, click "Back" → returns to `SinglePlayerMenuScreen`.
From `SinglePlayerMenuScreen`, click "Back" → returns to `MainMenuScreen`.

- [ ] **Step 4: Verify seed determinism**

Start a game with seed `99999`. Note the terrain shape. Exit to main menu. Start a new game with the same seed `99999`. The terrain should be identical.

- [ ] **Step 5: Commit if no issues found**

```bash
git add -A
git commit -m "test: verify single-player new-game flow integration"
```
