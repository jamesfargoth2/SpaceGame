# Ship Procgen Phase 1a — Style System Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make procedurally generated ships vary their silhouette shape and palette by faction style, driven by data-driven `HullStyle` archetypes resolved from a faction's ethos or an explicit binding — using the existing **lofted** hull generator only.

**Architecture:** Introduce a `HullStyle` value object (loaded from JSON via a `HullStyleRegistry`) that constrains the random ranges the existing `ShipHullGenerator` already samples (cross-section exponent, aspect bias, spine curvature, panel inset) plus supplies the color palette. A new `ShipGenerationConfig` threads faction/role/condition through `ShipFactory`, which resolves a style and passes it to the generator. The existing 4-arg `createShip` and all current generator behavior are preserved by a `HullStyle.defaultStyle()` that replicates today's hardcoded constants.

**Tech Stack:** Java 17, libGDX (`com.badlogic.gdx.utils.JsonReader`/`JsonValue`, `com.badlogic.gdx.graphics.Color`, `MathUtils`), Ashley ECS, JUnit 5.

**Scope note:** This is plan **1a of the Phase-1 trilogy**. Plan 1b adds the 11 signature-feature generators; plan 1c adds the `FacetedHullGenerator` (Null-System, zeeLee) and switches the `ISOLATIONIST` ethos mapping to `null_sentinel` and adds the `zeelee` explicit binding. In 1a, `FACETED` styles are not yet generable; `ShipFactory` falls back to the lofted path for any style and the JSON ships only lofted styles.

**Spec:** `docs/superpowers/specs/2026-05-28-ship-procgen-improvement-design.md` (§2, §3.1–3.3, §3.5–3.6).

---

## File Structure

**New files:**
- `core/src/main/java/com/galacticodyssey/ship/GeneratorType.java` — enum {LOFTED, FACETED}.
- `core/src/main/java/com/galacticodyssey/ship/ShipRole.java` — enum {WARSHIP, MERCHANT, PIRATE, SCOUT, CIVILIAN}.
- `core/src/main/java/com/galacticodyssey/ship/HullStyle.java` — value object: id, generatorType, shape ranges, palette, `ageless`; plus `defaultStyle()`.
- `core/src/main/java/com/galacticodyssey/ship/HullStyleRegistry.java` — loads styles + ethos map + explicit faction map; `resolve(FactionData)`, `get(String)`.
- `core/src/main/java/com/galacticodyssey/ship/ShipGenerationConfig.java` — generation inputs + `defaults(...)`.
- `core/src/main/resources/data/ships/hull_styles.json` — 6 lofted styles.
- `core/src/main/resources/data/ships/ethos_style_map.json` — ethos → styleId.
- `core/src/main/resources/data/ships/faction_styles.json` — explicit factionId → styleId.
- Tests under `core/src/test/java/com/galacticodyssey/ship/`: `HullStyleTest`, `ShipColorPaletteStyleTest`, `ShipHullGeneratorStyleTest`, `HullStyleRegistryTest`, `ShipGenerationConfigTest`. Plus `FactionDataStyleTest` under `core/src/test/java/com/galacticodyssey/galaxy/faction/`.

**Modified files:**
- `core/src/main/java/com/galacticodyssey/ship/ShipColorPalette.java` — add `(long seed, HullStyle style)` constructor; keep `(long seed)` delegating to `defaultStyle()`.
- `core/src/main/java/com/galacticodyssey/ship/ShipHullGenerator.java` — add `generate(ShipBlueprint, HullStyle)`; keep `generate(ShipBlueprint)` delegating to `defaultStyle()`; pull shape ranges + palette from the style.
- `core/src/main/java/com/galacticodyssey/galaxy/faction/FactionData.java` — add `styleId` field + constructor overload (old constructor delegates with `null`).
- `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java` — add `setHullStyleRegistry(...)`, `createShip(ShipGenerationConfig, x, y, z)`; existing 4-arg overload delegates with defaults.

---

## Task 1: GeneratorType and ShipRole enums

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/GeneratorType.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ShipRole.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/HullStyleTest.java` (created here, expanded in Task 2)

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/ship/HullStyleTest.java`:

```java
package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class HullStyleTest {

    @Test
    void generatorTypeHasLoftedAndFaceted() {
        assertEquals(2, GeneratorType.values().length);
        assertEquals(GeneratorType.LOFTED, GeneratorType.valueOf("LOFTED"));
        assertEquals(GeneratorType.FACETED, GeneratorType.valueOf("FACETED"));
    }

    @Test
    void shipRoleHasFiveValues() {
        assertEquals(5, ShipRole.values().length);
        assertEquals(ShipRole.WARSHIP, ShipRole.valueOf("WARSHIP"));
        assertEquals(ShipRole.CIVILIAN, ShipRole.valueOf("CIVILIAN"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.HullStyleTest"`
Expected: FAIL — `GeneratorType` / `ShipRole` do not exist (compile error).

- [ ] **Step 3: Write minimal implementation**

Create `core/src/main/java/com/galacticodyssey/ship/GeneratorType.java`:

```java
package com.galacticodyssey.ship;

/** Selects which hull mesh generation path a {@link HullStyle} uses. */
public enum GeneratorType {
    /** Smooth lofted spline hull (existing {@link ShipHullGenerator}). */
    LOFTED,
    /** Flat-shaded faceted / crystalline hull (FacetedHullGenerator, added in plan 1c). */
    FACETED
}
```

Create `core/src/main/java/com/galacticodyssey/ship/ShipRole.java`:

```java
package com.galacticodyssey.ship;

/** Functional role of a ship; biases loadout and (later) hull style selection. */
public enum ShipRole {
    WARSHIP,
    MERCHANT,
    PIRATE,
    SCOUT,
    CIVILIAN
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.HullStyleTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/GeneratorType.java core/src/main/java/com/galacticodyssey/ship/ShipRole.java core/src/test/java/com/galacticodyssey/ship/HullStyleTest.java
git commit -m "feat(ship): add GeneratorType and ShipRole enums"
```

---

## Task 2: HullStyle value object + defaultStyle()

`HullStyle` carries the shape ranges that constrain the generator and the palette. `defaultStyle()` MUST replicate the current hardcoded constants so existing generator behavior is unchanged when no style is supplied.

Current constants being replicated (from `ShipHullGenerator`/`ShipColorPalette`):
- section exponent range: `2.2`–`4.0` (`generateCrossSections`: `2.2f + rng*1.8f`)
- aspect bias range: `0.7`–`1.3` (`0.7f + rng*0.6f`)
- spine curvature multiplier: `1.0` (current spine uses `len*0.3` ctrl offset, `len*0.06` yVar, `len*0.04` xVar)
- panel inset scale: `0.015` (`PANEL_INSET`)
- base/accent/glow colors: the arrays in `ShipColorPalette`.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/HullStyle.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/HullStyleTest.java` (extend)

- [ ] **Step 1: Write the failing test (extend HullStyleTest)**

Add these imports and methods to `HullStyleTest`:

```java
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

```java
    @Test
    void defaultStyleReplicatesCurrentConstants() {
        HullStyle s = HullStyle.defaultStyle();
        assertEquals(GeneratorType.LOFTED, s.generatorType);
        assertEquals(2.2f, s.sectionExponentMin, 1e-4);
        assertEquals(4.0f, s.sectionExponentMax, 1e-4);
        assertEquals(0.7f, s.aspectBiasMin, 1e-4);
        assertEquals(1.3f, s.aspectBiasMax, 1e-4);
        assertEquals(1.0f, s.spineCurvature, 1e-4);
        assertEquals(0.015f, s.panelInsetScale, 1e-4);
        assertFalse(s.ageless);
    }

    @Test
    void defaultStyleHasNonEmptyPalettes() {
        HullStyle s = HullStyle.defaultStyle();
        assertTrue(s.baseColors.length > 0);
        assertTrue(s.accentColors.length > 0);
        assertTrue(s.glowColors.length > 0);
        // each color is an RGB triple
        assertEquals(3, s.baseColors[0].length);
    }

    @Test
    void constructorStoresFields() {
        float[][] base = {{0.1f, 0.2f, 0.3f}};
        float[][] accent = {{0.4f, 0.5f, 0.6f}};
        float[][] glow = {{0.7f, 0.8f, 0.9f}};
        HullStyle s = new HullStyle("vaun", GeneratorType.LOFTED,
                3.5f, 4.5f, 0.8f, 1.0f, 0.4f, 0.03f,
                base, accent, glow, true);
        assertEquals("vaun", s.id);
        assertEquals(3.5f, s.sectionExponentMin, 1e-4);
        assertTrue(s.ageless);
        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, s.baseColors[0], 1e-4f);
    }
```

Add `import static org.junit.jupiter.api.Assertions.assertFalse;` as well.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.HullStyleTest"`
Expected: FAIL — `HullStyle` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `core/src/main/java/com/galacticodyssey/ship/HullStyle.java`:

```java
package com.galacticodyssey.ship;

/**
 * Data-driven visual archetype for a faction's ships. Constrains the random
 * ranges the hull generator samples and supplies the color palette.
 *
 * <p>Colors are stored as RGB triples ({@code float[3]}, channels 0..1) for easy
 * JSON loading. {@link #defaultStyle()} replicates the legacy hardcoded constants
 * so generation is unchanged when no style is supplied.
 */
public final class HullStyle {

    public final String id;
    public final GeneratorType generatorType;

    // Lofted shape levers
    public final float sectionExponentMin;   // superellipse exponent (2=round, >2 boxy, <2 diamond)
    public final float sectionExponentMax;
    public final float aspectBiasMin;         // height/width bias
    public final float aspectBiasMax;
    public final float spineCurvature;        // 1.0 = legacy; >1 more curved, <1 straighter
    public final float panelInsetScale;       // recessed panel depth

    // Palette: each entry is {r, g, b} in 0..1
    public final float[][] baseColors;
    public final float[][] accentColors;
    public final float[][] glowColors;

    /** When true, the wear pass is skipped (ageless exotic hulls). */
    public final boolean ageless;

    public HullStyle(String id, GeneratorType generatorType,
                     float sectionExponentMin, float sectionExponentMax,
                     float aspectBiasMin, float aspectBiasMax,
                     float spineCurvature, float panelInsetScale,
                     float[][] baseColors, float[][] accentColors, float[][] glowColors,
                     boolean ageless) {
        this.id = id;
        this.generatorType = generatorType;
        this.sectionExponentMin = sectionExponentMin;
        this.sectionExponentMax = sectionExponentMax;
        this.aspectBiasMin = aspectBiasMin;
        this.aspectBiasMax = aspectBiasMax;
        this.spineCurvature = spineCurvature;
        this.panelInsetScale = panelInsetScale;
        this.baseColors = baseColors;
        this.accentColors = accentColors;
        this.glowColors = glowColors;
        this.ageless = ageless;
    }

    /** Replicates legacy hardcoded generation constants (no faction influence). */
    public static HullStyle defaultStyle() {
        float[][] base = {
            {0.6f, 0.6f, 0.62f}, {0.8f, 0.8f, 0.82f}, {0.2f, 0.25f, 0.35f},
            {0.25f, 0.32f, 0.22f}, {0.35f, 0.35f, 0.38f}, {0.45f, 0.42f, 0.4f},
        };
        float[][] accent = {
            {0.9f, 0.3f, 0.2f}, {0.2f, 0.5f, 0.9f}, {0.9f, 0.7f, 0.1f},
            {0.1f, 0.8f, 0.5f}, {0.8f, 0.4f, 0.0f}, {0.6f, 0.2f, 0.8f},
        };
        float[][] glow = {
            {0.3f, 0.5f, 1f}, {1f, 0.6f, 0.2f}, {0.9f, 0.9f, 1f},
        };
        return new HullStyle("default", GeneratorType.LOFTED,
                2.2f, 4.0f, 0.7f, 1.3f, 1.0f, 0.015f,
                base, accent, glow, false);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.HullStyleTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/HullStyle.java core/src/test/java/com/galacticodyssey/ship/HullStyleTest.java
git commit -m "feat(ship): add HullStyle value object with legacy-replicating defaultStyle"
```

---

## Task 3: ShipColorPalette style-driven palette

Refactor `ShipColorPalette` to pick colors from a `HullStyle`. Keep the `(long seed)` constructor delegating to `defaultStyle()` so existing callers and `ShipColorPaletteTest` are unaffected.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipColorPalette.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipColorPaletteStyleTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/ship/ShipColorPaletteStyleTest.java`:

```java
package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

class ShipColorPaletteStyleTest {

    private static HullStyle singleColorStyle() {
        float[][] base = {{0.10f, 0.20f, 0.30f}};
        float[][] accent = {{0.40f, 0.50f, 0.60f}};
        float[][] glow = {{0.70f, 0.80f, 0.90f}};
        return new HullStyle("test", GeneratorType.LOFTED,
                2.0f, 3.0f, 0.8f, 1.2f, 1.0f, 0.015f,
                base, accent, glow, false);
    }

    @Test
    void picksColorsFromStylePalette() {
        ShipColorPalette p = new ShipColorPalette(1234L, singleColorStyle());
        assertEquals(new Color(0.10f, 0.20f, 0.30f, 1f), p.baseColor);
        assertEquals(new Color(0.40f, 0.50f, 0.60f, 1f), p.accentColor);
        assertEquals(new Color(0.70f, 0.80f, 0.90f, 1f), p.engineGlowColor);
    }

    @Test
    void deterministicForSameSeedAndStyle() {
        HullStyle s = HullStyle.defaultStyle();
        ShipColorPalette a = new ShipColorPalette(99L, s);
        ShipColorPalette b = new ShipColorPalette(99L, s);
        assertEquals(a.baseColor, b.baseColor);
        assertEquals(a.accentColor, b.accentColor);
        assertEquals(a.engineGlowColor, b.engineGlowColor);
    }

    @Test
    void legacyConstructorStillWorks() {
        ShipColorPalette p = new ShipColorPalette(7L);
        assertTrue(p.baseColor.a == 1f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipColorPaletteStyleTest"`
Expected: FAIL — no `(long, HullStyle)` constructor.

- [ ] **Step 3: Write the implementation**

Replace the body of `core/src/main/java/com/galacticodyssey/ship/ShipColorPalette.java` with:

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.graphics.Color;
import java.util.Random;

/**
 * Deterministic color palette for a procedural ship hull, sourced from a
 * {@link HullStyle}. The same seed + style always yields an identical palette.
 */
public class ShipColorPalette {

    /** Primary hull plate color. */
    public final Color baseColor;
    /** Panel accent stripe color. */
    public final Color accentColor;
    /** Inset panel trim — a blend of base toward accent. */
    public final Color trimColor;
    /** Engine nozzle / exhaust glow color (emissive). */
    public final Color engineGlowColor;

    /** Legacy constructor — uses {@link HullStyle#defaultStyle()}. */
    public ShipColorPalette(long seed) {
        this(seed, HullStyle.defaultStyle());
    }

    /** Constructs the palette deterministically from {@code seed} and {@code style}. */
    public ShipColorPalette(long seed, HullStyle style) {
        Random rng = new Random(seed);
        baseColor   = pick(style.baseColors,  rng);
        accentColor = pick(style.accentColors, rng);
        trimColor   = new Color(baseColor).lerp(accentColor, 0.3f);
        engineGlowColor = pick(style.glowColors, rng);
    }

    private static Color pick(float[][] colors, Random rng) {
        float[] c = colors[rng.nextInt(colors.length)];
        return new Color(c[0], c[1], c[2], 1f);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipColorPaletteStyleTest" --tests "com.galacticodyssey.ship.ShipColorPaletteTest"`
Expected: PASS (both the new test and the existing `ShipColorPaletteTest`).

> If `ShipColorPaletteTest` asserts exact legacy color *selection* per seed, the
> selection order is preserved (base then accent then glow, single `Random(seed)`),
> so it must still pass. If it fails, read the failing assertion and confirm the
> default palette arrays/order match the originals before changing anything.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ShipColorPalette.java core/src/test/java/com/galacticodyssey/ship/ShipColorPaletteStyleTest.java
git commit -m "feat(ship): drive ShipColorPalette from HullStyle palette"
```

---

## Task 4: ShipHullGenerator style threading

Add `generate(ShipBlueprint, HullStyle)`. Keep `generate(ShipBlueprint)` delegating to `defaultStyle()`. Replace the literals for spine curvature, section exponent, aspect bias, panel inset, and palette with style-sourced values.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipHullGenerator.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipHullGeneratorStyleTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/ship/ShipHullGeneratorStyleTest.java`:

```java
package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShipHullGeneratorStyleTest {

    private static HullStyle boxyStyle() {
        float[][] base = {{0.5f, 0.5f, 0.5f}};
        float[][] accent = {{0.9f, 0.3f, 0.2f}};
        float[][] glow = {{1f, 0.6f, 0.2f}};
        // high exponent => boxy; low spine curvature => straight
        return new HullStyle("boxy", GeneratorType.LOFTED,
                3.8f, 4.2f, 0.8f, 1.0f, 0.2f, 0.03f,
                base, accent, glow, false);
    }

    private static HullStyle roundStyle() {
        float[][] base = {{0.5f, 0.5f, 0.5f}};
        float[][] accent = {{0.2f, 0.5f, 0.9f}};
        float[][] glow = {{0.3f, 0.5f, 1f}};
        // low exponent => round; high spine curvature => curvy
        return new HullStyle("round", GeneratorType.LOFTED,
                1.8f, 2.2f, 0.8f, 1.0f, 2.0f, 0.01f,
                base, accent, glow, false);
    }

    @Test
    void sameSeedAndStyleIsDeterministic() {
        ShipBlueprint bp = new ShipBlueprint(4242L, ShipSizeClass.SMALL);
        ShipHullGenerator g = new ShipHullGenerator();
        HullGeometry a = g.generate(bp, boxyStyle());
        HullGeometry b = g.generate(bp, boxyStyle());
        org.junit.jupiter.api.Assertions.assertArrayEquals(a.vertices, b.vertices, 0f);
    }

    @Test
    void boxyStyleIsWiderThanRoundAtSameSeed() {
        // Higher exponent fills the bounding cross-section more, so for the same
        // blueprint the boxy hull's averaged |x| extent exceeds the round one.
        ShipBlueprint bp = new ShipBlueprint(2024L, ShipSizeClass.SMALL);
        ShipHullGenerator g = new ShipHullGenerator();
        float boxyFill = averageAbsX(g.generate(bp, boxyStyle()));
        float roundFill = averageAbsX(g.generate(bp, roundStyle()));
        assertTrue(boxyFill > roundFill,
                "boxy avg|x|=" + boxyFill + " should exceed round avg|x|=" + roundFill);
    }

    private static float averageAbsX(HullGeometry hull) {
        int stride = hull.vertexStride;
        double sum = 0;
        int n = hull.vertexCount();
        for (int i = 0; i < n; i++) sum += Math.abs(hull.vertices[i * stride]);
        return (float) (sum / n);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipHullGeneratorStyleTest"`
Expected: FAIL — no `generate(ShipBlueprint, HullStyle)` overload.

- [ ] **Step 3: Implement the style overload**

In `ShipHullGenerator.java`, replace the legacy `generate(ShipBlueprint)` method (currently starting `public HullGeometry generate(ShipBlueprint blueprint) {` at line 47) so it delegates, and add the style overload. The new `generate(blueprint, style)` body is identical to the current method except: it seeds the palette from the style, and passes `style` into `generateSpine` and `generateCrossSections`.

```java
    public HullGeometry generate(ShipBlueprint blueprint) {
        return generate(blueprint, HullStyle.defaultStyle());
    }

    public HullGeometry generate(ShipBlueprint blueprint, HullStyle style) {
        Random rng = new Random(blueprint.seed + 1);
        ShipColorPalette palette = new ShipColorPalette(blueprint.seed, style);

        SpineCurve spine = generateSpine(blueprint, style, rng);
        List<CrossSection> sections = generateCrossSections(blueprint, style, rng);
        List<Float> tValues = generateTValues(sections.size());
```

Leave the remainder of the method body (everything from `// Build interpolated hull rings` onward) unchanged.

- [ ] **Step 4: Thread style into the spine helper**

Replace `generateSpine` (currently at line 543) signature and the three variance lines to scale by `style.spineCurvature`:

```java
    private SpineCurve generateSpine(ShipBlueprint blueprint, HullStyle style, Random rng) {
        float len        = blueprint.spineLength;
        float ctrlOffset = len * 0.3f;
        float yVar       = len * 0.06f * style.spineCurvature;
        float xVar       = len * 0.04f * style.spineCurvature;
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(
            (rng.nextFloat() - 0.5f) * 2f * xVar,
            rng.nextFloat() * yVar,
            -ctrlOffset);
        Vector3 p2 = new Vector3(
            (rng.nextFloat() - 0.5f) * 2f * xVar,
            rng.nextFloat() * yVar,
            -(len - ctrlOffset));
        Vector3 p3 = new Vector3(0, 0, -len);
        return new SpineCurve(p0, p1, p2, p3);
    }
```

- [ ] **Step 5: Thread style into the cross-section helper**

Replace `generateCrossSections` (currently at line 561) to pull exponent and aspect-bias ranges from the style:

```java
    private List<CrossSection> generateCrossSections(ShipBlueprint blueprint, HullStyle style, Random rng) {
        List<CrossSection> sections = new ArrayList<>();
        int count = blueprint.crossSectionCount;

        // Aspect ratio (height/width) biased by style
        float aspectBias = style.aspectBiasMin
                + rng.nextFloat() * (style.aspectBiasMax - style.aspectBiasMin);

        float expRange = style.sectionExponentMax - style.sectionExponentMin;

        for (int i = 0; i < count; i++) {
            float frac     = (float) i / (count - 1);
            float envelope = computeEnvelope(frac);

            float w   = blueprint.maxWidth  * envelope * (0.85f + rng.nextFloat() * 0.15f);
            float h   = blueprint.maxHeight * envelope * aspectBias * (0.85f + rng.nextFloat() * 0.15f);
            float exp = style.sectionExponentMin + rng.nextFloat() * expRange;
            sections.add(new CrossSection(Math.max(0.1f, w), Math.max(0.1f, h), exp));
        }
        return sections;
    }
```

- [ ] **Step 6: Replace the panel inset constant usage**

`PANEL_INSET` is a `static final` literal used in `buildHullVerts`. To drive it from the style without restructuring, pass the style into `buildHullVerts`. In `generate(blueprint, style)`, change the `buildHullVerts(...)` call to include `style`:

Find:
```java
        float[] hullVerts = buildHullVerts(allRings, ringCenters, ringTangents,
                                           ringCount, vertsPerRing, palette, bbox);
```
Replace with:
```java
        float[] hullVerts = buildHullVerts(allRings, ringCenters, ringTangents,
                                           ringCount, vertsPerRing, palette, bbox, style);
```

Update the `buildHullVerts` signature (line 131) to accept `HullStyle style` and use `style.panelInsetScale` instead of the `PANEL_INSET` constant:

```java
    private float[] buildHullVerts(List<float[]> allRings, List<Vector3> ringCenters,
                                   List<Vector3> ringTangents, int ringCount, int vertsPerRing,
                                   ShipColorPalette palette, BoundingBox bbox, HullStyle style) {
```

Inside that method, change the inset line:
```java
                float insetScale = (panelInset && (v % 3 != 0)) ? (1f - PANEL_INSET) : 1f;
```
to:
```java
                float insetScale = (panelInset && (v % 3 != 0)) ? (1f - style.panelInsetScale) : 1f;
```

Leave the `PANEL_INSET` constant declaration in place (now unused is acceptable, or delete it — deleting is cleaner):
Delete the line `private static final float PANEL_INSET = 0.015f;`.

- [ ] **Step 7: Run all generator tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipHullGeneratorStyleTest" --tests "com.galacticodyssey.ship.ShipHullGeneratorTest"`
Expected: PASS. The legacy `ShipHullGeneratorTest` still passes because `generate(blueprint)` now delegates to `defaultStyle()`, whose ranges/palette match the old literals.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ShipHullGenerator.java core/src/test/java/com/galacticodyssey/ship/ShipHullGeneratorStyleTest.java
git commit -m "feat(ship): drive hull silhouette shape from HullStyle (lofted)"
```

---

## Task 5: HullStyleRegistry + data files

Loads styles, the ethos→style fallback map, and the explicit factionId→style map. Resolves a `FactionData` to a style: explicit map first, then ethos fallback, then `default`. Mirrors `ShipClassRegistry`'s `(String path)` + `(JsonValue root)` overload pattern so tests need no GL/files.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/HullStyleRegistry.java`
- Create: `core/src/main/resources/data/ships/hull_styles.json`
- Create: `core/src/main/resources/data/ships/ethos_style_map.json`
- Create: `core/src/main/resources/data/ships/faction_styles.json`
- Test: `core/src/test/java/com/galacticodyssey/ship/HullStyleRegistryTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/ship/HullStyleRegistryTest.java`:

```java
package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.galaxy.faction.FactionData;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.Test;

class HullStyleRegistryTest {

    private static final String STYLES_JSON =
        "[" +
        "  {\"id\":\"vaun\",\"generatorType\":\"LOFTED\",\"sectionExponentMin\":3.5,\"sectionExponentMax\":4.5," +
        "   \"aspectBiasMin\":0.8,\"aspectBiasMax\":1.0,\"spineCurvature\":0.3,\"panelInsetScale\":0.03," +
        "   \"ageless\":false,\"baseColors\":[[0.2,0.2,0.22]],\"accentColors\":[[0.8,0.1,0.1]],\"glowColors\":[[1.0,0.4,0.1]]}," +
        "  {\"id\":\"federation\",\"generatorType\":\"LOFTED\",\"sectionExponentMin\":2.0,\"sectionExponentMax\":2.8," +
        "   \"aspectBiasMin\":0.9,\"aspectBiasMax\":1.1,\"spineCurvature\":1.4,\"panelInsetScale\":0.01," +
        "   \"ageless\":false,\"baseColors\":[[0.85,0.87,0.9]],\"accentColors\":[[0.2,0.5,0.95]],\"glowColors\":[[0.3,0.6,1.0]]}" +
        "]";

    private static final String ETHOS_JSON =
        "{\"FEDERATION\":\"federation\",\"MILITARIST\":\"vaun\"}";

    private static final String FACTION_JSON =
        "{\"vaun_empire\":\"vaun\"}";

    private static HullStyleRegistry loaded() {
        HullStyleRegistry reg = new HullStyleRegistry();
        JsonReader r = new JsonReader();
        reg.loadStyles(r.parse(STYLES_JSON));
        reg.loadEthosMap(r.parse(ETHOS_JSON));
        reg.loadFactionMap(r.parse(FACTION_JSON));
        return reg;
    }

    private static FactionData faction(String id, FactionEthos ethos) {
        return new FactionData(id, "Name", 0, 0, 0, 0.5f, 0.5f, ethos,
                0.5f, 0.5f, 0.5f, 300f, "HUMAN_COLONY");
    }

    @Test
    void getReturnsLoadedStyleById() {
        HullStyle s = loaded().get("vaun");
        assertNotNull(s);
        assertEquals("vaun", s.id);
        assertEquals(GeneratorType.LOFTED, s.generatorType);
        assertEquals(3.5f, s.sectionExponentMin, 1e-4);
    }

    @Test
    void resolveUsesExplicitFactionMapFirst() {
        // vaun_empire is mapped explicitly to vaun even though its ethos is FEDERATION
        HullStyle s = loaded().resolve(faction("vaun_empire", FactionEthos.FEDERATION));
        assertEquals("vaun", s.id);
    }

    @Test
    void resolveFallsBackToEthosMap() {
        HullStyle s = loaded().resolve(faction("faction-3", FactionEthos.FEDERATION));
        assertEquals("federation", s.id);
    }

    @Test
    void resolveNullFactionReturnsDefault() {
        HullStyle s = loaded().resolve(null);
        assertEquals("default", s.id);
    }

    @Test
    void resolveUnknownEthosReturnsDefault() {
        // CORPORATE is not in the test ethos map => default
        HullStyle s = loaded().resolve(faction("faction-9", FactionEthos.CORPORATE));
        assertEquals("default", s.id);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.HullStyleRegistryTest"`
Expected: FAIL — `HullStyleRegistry` does not exist.

- [ ] **Step 3: Implement the registry**

Create `core/src/main/java/com/galacticodyssey/ship/HullStyleRegistry.java`:

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.galaxy.faction.FactionData;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@link HullStyle} archetypes and the faction → style bindings.
 *
 * <p>Resolution order for a faction: explicit {@code factionId → styleId} map,
 * then {@code ethos → styleId} fallback, then {@link HullStyle#defaultStyle()}.
 * Mirrors {@code ShipClassRegistry}: a {@code (String path)} production loader and
 * a {@code (JsonValue root)} test-friendly overload per data file.
 */
public class HullStyleRegistry {

    private final Map<String, HullStyle> styles = new HashMap<>();
    private final Map<String, String> ethosToStyle = new HashMap<>();
    private final Map<String, String> factionToStyle = new HashMap<>();

    // ---- styles ----

    public void loadStyles(String path) {
        loadStyles(new JsonReader().parse(Gdx.files.internal(path)));
    }

    public void loadStyles(JsonValue root) {
        for (JsonValue e = root.child; e != null; e = e.next) {
            HullStyle s = new HullStyle(
                e.getString("id"),
                GeneratorType.valueOf(e.getString("generatorType", "LOFTED")),
                e.getFloat("sectionExponentMin", 2.2f),
                e.getFloat("sectionExponentMax", 4.0f),
                e.getFloat("aspectBiasMin", 0.7f),
                e.getFloat("aspectBiasMax", 1.3f),
                e.getFloat("spineCurvature", 1.0f),
                e.getFloat("panelInsetScale", 0.015f),
                readColors(e.get("baseColors")),
                readColors(e.get("accentColors")),
                readColors(e.get("glowColors")),
                e.getBoolean("ageless", false));
            styles.put(s.id, s);
        }
    }

    private static float[][] readColors(JsonValue arr) {
        if (arr == null || arr.size == 0) {
            return new float[][]{{0.5f, 0.5f, 0.5f}};
        }
        float[][] out = new float[arr.size][];
        int i = 0;
        for (JsonValue c = arr.child; c != null; c = c.next, i++) {
            out[i] = new float[]{c.getFloat(0), c.getFloat(1), c.getFloat(2)};
        }
        return out;
    }

    // ---- ethos map ----

    public void loadEthosMap(String path) {
        loadEthosMap(new JsonReader().parse(Gdx.files.internal(path)));
    }

    public void loadEthosMap(JsonValue root) {
        for (JsonValue e = root.child; e != null; e = e.next) {
            ethosToStyle.put(e.name, e.asString());
        }
    }

    // ---- explicit faction map ----

    public void loadFactionMap(String path) {
        loadFactionMap(new JsonReader().parse(Gdx.files.internal(path)));
    }

    public void loadFactionMap(JsonValue root) {
        for (JsonValue e = root.child; e != null; e = e.next) {
            factionToStyle.put(e.name, e.asString());
        }
    }

    // ---- accessors ----

    /** Returns the style with {@code id}, or {@link HullStyle#defaultStyle()} if missing. */
    public HullStyle get(String id) {
        HullStyle s = styles.get(id);
        return s != null ? s : HullStyle.defaultStyle();
    }

    /**
     * Resolves the style for a faction: explicit id map, then ethos fallback,
     * then default. A {@code null} faction resolves to default.
     */
    public HullStyle resolve(FactionData faction) {
        if (faction == null) return HullStyle.defaultStyle();

        if (faction.styleId != null) {
            HullStyle s = styles.get(faction.styleId);
            if (s != null) return s;
        }
        String byId = factionToStyle.get(faction.id);
        if (byId != null && styles.containsKey(byId)) return styles.get(byId);

        String byEthos = ethosToStyle.get(faction.ethos.name());
        if (byEthos != null && styles.containsKey(byEthos)) return styles.get(byEthos);

        return HullStyle.defaultStyle();
    }
}
```

> NOTE: `resolve(...)` reads `faction.styleId`. That field is added in Task 6.
> Implement Task 6 before running this task's tests, OR temporarily omit the
> `faction.styleId` block. Recommended: do Task 6 first, then return here. The
> task order below in "Execution" runs Task 6 before Task 5's final test.

- [ ] **Step 4: Create the data files**

Create `core/src/main/resources/data/ships/hull_styles.json` (six lofted styles):

```json
[
  {
    "id": "default", "generatorType": "LOFTED",
    "sectionExponentMin": 2.2, "sectionExponentMax": 4.0,
    "aspectBiasMin": 0.7, "aspectBiasMax": 1.3,
    "spineCurvature": 1.0, "panelInsetScale": 0.015, "ageless": false,
    "baseColors": [[0.6,0.6,0.62],[0.35,0.35,0.38],[0.45,0.42,0.4]],
    "accentColors": [[0.9,0.7,0.1],[0.2,0.5,0.9]],
    "glowColors": [[0.3,0.5,1.0],[1.0,0.6,0.2]]
  },
  {
    "id": "federation", "generatorType": "LOFTED",
    "sectionExponentMin": 2.0, "sectionExponentMax": 2.8,
    "aspectBiasMin": 0.9, "aspectBiasMax": 1.1,
    "spineCurvature": 1.4, "panelInsetScale": 0.01, "ageless": false,
    "baseColors": [[0.85,0.87,0.9],[0.78,0.8,0.85],[0.9,0.92,0.95]],
    "accentColors": [[0.2,0.5,0.95],[0.3,0.65,1.0]],
    "glowColors": [[0.3,0.6,1.0]]
  },
  {
    "id": "vaun", "generatorType": "LOFTED",
    "sectionExponentMin": 3.5, "sectionExponentMax": 4.5,
    "aspectBiasMin": 0.8, "aspectBiasMax": 1.0,
    "spineCurvature": 0.3, "panelInsetScale": 0.035, "ageless": false,
    "baseColors": [[0.18,0.18,0.2],[0.12,0.12,0.13],[0.22,0.2,0.2]],
    "accentColors": [[0.8,0.12,0.1],[0.9,0.45,0.1]],
    "glowColors": [[1.0,0.4,0.1],[1.0,0.3,0.05]]
  },
  {
    "id": "zulkiri", "generatorType": "LOFTED",
    "sectionExponentMin": 1.4, "sectionExponentMax": 1.9,
    "aspectBiasMin": 0.6, "aspectBiasMax": 1.4,
    "spineCurvature": 1.6, "panelInsetScale": 0.02, "ageless": false,
    "baseColors": [[0.45,0.35,0.2],[0.3,0.38,0.18],[0.4,0.3,0.22]],
    "accentColors": [[0.5,0.7,0.15],[0.7,0.6,0.1]],
    "glowColors": [[0.6,0.8,0.2]]
  },
  {
    "id": "pirate_patchwork", "generatorType": "LOFTED",
    "sectionExponentMin": 2.0, "sectionExponentMax": 4.2,
    "aspectBiasMin": 0.6, "aspectBiasMax": 1.4,
    "spineCurvature": 1.2, "panelInsetScale": 0.025, "ageless": false,
    "baseColors": [[0.3,0.3,0.32],[0.4,0.25,0.2],[0.25,0.28,0.3]],
    "accentColors": [[0.9,0.3,0.2],[0.8,0.4,0.0]],
    "glowColors": [[1.0,0.6,0.2],[0.9,0.9,1.0]]
  },
  {
    "id": "independent_utilitarian", "generatorType": "LOFTED",
    "sectionExponentMin": 2.6, "sectionExponentMax": 3.4,
    "aspectBiasMin": 0.8, "aspectBiasMax": 1.2,
    "spineCurvature": 0.9, "panelInsetScale": 0.015, "ageless": false,
    "baseColors": [[0.5,0.5,0.52],[0.55,0.53,0.5]],
    "accentColors": [[0.9,0.7,0.1],[0.6,0.6,0.62]],
    "glowColors": [[0.9,0.9,1.0]]
  }
]
```

Create `core/src/main/resources/data/ships/ethos_style_map.json`:

```json
{
  "FEDERATION": "federation",
  "MILITARIST": "vaun",
  "CORPORATE": "zulkiri",
  "ISOLATIONIST": "independent_utilitarian",
  "PIRATE_SYNDICATE": "pirate_patchwork"
}
```

> `ISOLATIONIST` maps to `independent_utilitarian` for now; plan 1c re-points it to
> `null_sentinel` once the faceted generator exists.

Create `core/src/main/resources/data/ships/faction_styles.json` (explicit bindings for the eventual authored lore factions — harmless until those factions exist as data):

```json
{
  "galactic_federation": "federation",
  "vaun_empire": "vaun",
  "zul_kiri_conglomerate": "zulkiri"
}
```

- [ ] **Step 5: Run the registry test (after Task 6 is done)**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.HullStyleRegistryTest"`
Expected: PASS.

- [ ] **Step 6: Add a data-file load smoke test**

Append to `HullStyleRegistryTest` a test that the shipped JSON parses and resolves
(uses the literal JSON, not `Gdx.files`, to stay GL-free — copy the shipped arrays
is unnecessary; instead validate structure of the inline constants already tested).
Add:

```java
    @Test
    void allEthosFallbacksResolveToALoadedStyle() {
        // Mirrors the shipped ethos map against the shipped style ids.
        HullStyleRegistry reg = new HullStyleRegistry();
        JsonReader r = new JsonReader();
        reg.loadStyles(r.parse(
            "[{\"id\":\"federation\"},{\"id\":\"vaun\"},{\"id\":\"zulkiri\"}," +
            "{\"id\":\"independent_utilitarian\"},{\"id\":\"pirate_patchwork\"}]"));
        reg.loadEthosMap(r.parse(
            "{\"FEDERATION\":\"federation\",\"MILITARIST\":\"vaun\",\"CORPORATE\":\"zulkiri\"," +
            "\"ISOLATIONIST\":\"independent_utilitarian\",\"PIRATE_SYNDICATE\":\"pirate_patchwork\"}"));
        for (FactionEthos ethos : FactionEthos.values()) {
            HullStyle s = reg.resolve(faction("faction-x", ethos));
            assertEquals(ethos != null, s != null);
            org.junit.jupiter.api.Assertions.assertNotEquals("default", s.id,
                "ethos " + ethos + " should map to a real style");
        }
    }
```

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.HullStyleRegistryTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/HullStyleRegistry.java core/src/main/resources/data/ships/hull_styles.json core/src/main/resources/data/ships/ethos_style_map.json core/src/main/resources/data/ships/faction_styles.json core/src/test/java/com/galacticodyssey/ship/HullStyleRegistryTest.java
git commit -m "feat(ship): add HullStyleRegistry + lofted style data files"
```

---

## Task 6: FactionData.styleId

Add an optional `styleId` so authored factions can override the ethos fallback. Keep the existing constructor working (delegates with `styleId = null`).

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/faction/FactionData.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/faction/FactionDataStyleTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/galaxy/faction/FactionDataStyleTest.java`:

```java
package com.galacticodyssey.galaxy.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FactionDataStyleTest {

    @Test
    void legacyConstructorLeavesStyleIdNull() {
        FactionData f = new FactionData("f1", "N", 0, 0, 0, 0.5f, 0.5f,
                FactionEthos.FEDERATION, 0.5f, 0.5f, 0.5f, 300f, "HUMAN_COLONY");
        assertNull(f.styleId);
    }

    @Test
    void styleConstructorStoresStyleId() {
        FactionData f = new FactionData("f1", "N", 0, 0, 0, 0.5f, 0.5f,
                FactionEthos.MILITARIST, 0.5f, 0.5f, 0.5f, 300f, "HUMAN_COLONY", "vaun");
        assertEquals("vaun", f.styleId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.faction.FactionDataStyleTest"`
Expected: FAIL — `styleId` field / 14-arg constructor missing.

- [ ] **Step 3: Implement**

In `FactionData.java`, add the field after `namingStyleId` (line 29):

```java
    /** Optional explicit hull style id; null means resolve from ethos. */
    public final String styleId;
```

Change the existing constructor to delegate, and add the new constructor. Replace the current constructor (lines 31–50) with:

```java
    public FactionData(String id, String name,
                       double capitalX, double capitalY, double capitalZ,
                       float militaryStrength, float economicStrength,
                       FactionEthos ethos,
                       float mapColorR, float mapColorG, float mapColorB,
                       float influenceRadiusLY, String namingStyleId) {
        this(id, name, capitalX, capitalY, capitalZ, militaryStrength, economicStrength,
             ethos, mapColorR, mapColorG, mapColorB, influenceRadiusLY, namingStyleId, null);
    }

    public FactionData(String id, String name,
                       double capitalX, double capitalY, double capitalZ,
                       float militaryStrength, float economicStrength,
                       FactionEthos ethos,
                       float mapColorR, float mapColorG, float mapColorB,
                       float influenceRadiusLY, String namingStyleId, String styleId) {
        this.id = id;
        this.name = name;
        this.capitalX = capitalX;
        this.capitalY = capitalY;
        this.capitalZ = capitalZ;
        this.militaryStrength = militaryStrength;
        this.economicStrength = economicStrength;
        this.ethos = ethos;
        this.mapColorR = mapColorR;
        this.mapColorG = mapColorG;
        this.mapColorB = mapColorB;
        this.influenceRadiusLY = influenceRadiusLY;
        this.namingStyleId = namingStyleId;
        this.styleId = styleId;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.faction.FactionDataStyleTest" --tests "com.galacticodyssey.galaxy.faction.FactionGeneratorTest"`
Expected: PASS (existing `FactionGeneratorTest` uses the 13-arg constructor, which still compiles via delegation).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/faction/FactionData.java core/src/test/java/com/galacticodyssey/galaxy/faction/FactionDataStyleTest.java
git commit -m "feat(faction): add optional styleId to FactionData"
```

---

## Task 7: ShipGenerationConfig

Carries faction/role/condition through to `ShipFactory`.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ShipGenerationConfig.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipGenerationConfigTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/ship/ShipGenerationConfigTest.java`:

```java
package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ShipGenerationConfigTest {

    @Test
    void defaultsAreIndependentAndPristine() {
        ShipGenerationConfig c = ShipGenerationConfig.defaults(123L, ShipSizeClass.SMALL);
        assertEquals(123L, c.seed);
        assertEquals(ShipSizeClass.SMALL, c.sizeClass);
        assertNull(c.faction);
        assertEquals(ShipRole.CIVILIAN, c.role);
        assertEquals(1.0f, c.conditionFactor, 1e-4);
        org.junit.jupiter.api.Assertions.assertFalse(c.isFlagship);
    }

    @Test
    void fieldsAreSettable() {
        ShipGenerationConfig c = new ShipGenerationConfig();
        c.seed = 9L;
        c.sizeClass = ShipSizeClass.LARGE;
        c.role = ShipRole.WARSHIP;
        c.conditionFactor = 0.4f;
        c.isFlagship = true;
        assertEquals(ShipSizeClass.LARGE, c.sizeClass);
        assertEquals(ShipRole.WARSHIP, c.role);
        assertEquals(0.4f, c.conditionFactor, 1e-4);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipGenerationConfigTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

Create `core/src/main/java/com/galacticodyssey/ship/ShipGenerationConfig.java`:

```java
package com.galacticodyssey.ship;

import com.galacticodyssey.galaxy.faction.FactionData;

/** Inputs to a ship generation pass. Mutable POJO for easy construction. */
public class ShipGenerationConfig {

    public long seed;
    public ShipSizeClass sizeClass = ShipSizeClass.SMALL;
    public FactionData faction;            // null => independent
    public ShipRole role = ShipRole.CIVILIAN;
    public float conditionFactor = 1.0f;   // 1 = pristine, 0 = derelict
    public boolean isFlagship = false;

    /** Independent, pristine, civilian config for {@code seed}/{@code sizeClass}. */
    public static ShipGenerationConfig defaults(long seed, ShipSizeClass sizeClass) {
        ShipGenerationConfig c = new ShipGenerationConfig();
        c.seed = seed;
        c.sizeClass = sizeClass;
        return c;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipGenerationConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ShipGenerationConfig.java core/src/test/java/com/galacticodyssey/ship/ShipGenerationConfigTest.java
git commit -m "feat(ship): add ShipGenerationConfig"
```

---

## Task 8: ShipFactory style dispatch

Add `setHullStyleRegistry(...)` and `createShip(ShipGenerationConfig, x, y, z)` that resolves a style and passes it to the generator. The existing 4-arg `createShip` delegates with `ShipGenerationConfig.defaults(...)`. When no registry is set, fall back to `HullStyle.defaultStyle()` so existing callers/tests are unaffected.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipFactoryStyleTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/ship/ShipFactoryStyleTest.java`. This is a unit test of the **style-resolution seam only** (no GL/engine), exercising the helper that picks the style for a config:

```java
package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.utils.JsonReader;
import com.galacticodyssey.galaxy.faction.FactionData;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.Test;

class ShipFactoryStyleTest {

    private static HullStyleRegistry registry() {
        HullStyleRegistry reg = new HullStyleRegistry();
        JsonReader r = new JsonReader();
        reg.loadStyles(r.parse(
            "[{\"id\":\"vaun\",\"sectionExponentMin\":3.5},{\"id\":\"federation\",\"sectionExponentMin\":2.0}]"));
        reg.loadEthosMap(r.parse("{\"MILITARIST\":\"vaun\",\"FEDERATION\":\"federation\"}"));
        reg.loadFactionMap(r.parse("{}"));
        return reg;
    }

    @Test
    void resolvesStyleForFactionConfig() {
        ShipGenerationConfig c = ShipGenerationConfig.defaults(1L, ShipSizeClass.SMALL);
        c.faction = new FactionData("faction-1", "N", 0, 0, 0, 0.5f, 0.5f,
                FactionEthos.MILITARIST, 0.5f, 0.5f, 0.5f, 300f, "HUMAN_COLONY");
        HullStyle s = ShipFactory.resolveStyle(registry(), c);
        assertEquals("vaun", s.id);
    }

    @Test
    void resolvesDefaultWhenRegistryNull() {
        ShipGenerationConfig c = ShipGenerationConfig.defaults(1L, ShipSizeClass.SMALL);
        HullStyle s = ShipFactory.resolveStyle(null, c);
        assertEquals("default", s.id);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipFactoryStyleTest"`
Expected: FAIL — `ShipFactory.resolveStyle` does not exist.

- [ ] **Step 3: Implement the seam + config overload**

In `ShipFactory.java`:

Add a field near the other registries (after line 84 `private ShipModuleRegistry moduleRegistry;`):

```java
    private HullStyleRegistry hullStyleRegistry;
```

Add a setter near `setModuleRegistry` (after line 102):

```java
    public void setHullStyleRegistry(HullStyleRegistry registry) {
        this.hullStyleRegistry = registry;
    }
```

Add the static resolution helper (place it in the Helpers section near the bottom, before the closing brace):

```java
    /**
     * Resolves the hull style for a config: uses the registry if present,
     * else {@link HullStyle#defaultStyle()}. Static + package-visible for testing.
     */
    static HullStyle resolveStyle(HullStyleRegistry registry, ShipGenerationConfig config) {
        if (registry == null) return HullStyle.defaultStyle();
        return registry.resolve(config.faction);
    }
```

Refactor the public API: change the existing 4-arg `createShip` (line 118) to delegate, and add the config overload. Replace the method signature line:

```java
    public Entity createShip(long seed, ShipSizeClass sizeClass, float x, float y, float z) {
```
with:
```java
    public Entity createShip(long seed, ShipSizeClass sizeClass, float x, float y, float z) {
        return createShip(ShipGenerationConfig.defaults(seed, sizeClass), x, y, z);
    }

    public Entity createShip(ShipGenerationConfig config, float x, float y, float z) {
        long seed = config.seed;
        ShipSizeClass sizeClass = config.sizeClass;
        HullStyle style = resolveStyle(hullStyleRegistry, config);
```

Then, in the (now config) method body, change the blueprint+hull generation lines:

Find:
```java
        // ----- 1. Blueprint -----
        ShipBlueprint blueprint = new ShipBlueprint(seed, sizeClass);

        // ----- 2. Hull + interior geometry -----
        HullGeometry    hull    = hullGenerator.generate(blueprint);
```
Replace with:
```java
        // ----- 1. Blueprint -----
        ShipBlueprint blueprint = new ShipBlueprint(seed, sizeClass);

        // ----- 2. Hull + interior geometry -----
        // NOTE: FACETED styles fall back to the lofted generator until plan 1c.
        HullGeometry    hull    = hullGenerator.generate(blueprint, style);
```

The rest of the method body is unchanged (it already uses `seed`, `sizeClass`, `blueprint`, `hull`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipFactoryStyleTest" --tests "com.galacticodyssey.ship.ShipFactoryTest"`
Expected: PASS (existing `ShipFactoryTest` uses the 4-arg overload, now delegating).

- [ ] **Step 5: Wire the registry where the factory is constructed (production)**

Find where `ShipFactory` is instantiated and other registries are set:

Run: `./gradlew --offline 2>/dev/null; grep -rn "new ShipFactory(" core/src/main desktop/src 2>/dev/null` (or use the Grep tool for `setModuleRegistry(`).

At each production construction site that already calls `setModuleRegistry(...)`/`setReactorSpecRegistry(...)`, add:

```java
HullStyleRegistry hullStyleRegistry = new HullStyleRegistry();
hullStyleRegistry.loadStyles("data/ships/hull_styles.json");
hullStyleRegistry.loadEthosMap("data/ships/ethos_style_map.json");
hullStyleRegistry.loadFactionMap("data/ships/faction_styles.json");
shipFactory.setHullStyleRegistry(hullStyleRegistry);
```

(If no production site sets the other registries, skip this step — the factory
already defaults to `HullStyle.defaultStyle()` when no registry is set, and styled
generation can be wired in a later integration pass. Note this in the commit.)

- [ ] **Step 6: Run the full ship + faction test suites**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.*" --tests "com.galacticodyssey.galaxy.faction.*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ShipFactory.java core/src/test/java/com/galacticodyssey/ship/ShipFactoryStyleTest.java
git commit -m "feat(ship): resolve and apply HullStyle in ShipFactory generation"
```

---

## Execution order note

Task 5's `resolve(...)` references `faction.styleId` from Task 6. Execute in this
order: 1 → 2 → 3 → 4 → **6 → 5** → 7 → 8. (Task 6 before Task 5.)

---

## Self-Review

**Spec coverage (1a subset of §2, §3.1–3.3, §3.5–3.6):**
- Two generation paths declared (`GeneratorType`) — Task 1. (FACETED build is plan 1c.) ✓
- `HullStyle` data-driven shape levers + palette — Tasks 2, 5. ✓
- Lofted style biasing wired into generator (exponent, aspect, spine curvature, panel inset, palette) — Tasks 3, 4. ✓
- Faction → style binding (explicit map + ethos fallback + default) — Tasks 5, 6. ✓
- `ShipGenerationConfig` + backward-compatible `ShipFactory` overload — Tasks 7, 8. ✓
- Determinism preserved (per-blueprint `Random(seed+1)`, palette `Random(seed)`) — asserted in Tasks 3, 4. ✓
- Deferred to later plans (documented): signature features (1b); FacetedHullGenerator + ISOLATIONIST→null_sentinel + zeelee (1c); sub-seed `mix()` refactor and `HullGeometry` sockets[] (Phase 2 plan); wear/ageless application (Phase 3 plan). The `ageless` field exists now but is consumed by the wear pass in Phase 3.

**Placeholder scan:** No TBD/TODO-as-work; the two "NOTE" callouts are sequencing guidance with concrete instructions, not deferred work.

**Type consistency:** `HullStyle` constructor arg order is identical across Task 2 (definition), Task 5 (registry construction), and the tests. `generate(ShipBlueprint, HullStyle)` signature consistent in Tasks 4 and 8. `FactionData` 14-arg constructor consistent in Task 6 and used in Task 5/8 tests via the 13-arg form. `resolveStyle(HullStyleRegistry, ShipGenerationConfig)` consistent in Task 8 definition and test.

---

## Execution Handoff

Plan complete. After this plan lands, plans **1b** (signature features) and **1c**
(faceted generator) follow, then Phase 2 (component visibility) and Phase 3 (wear)
each get their own plan.
