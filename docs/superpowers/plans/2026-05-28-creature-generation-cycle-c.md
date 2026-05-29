# Creature Generation Cycle C — Procedural Skin Patterns

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each creature a unique, biome-appropriate skin pattern using hybrid vertex-color baking (body-context metadata) plus a custom fragment shader (runtime pattern compositing with per-creature uniforms).

**Architecture:** `PaletteGenerator` deterministically selects a palette + pattern type from `colorSeed` + biome. `PatternStamper` bakes four metadata channels into vertex colors during mesh generation (dorsal/ventral gradient, limb-axis gradient, Voronoi cell ID, curvature). A custom `creature_skin.frag` shader reads these channels alongside per-creature uniforms (`u_palette`, `u_patternType`, etc.) to composite the final albedo and PBR properties, outputting to the existing G-buffer format. `CreatureMeshBuilder.buildSkinned()` is updated to emit vertex colors and set material uniforms from a `CreatureSkinSpec` stored on `CreatureSpec`.

**Tech Stack:** Java 17, libGDX 1.13 (MeshPartBuilder with vertex colors, ShaderProgram, Material attributes), GLSL 330, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-28-creature-generation-bcd-design.md` (Cycle C section)

---

### Task 1: PatternType enum and CreatureSkinSpec data class

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/skin/PatternType.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/skin/CreatureSkinSpec.java`

- [ ] **Step 1: Write PatternType enum**

```java
package com.galacticodyssey.fauna.skin;

public enum PatternType {
    SOLID(0), STRIPES(1), SPOTS(2), ROSETTES(3), MOTTLED(4), BIOLUMINESCENT(5);

    public final int shaderId;

    PatternType(int shaderId) { this.shaderId = shaderId; }
}
```

- [ ] **Step 2: Write CreatureSkinSpec data class**

This is a GL-free, serializable data class stored on `CreatureSpec`. It carries everything the shader needs.

```java
package com.galacticodyssey.fauna.skin;

public final class CreatureSkinSpec {
    public PatternType patternType = PatternType.SOLID;

    // Palette: primary (dorsal), secondary (pattern), accent (highlights/glow)
    public float primaryR, primaryG, primaryB;
    public float secondaryR, secondaryG, secondaryB;
    public float accentR, accentG, accentB;

    // Ventral auto-derived from primary (lighter, desaturated)
    public float ventralR, ventralG, ventralB;

    public float patternScale = 1f;       // noise frequency multiplier
    public float patternContrast = 0.5f;  // edge hardness
    public float bioGlow = 0f;            // bioluminescence intensity (0 = none)

    public float roughness = 0.7f;        // PBR roughness
    public float metallic = 0f;           // PBR metallic
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/skin/PatternType.java \
        core/src/main/java/com/galacticodyssey/fauna/skin/CreatureSkinSpec.java
git commit -m "feat(fauna): PatternType enum and CreatureSkinSpec data class for Cycle C"
```

---

### Task 2: PaletteGenerator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/skin/PaletteGenerator.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/skin/PaletteGeneratorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.skin;

import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaletteGeneratorTest {

    @Test
    void deterministic_sameSeedAndBiomeProduceIdenticalSpec() {
        CreatureSkinSpec a = PaletteGenerator.generate(12345L, BiomeType.GRASSLAND, BodyPlan.QUADRUPED);
        CreatureSkinSpec b = PaletteGenerator.generate(12345L, BiomeType.GRASSLAND, BodyPlan.QUADRUPED);
        assertEquals(a.patternType, b.patternType);
        assertEquals(a.primaryR, b.primaryR, 1e-6f);
        assertEquals(a.primaryG, b.primaryG, 1e-6f);
        assertEquals(a.primaryB, b.primaryB, 1e-6f);
        assertEquals(a.secondaryR, b.secondaryR, 1e-6f);
        assertEquals(a.patternScale, b.patternScale, 1e-6f);
    }

    @Test
    void differentSeedsProduceDifferentPalettes() {
        CreatureSkinSpec a = PaletteGenerator.generate(100L, BiomeType.GRASSLAND, BodyPlan.QUADRUPED);
        CreatureSkinSpec b = PaletteGenerator.generate(999L, BiomeType.GRASSLAND, BodyPlan.QUADRUPED);
        boolean differs = a.primaryR != b.primaryR || a.primaryG != b.primaryG
                       || a.primaryB != b.primaryB || a.patternType != b.patternType;
        assertTrue(differs, "different seeds should generally produce different palettes");
    }

    @Test
    void colorsAreInValidRange() {
        CreatureSkinSpec s = PaletteGenerator.generate(42L, BiomeType.DESERT, BodyPlan.BIPEDAL);
        assertColorRange(s.primaryR, s.primaryG, s.primaryB);
        assertColorRange(s.secondaryR, s.secondaryG, s.secondaryB);
        assertColorRange(s.accentR, s.accentG, s.accentB);
        assertColorRange(s.ventralR, s.ventralG, s.ventralB);
    }

    @Test
    void ventralIsLighterThanPrimary() {
        CreatureSkinSpec s = PaletteGenerator.generate(42L, BiomeType.TEMPERATE_FOREST, BodyPlan.QUADRUPED);
        float primaryLum = 0.299f * s.primaryR + 0.587f * s.primaryG + 0.114f * s.primaryB;
        float ventralLum = 0.299f * s.ventralR + 0.587f * s.ventralG + 0.114f * s.ventralB;
        assertTrue(ventralLum >= primaryLum - 0.01f,
            "ventral should be at least as bright as primary");
    }

    @Test
    void hexapodHasLowRoughness() {
        CreatureSkinSpec s = PaletteGenerator.generate(42L, BiomeType.GRASSLAND, BodyPlan.HEXAPOD);
        assertTrue(s.roughness < 0.5f, "chitin should be shiny (low roughness)");
        assertTrue(s.metallic > 0f, "chitin should have slight metallic");
    }

    @Test
    void caveBiomeFavorsBioluminescent() {
        int bioCount = 0;
        for (int i = 0; i < 100; i++) {
            CreatureSkinSpec s = PaletteGenerator.generate(i * 7919L, BiomeType.SWAMP, BodyPlan.HEXAPOD);
            if (s.patternType == PatternType.BIOLUMINESCENT) bioCount++;
        }
        // Swamp (closest to cave biome in current set) should have elevated bioluminescence rate
        // Not all will be bioluminescent, but some should be
        assertTrue(bioCount >= 5, "cave-like biomes should produce some bioluminescent creatures");
    }

    private void assertColorRange(float r, float g, float b) {
        assertTrue(r >= 0f && r <= 1f, "R out of range: " + r);
        assertTrue(g >= 0f && g <= 1f, "G out of range: " + g);
        assertTrue(b >= 0f && b <= 1f, "B out of range: " + b);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.skin.PaletteGeneratorTest" --info`
Expected: FAIL — `PaletteGenerator` does not exist.

- [ ] **Step 3: Implement PaletteGenerator**

```java
package com.galacticodyssey.fauna.skin;

import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.planet.BiomeType;

import java.util.Random;

public final class PaletteGenerator {

    private PaletteGenerator() {}

    public static CreatureSkinSpec generate(long colorSeed, BiomeType biome, BodyPlan bodyPlan) {
        Random rng = new Random(colorSeed);
        CreatureSkinSpec spec = new CreatureSkinSpec();

        // Hue range constrained per biome (degrees 0-360)
        float hueMin, hueMax, satMin, satMax, valMin, valMax;
        float[] patternWeights;
        switch (biome) {
            case DESERT: case SAVANNA: case ARID_SHRUB: case BADLANDS:
                hueMin = 20f; hueMax = 60f; satMin = 0.3f; satMax = 0.6f; valMin = 0.5f; valMax = 0.7f;
                patternWeights = new float[]{0.4f, 0.2f, 0.1f, 0f, 0.3f, 0f}; break;
            case TEMPERATE_FOREST: case BOREAL_FOREST: case TROPICAL_FOREST:
                hueMin = 80f; hueMax = 160f; satMin = 0.4f; satMax = 0.7f; valMin = 0.3f; valMax = 0.6f;
                patternWeights = new float[]{0f, 0.1f, 0.3f, 0.3f, 0.3f, 0f}; break;
            case TUNDRA: case ICE_SHEET: case POLAR_DESERT: case ICE_FIELD:
                hueMin = 0f; hueMax = 40f; satMin = 0.05f; satMax = 0.2f; valMin = 0.7f; valMax = 0.9f;
                patternWeights = new float[]{0.4f, 0.2f, 0.1f, 0f, 0.3f, 0f}; break;
            case OCEAN: case LAKE: case RIVER:
                hueMin = 180f; hueMax = 260f; satMin = 0.4f; satMax = 0.7f; valMin = 0.3f; valMax = 0.6f;
                patternWeights = new float[]{0f, 0.3f, 0.3f, 0f, 0.2f, 0.2f}; break;
            case VOLCANIC:
                hueMin = 0f; hueMax = 30f; satMin = 0.3f; satMax = 0.5f; valMin = 0.15f; valMax = 0.35f;
                patternWeights = new float[]{0.3f, 0.2f, 0.1f, 0f, 0.4f, 0f}; break;
            case SWAMP:
                hueMin = 60f; hueMax = 160f; satMin = 0.2f; satMax = 0.5f; valMin = 0.2f; valMax = 0.5f;
                patternWeights = new float[]{0.1f, 0.1f, 0.1f, 0f, 0.2f, 0.5f}; break;
            case GRASSLAND: case STEPPE: default:
                hueMin = 30f; hueMax = 120f; satMin = 0.3f; satMax = 0.6f; valMin = 0.4f; valMax = 0.7f;
                patternWeights = new float[]{0.1f, 0.4f, 0.2f, 0f, 0.3f, 0f}; break;
        }

        // Primary color
        float hue = hueMin + rng.nextFloat() * (hueMax - hueMin);
        float sat = satMin + rng.nextFloat() * (satMax - satMin);
        float val = valMin + rng.nextFloat() * (valMax - valMin);
        float[] primary = hsvToRgb(hue, sat, val);
        spec.primaryR = primary[0]; spec.primaryG = primary[1]; spec.primaryB = primary[2];

        // Secondary: analogous or complementary hue shift
        float hueShift = rng.nextBoolean() ? (30f + rng.nextFloat() * 30f) : (150f + rng.nextFloat() * 60f);
        float secHue = (hue + hueShift) % 360f;
        float[] secondary = hsvToRgb(secHue, sat * 0.9f, val * (0.8f + rng.nextFloat() * 0.4f));
        spec.secondaryR = secondary[0]; spec.secondaryG = secondary[1]; spec.secondaryB = secondary[2];

        // Accent: high saturation
        float accHue = (hue + rng.nextFloat() * 60f - 30f + 360f) % 360f;
        float[] accent = hsvToRgb(accHue, 0.7f + rng.nextFloat() * 0.3f, 0.6f + rng.nextFloat() * 0.4f);
        spec.accentR = accent[0]; spec.accentG = accent[1]; spec.accentB = accent[2];

        // Ventral: primary hue, desaturated, lighter
        float[] ventral = hsvToRgb(hue, sat * 0.5f, Math.min(1f, val * 1.3f));
        spec.ventralR = ventral[0]; spec.ventralG = ventral[1]; spec.ventralB = ventral[2];

        // Pattern type: weighted random from biome table
        spec.patternType = pickPattern(rng, patternWeights);

        // Pattern params
        spec.patternScale = 0.5f + rng.nextFloat() * 2f;
        spec.patternContrast = 0.3f + rng.nextFloat() * 0.5f;

        // Bioluminescence
        if (spec.patternType == PatternType.BIOLUMINESCENT) {
            spec.bioGlow = 0.3f + rng.nextFloat() * 0.7f;
        }

        // PBR per body plan
        switch (bodyPlan) {
            case QUADRUPED: spec.roughness = 0.75f; spec.metallic = 0f; break;
            case BIPEDAL:   spec.roughness = 0.70f; spec.metallic = 0f; break;
            case HEXAPOD:   spec.roughness = 0.35f; spec.metallic = 0.15f; break;
            case SERPENTINE: spec.roughness = 0.45f; spec.metallic = 0.05f; break;
        }

        return spec;
    }

    private static PatternType pickPattern(Random rng, float[] weights) {
        float roll = rng.nextFloat();
        float cumulative = 0f;
        PatternType[] types = PatternType.values();
        for (int i = 0; i < weights.length && i < types.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return types[i];
        }
        return PatternType.SOLID;
    }

    static float[] hsvToRgb(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if      (h < 60f)  { r = c; g = x; b = 0; }
        else if (h < 120f) { r = x; g = c; b = 0; }
        else if (h < 180f) { r = 0; g = c; b = x; }
        else if (h < 240f) { r = 0; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0; b = c; }
        else               { r = c; g = 0; b = x; }
        return new float[]{
            Math.max(0f, Math.min(1f, r + m)),
            Math.max(0f, Math.min(1f, g + m)),
            Math.max(0f, Math.min(1f, b + m))
        };
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.skin.PaletteGeneratorTest" --info`
Expected: 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/skin/PaletteGenerator.java \
        core/src/test/java/com/galacticodyssey/fauna/skin/PaletteGeneratorTest.java
git commit -m "feat(fauna): PaletteGenerator — biome-weighted palettes and pattern selection"
```

---

### Task 3: Add normals to ProceduralMeshData and ProceduralPartMesher

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralMeshData.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralPartMesher.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/geometry/ProceduralPartMesherTest.java` (modify existing)

The PatternStamper needs per-vertex normals (for dorsal/ventral gradient) and per-vertex Z position along the part axis (for limb-axis gradient). Currently `ProceduralMeshData` only stores positions and indices. We add a `normals` array parallel to `positions`.

- [ ] **Step 1: Add normals field to ProceduralMeshData**

The file is at `core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralMeshData.java`. Add a `normals` array:

```java
package com.galacticodyssey.fauna.geometry;

public final class ProceduralMeshData {
    public final float[] positions; // length = vertexCount * 3
    public final float[] normals;   // length = vertexCount * 3 (per-vertex normals)
    public final short[] indices;

    public ProceduralMeshData(float[] positions, float[] normals, short[] indices) {
        this.positions = positions;
        this.normals = normals;
        this.indices = indices;
    }

    public int positionCount() { return positions.length / 3; }

    public float maxZ() { float m = -Float.MAX_VALUE; for (int i = 2; i < positions.length; i += 3) m = Math.max(m, positions[i]); return m; }
    public float minZ() { float m =  Float.MAX_VALUE; for (int i = 2; i < positions.length; i += 3) m = Math.min(m, positions[i]); return m; }
    public float maxAbsXY() {
        float m = 0f;
        for (int i = 0; i < positions.length; i += 3) { m = Math.max(m, Math.abs(positions[i])); m = Math.max(m, Math.abs(positions[i+1])); }
        return m;
    }
}
```

- [ ] **Step 2: Update ProceduralPartMesher to compute normals**

For tube-like shapes, the vertex normal is the radial direction from the Z axis. Modify `build()` in `ProceduralPartMesher` to compute normals alongside positions:

```java
public ProceduralMeshData build(PartGeometrySpec spec) {
    List<Float> pos = new ArrayList<>();
    List<Float> nrm = new ArrayList<>();
    List<Short> idx = new ArrayList<>();

    for (int ring = 0; ring <= RINGS; ring++) {
        float t = ring / (float) RINGS;
        float z = t * spec.length;
        float r = spec.radius * (1f + (spec.taper - 1f) * t);
        if (spec.shape == PartGeometrySpec.Shape.ELLIPSOID_SNOUT)
            r = spec.radius * (float) Math.sin(Math.PI * Math.max(0.05f, t));
        else if (spec.shape == PartGeometrySpec.Shape.CONE)
            r = spec.radius * (1f - t);
        for (int s = 0; s < RADIAL; s++) {
            double a = 2.0 * Math.PI * s / RADIAL;
            float nx = (float) Math.cos(a);
            float ny = (float) Math.sin(a);
            pos.add(nx * r);
            pos.add(ny * r);
            pos.add(z);
            // Normal: radial direction (outward from axis)
            nrm.add(nx);
            nrm.add(ny);
            nrm.add(0f);
        }
    }
    for (int ring = 0; ring < RINGS; ring++) {
        for (int s = 0; s < RADIAL; s++) {
            int s2 = (s + 1) % RADIAL;
            short a = (short) (ring * RADIAL + s);
            short b = (short) (ring * RADIAL + s2);
            short c = (short) ((ring + 1) * RADIAL + s);
            short d = (short) ((ring + 1) * RADIAL + s2);
            idx.add(a); idx.add(c); idx.add(b);
            idx.add(b); idx.add(c); idx.add(d);
        }
    }

    float[] p = new float[pos.size()];
    for (int i = 0; i < p.length; i++) p[i] = pos.get(i);
    float[] n = new float[nrm.size()];
    for (int i = 0; i < n.length; i++) n[i] = nrm.get(i);
    short[] ix = new short[idx.size()];
    for (int i = 0; i < ix.length; i++) ix[i] = idx.get(i);
    return new ProceduralMeshData(p, n, ix);
}
```

- [ ] **Step 3: Add test for normals**

Append to the existing `ProceduralPartMesherTest.java`:

```java
@Test
void normalsAreUnitLengthAndRadial() {
    ProceduralPartMesher mesher = new ProceduralPartMesher();
    PartGeometrySpec spec = new PartGeometrySpec();
    spec.shape = PartGeometrySpec.Shape.CAPSULE;
    spec.length = 2f;
    spec.radius = 0.5f;
    ProceduralMeshData data = mesher.build(spec);

    assertNotNull(data.normals);
    assertEquals(data.positions.length, data.normals.length, "normals array must match positions");

    for (int i = 0; i < data.normals.length; i += 3) {
        float nx = data.normals[i], ny = data.normals[i+1], nz = data.normals[i+2];
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        assertEquals(1f, len, 0.01f, "normal at vertex " + (i/3) + " should be unit length");
        assertEquals(0f, nz, 1e-5f, "radial normals should have zero Z component");
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.geometry.ProceduralPartMesherTest" --info`
Expected: All tests PASS (existing + new)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralMeshData.java \
        core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralPartMesher.java \
        core/src/test/java/com/galacticodyssey/fauna/geometry/ProceduralPartMesherTest.java
git commit -m "feat(fauna): add per-vertex normals to ProceduralMeshData"
```

---

### Task 4: PatternStamper

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/skin/PatternStamper.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/skin/PatternStamperTest.java`

The stamper takes `ProceduralMeshData` (with normals) and writes a `float[] vertexColors` (4 floats per vertex: RGBA) encoding body-context metadata:

- **R**: Dorsal/ventral gradient — `dot(normal, up)` mapped to 0–1. Up = (0,1,0) in local space. Vertices pointing up (dorsal) get R≈0; vertices pointing down (ventral) get R≈1.
- **G**: Limb-axis gradient — normalized Z along the part axis: `z / length`.
- **B**: Seeded hash — simple spatial hash for pattern variation. Not full Voronoi (cheaper).
- **A**: Curvature — approximated as `1 - radius/maxRadius` (narrow parts have higher curvature).

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.skin;

import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import com.galacticodyssey.fauna.geometry.ProceduralMeshData;
import com.galacticodyssey.fauna.geometry.ProceduralPartMesher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternStamperTest {

    @Test
    void outputHasFourFloatsPerVertex() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f;
        spec.radius = 0.5f;
        ProceduralMeshData data = mesher.build(spec);

        float[] colors = PatternStamper.stamp(data, spec, 42L);
        assertEquals(data.positionCount() * 4, colors.length,
            "4 color floats per vertex");
    }

    @Test
    void dorsalVerticesHaveLowR_ventralHaveHighR() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f;
        spec.radius = 0.5f;
        ProceduralMeshData data = mesher.build(spec);

        float[] colors = PatternStamper.stamp(data, spec, 42L);

        boolean foundDorsal = false, foundVentral = false;
        for (int v = 0; v < data.positionCount(); v++) {
            float ny = data.normals[v * 3 + 1]; // Y component of normal
            float r = colors[v * 4];             // R channel = dorsal/ventral
            if (ny > 0.5f) { assertTrue(r < 0.5f, "dorsal vertex (ny=" + ny + ") should have low R"); foundDorsal = true; }
            if (ny < -0.5f) { assertTrue(r > 0.5f, "ventral vertex (ny=" + ny + ") should have high R"); foundVentral = true; }
        }
        assertTrue(foundDorsal, "should have dorsal vertices");
        assertTrue(foundVentral, "should have ventral vertices");
    }

    @Test
    void limbAxisGradientRunsZeroToOne() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f;
        spec.radius = 0.5f;
        ProceduralMeshData data = mesher.build(spec);

        float[] colors = PatternStamper.stamp(data, spec, 42L);

        float minG = Float.MAX_VALUE, maxG = -Float.MAX_VALUE;
        for (int v = 0; v < data.positionCount(); v++) {
            float g = colors[v * 4 + 1]; // G channel = limb axis
            minG = Math.min(minG, g);
            maxG = Math.max(maxG, g);
            assertTrue(g >= 0f && g <= 1f, "G must be in [0,1]");
        }
        assertTrue(minG < 0.1f, "proximal vertices should have G near 0");
        assertTrue(maxG > 0.9f, "distal vertices should have G near 1");
    }

    @Test
    void allChannelsInZeroOneRange() {
        ProceduralPartMesher mesher = new ProceduralPartMesher();
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CONE;
        spec.length = 1f;
        spec.radius = 0.3f;
        ProceduralMeshData data = mesher.build(spec);

        float[] colors = PatternStamper.stamp(data, spec, 99L);

        for (int i = 0; i < colors.length; i++) {
            assertTrue(colors[i] >= 0f && colors[i] <= 1f,
                "color channel " + (i % 4) + " at vertex " + (i / 4) + " = " + colors[i] + " out of [0,1]");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.skin.PatternStamperTest" --info`
Expected: FAIL — `PatternStamper` does not exist.

- [ ] **Step 3: Implement PatternStamper**

```java
package com.galacticodyssey.fauna.skin;

import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import com.galacticodyssey.fauna.geometry.ProceduralMeshData;

public final class PatternStamper {

    private PatternStamper() {}

    public static float[] stamp(ProceduralMeshData data, PartGeometrySpec spec, long colorSeed) {
        int vertCount = data.positionCount();
        float[] colors = new float[vertCount * 4];

        float length = Math.max(0.001f, spec.length);
        float maxRadius = Math.max(0.001f, spec.radius);

        for (int v = 0; v < vertCount; v++) {
            float px = data.positions[v * 3];
            float py = data.positions[v * 3 + 1];
            float pz = data.positions[v * 3 + 2];
            float nx = data.normals[v * 3];
            float ny = data.normals[v * 3 + 1];

            // R: dorsal/ventral — dot(normal, up=(0,1,0)), remapped from [-1,1] to [0,1]
            // ny > 0 = dorsal (top, R→0), ny < 0 = ventral (bottom, R→1)
            float dorsalVentral = 0.5f - ny * 0.5f;

            // G: limb-axis gradient — Z position along part axis, normalized to [0,1]
            float limbAxis = Math.max(0f, Math.min(1f, pz / length));

            // B: spatial hash for pattern variation
            float hash = spatialHash(px, py, pz, colorSeed);

            // A: curvature estimate — how far the vertex is from the axis relative to max radius
            float radialDist = (float) Math.sqrt(px * px + py * py);
            float curvature = 1f - Math.min(1f, radialDist / maxRadius);

            colors[v * 4]     = clamp01(dorsalVentral);
            colors[v * 4 + 1] = clamp01(limbAxis);
            colors[v * 4 + 2] = clamp01(hash);
            colors[v * 4 + 3] = clamp01(curvature);
        }

        return colors;
    }

    private static float spatialHash(float x, float y, float z, long seed) {
        long h = seed;
        h ^= Float.floatToIntBits(x * 7.13f) * 0x9E3779B97F4A7C15L;
        h ^= Float.floatToIntBits(y * 11.37f) * 0x6C62272E07BB0142L;
        h ^= Float.floatToIntBits(z * 5.91f) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (float) ((h & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.skin.PatternStamperTest" --info`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/skin/PatternStamper.java \
        core/src/test/java/com/galacticodyssey/fauna/skin/PatternStamperTest.java
git commit -m "feat(fauna): PatternStamper — bakes dorsal/ventral, limb axis, hash, curvature into vertex colors"
```

---

### Task 5: Wire CreatureSkinSpec into CreatureSpec and CreatureAssembler

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureSpec.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/assembly/CreatureAssembler.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/components/CreatureRenderComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/CreatureGeneratorTest.java` (add test)

- [ ] **Step 1: Add skinSpec field to CreatureSpec**

After the `colorSeed` field (line 24), add:

```java
public com.galacticodyssey.fauna.skin.CreatureSkinSpec skinSpec;
```

- [ ] **Step 2: Generate skinSpec in CreatureAssembler**

In `CreatureAssembler.assemble()`, after `spec.colorSeed = rng.nextLong();` (line 33), add:

```java
spec.skinSpec = com.galacticodyssey.fauna.skin.PaletteGenerator.generate(
    spec.colorSeed, com.galacticodyssey.planet.BiomeType.GRASSLAND, arch.bodyPlan);
```

Note: biome is hardcoded to GRASSLAND for now — Cycle D will pass the actual biome. The `colorSeed` is already set on the line above, so we use it directly.

- [ ] **Step 3: Update CreatureRenderComponent — replace tint fields with skinSpec reference**

Replace the tint fields with a `CreatureSkinSpec` reference:

```java
package com.galacticodyssey.fauna.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.galacticodyssey.fauna.skin.CreatureSkinSpec;

public class CreatureRenderComponent implements Component {
    public Object modelInstance = null;
    public ModelInstance skinnedInstance = null;
    public CreatureSkinSpec skinSpec = null;
}
```

- [ ] **Step 4: Add test for skinSpec propagation**

Append to `CreatureGeneratorTest.java`:

```java
@Test
void skinSpecIsPropagatedFromAssembly() {
    CreatureSpec spec = new CreatureGenerator(reg).generate("quad", 42L);
    assertNotNull(spec.skinSpec, "skinSpec should be populated during assembly");
    assertNotNull(spec.skinSpec.patternType);
    assertTrue(spec.skinSpec.primaryR >= 0f && spec.skinSpec.primaryR <= 1f);
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.*" --info`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/CreatureSpec.java \
        core/src/main/java/com/galacticodyssey/fauna/assembly/CreatureAssembler.java \
        core/src/main/java/com/galacticodyssey/fauna/components/CreatureRenderComponent.java \
        core/src/test/java/com/galacticodyssey/fauna/CreatureGeneratorTest.java
git commit -m "feat(fauna): wire CreatureSkinSpec through assembly pipeline"
```

---

### Task 6: Create creature skin shaders

**Files:**
- Create: `core/src/main/resources/shaders/creature_skin.vert`
- Create: `core/src/main/resources/shaders/creature_skin.frag`

These shaders output to the same G-buffer format as the existing `gbuffer.vert`/`gbuffer.frag` but interpret vertex colors as pattern metadata and composite the final albedo from per-creature uniforms.

- [ ] **Step 1: Write creature_skin.vert**

This is nearly identical to `gbuffer.vert` but always passes vertex colors and adds an object-space position varying for the fragment shader's noise functions.

```glsl
#version 330

in vec3 a_position;
in vec3 a_normal;
in vec4 a_color;

uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat3 u_normalMatrix;

out vec3 v_viewNormal;
out vec3 v_worldPos;
out vec4 v_color;       // pattern metadata channels
out vec3 v_objectPos;   // object-space position for noise

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;
    v_objectPos = a_position;
    gl_Position = u_projViewTrans * worldPos;
    v_viewNormal = normalize(u_normalMatrix * a_normal);
    v_color = a_color;
}
```

- [ ] **Step 2: Write creature_skin.frag**

```glsl
#version 330

#include "include/normal_encoding.glsl"

in vec3 v_viewNormal;
in vec3 v_worldPos;
in vec4 v_color;
in vec3 v_objectPos;

// Per-creature uniforms
uniform int u_patternType;        // 0=SOLID,1=STRIPES,2=SPOTS,3=ROSETTES,4=MOTTLED,5=BIOLUMINESCENT
uniform vec3 u_palette[4];        // [0]=primary/dorsal, [1]=secondary/pattern, [2]=accent, [3]=ventral
uniform float u_patternScale;
uniform float u_patternContrast;
uniform float u_bioGlow;
uniform float u_roughness;
uniform float u_metallic;

layout(location = 0) out vec4 rt0_albedoMetallic;
layout(location = 1) out vec4 rt1_normalRoughnessAO;
layout(location = 2) out vec2 rt2_emissive;

// Simple noise helpers
float hash31(vec3 p) {
    p = fract(p * vec3(443.8975, 397.2973, 491.1871));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash31(i), hash31(i + vec3(1,0,0)), f.x),
            mix(hash31(i + vec3(0,1,0)), hash31(i + vec3(1,1,0)), f.x), f.y),
        mix(mix(hash31(i + vec3(0,0,1)), hash31(i + vec3(1,0,1)), f.x),
            mix(hash31(i + vec3(0,1,1)), hash31(i + vec3(1,1,1)), f.x), f.y),
        f.z);
}

void main() {
    float dorsalVentral = v_color.r;  // 0=dorsal, 1=ventral
    float limbAxis      = v_color.g;  // 0=proximal, 1=distal
    float spatialHash   = v_color.b;  // pre-baked hash
    float curvature     = v_color.a;  // 0=flat, 1=high curvature

    // Base color: dorsal/ventral interpolation
    vec3 baseColor = mix(u_palette[0], u_palette[3], dorsalVentral);

    // Pattern modulation between primary and secondary
    float pattern = 0.0;
    vec3 objScaled = v_objectPos * u_patternScale;

    if (u_patternType == 1) {
        // STRIPES: directional sine along limb axis
        pattern = smoothstep(0.5 - u_patternContrast * 0.5, 0.5 + u_patternContrast * 0.5,
                            sin(limbAxis * u_patternScale * 3.14159 * 4.0) * 0.5 + 0.5);
    } else if (u_patternType == 2) {
        // SPOTS: thresholded noise
        float n = noise3(objScaled * 3.0);
        pattern = smoothstep(0.55 - u_patternContrast * 0.15, 0.55, n);
    } else if (u_patternType == 3) {
        // ROSETTES: inverted spots (rings)
        float n = noise3(objScaled * 3.0);
        float inner = smoothstep(0.55, 0.6, n);
        float outer = smoothstep(0.45, 0.5, n);
        pattern = outer - inner;
    } else if (u_patternType == 4) {
        // MOTTLED: multi-octave noise
        float n = noise3(objScaled) * 0.5 + noise3(objScaled * 2.0) * 0.3 + noise3(objScaled * 4.0) * 0.2;
        pattern = smoothstep(0.4 - u_patternContrast * 0.2, 0.6 + u_patternContrast * 0.2, n);
    } else if (u_patternType == 5) {
        // BIOLUMINESCENT: mottled base + curvature glow
        float n = noise3(objScaled) * 0.5 + noise3(objScaled * 2.0) * 0.5;
        pattern = smoothstep(0.4, 0.6, n);
    }
    // u_patternType == 0 (SOLID): pattern stays 0.0

    vec3 albedo = mix(baseColor, u_palette[1], pattern);

    // Limb darkening: extremities slightly darker
    albedo *= mix(1.0, 0.75, limbAxis * 0.5);

    // Curvature highlighting: ridges slightly lighter
    albedo += vec3(curvature * 0.03);

    // Emissive for bioluminescence
    float emissive = 0.0;
    if (u_patternType == 5 && u_bioGlow > 0.0) {
        float glowMask = pattern * curvature;
        emissive = glowMask * u_bioGlow;
        albedo += u_palette[2] * glowMask * u_bioGlow * 0.5;
    }

    albedo = clamp(albedo, 0.0, 1.0);

    // G-buffer output (same format as gbuffer.frag)
    rt0_albedoMetallic = vec4(albedo, u_metallic);
    vec2 encNormal = octEncode(normalize(v_viewNormal));
    rt1_normalRoughnessAO = vec4(encNormal, u_roughness, 1.0);
    rt2_emissive = vec2(emissive, 0.0);
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/resources/shaders/creature_skin.vert \
        core/src/main/resources/shaders/creature_skin.frag
git commit -m "feat(fauna): creature_skin.vert/frag — pattern compositing shader for G-buffer"
```

---

### Task 7: CreatureSkinShader and shader provider

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/skin/CreatureSkinShader.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/skin/CreatureSkinShaderProvider.java`

- [ ] **Step 1: Write CreatureSkinShader**

This is a libGDX `Shader` implementation that loads the creature skin shader program and sets per-creature uniforms from material attributes.

```java
package com.galacticodyssey.fauna.skin;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.galacticodyssey.rendering.shaders.ShaderUtils;

public class CreatureSkinShader implements Shader {
    private ShaderProgram program;
    private Camera camera;
    private RenderContext context;
    private final Matrix3 tmpMat3 = new Matrix3();
    private final Matrix4 tmpMat4 = new Matrix4();

    // Uniform locations (cached)
    private int u_projViewTrans, u_worldTrans, u_normalMatrix;
    private int u_patternType, u_patternScale, u_patternContrast, u_bioGlow;
    private int u_roughness, u_metallic;
    private int u_palette;

    @Override
    public void init() {
        String vert = ShaderUtils.loadShader("shaders/creature_skin.vert");
        String frag = ShaderUtils.loadShader("shaders/creature_skin.frag");
        frag = ShaderUtils.resolveIncludes(frag, "shaders/");
        program = new ShaderProgram(vert, frag);
        if (!program.isCompiled()) {
            throw new GdxRuntimeException("Creature skin shader failed: " + program.getLog());
        }
        u_projViewTrans = program.getUniformLocation("u_projViewTrans");
        u_worldTrans = program.getUniformLocation("u_worldTrans");
        u_normalMatrix = program.getUniformLocation("u_normalMatrix");
        u_patternType = program.getUniformLocation("u_patternType");
        u_patternScale = program.getUniformLocation("u_patternScale");
        u_patternContrast = program.getUniformLocation("u_patternContrast");
        u_bioGlow = program.getUniformLocation("u_bioGlow");
        u_roughness = program.getUniformLocation("u_roughness");
        u_metallic = program.getUniformLocation("u_metallic");
        u_palette = program.getUniformLocation("u_palette[0]");
    }

    @Override
    public int compareTo(Shader other) { return 0; }

    @Override
    public boolean canRender(Renderable instance) { return true; }

    @Override
    public void begin(Camera camera, RenderContext context) {
        this.camera = camera;
        this.context = context;
        program.bind();
        program.setUniformMatrix(u_projViewTrans, camera.combined);
    }

    @Override
    public void render(Renderable renderable) {
        program.setUniformMatrix(u_worldTrans, renderable.worldTransform);

        // Normal matrix: inverse transpose of upper-3x3 of view * world
        tmpMat4.set(camera.view).mul(renderable.worldTransform);
        tmpMat3.set(tmpMat4).inv().transpose();
        program.setUniformMatrix(u_normalMatrix, tmpMat3);

        // Read skin spec from material userData
        CreatureSkinSpec skin = null;
        if (renderable.material != null && renderable.material.id != null) {
            Object ud = renderable.userData;
            if (ud instanceof CreatureSkinSpec) skin = (CreatureSkinSpec) ud;
        }
        if (skin == null) {
            // Fallback: use renderable's userData directly
            if (renderable.userData instanceof CreatureSkinSpec) {
                skin = (CreatureSkinSpec) renderable.userData;
            }
        }

        if (skin != null) {
            program.setUniformi(u_patternType, skin.patternType.shaderId);
            program.setUniformf(u_patternScale, skin.patternScale);
            program.setUniformf(u_patternContrast, skin.patternContrast);
            program.setUniformf(u_bioGlow, skin.bioGlow);
            program.setUniformf(u_roughness, skin.roughness);
            program.setUniformf(u_metallic, skin.metallic);

            float[] palette = new float[] {
                skin.primaryR, skin.primaryG, skin.primaryB,
                skin.secondaryR, skin.secondaryG, skin.secondaryB,
                skin.accentR, skin.accentG, skin.accentB,
                skin.ventralR, skin.ventralG, skin.ventralB
            };
            program.setUniform3fv(u_palette, palette, 0, 12);
        } else {
            program.setUniformi(u_patternType, 0);
            program.setUniformf(u_roughness, 0.7f);
            program.setUniformf(u_metallic, 0f);
            program.setUniformf(u_bioGlow, 0f);
        }

        renderable.meshPart.render(program);
    }

    @Override
    public void end() {}

    @Override
    public void dispose() {
        if (program != null) program.dispose();
    }
}
```

- [ ] **Step 2: Write CreatureSkinShaderProvider**

```java
package com.galacticodyssey.fauna.skin;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;

public class CreatureSkinShaderProvider implements ShaderProvider {
    private CreatureSkinShader shader;

    @Override
    public Shader getShader(Renderable renderable) {
        if (shader == null) {
            shader = new CreatureSkinShader();
            shader.init();
        }
        return shader;
    }

    @Override
    public void dispose() {
        if (shader != null) shader.dispose();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/skin/CreatureSkinShader.java \
        core/src/main/java/com/galacticodyssey/fauna/skin/CreatureSkinShaderProvider.java
git commit -m "feat(fauna): CreatureSkinShader + provider — creature pattern rendering pipeline"
```

---

### Task 8: Update CreatureMeshBuilder to emit vertex colors and set skin uniforms

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureMeshBuilder.java`

The `buildSkinned()` method is updated to:
1. Include `Usage.ColorPacked` in the vertex attributes
2. Call `PatternStamper.stamp()` for each part and set vertex colors via `MeshPartBuilder.setColor()`
3. Store the `CreatureSkinSpec` on each `Renderable`'s `userData` so the shader can read it

- [ ] **Step 1: Update buildSkinned method**

In `CreatureMeshBuilder.java`, modify the `buildSkinned` method. The key changes are:
- Add `Usage.ColorPacked` to the vertex attributes
- After building each mesh triangle, stamp vertex colors
- Store skinSpec on the ModelInstance's userData

Replace the `buildSkinned` method with:

```java
public ModelInstance buildSkinned(CreatureSpec spec, CreatureRig rig) {
    ModelBuilder mb = new ModelBuilder();
    mb.begin();

    Map<Integer, Node> nodeMap = new HashMap<>();
    ProceduralPartMesher mesher = new ProceduralPartMesher();

    for (int i = 0; i < spec.allNodes.size(); i++) {
        AssembledNode an = spec.allNodes.get(i);

        Node node = mb.node();
        node.id = rig.getBone(i).name;

        Material mat = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        MeshPartBuilder mpb = mb.part("mesh_" + i, GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.ColorPacked, mat);

        ProceduralMeshData data = mesher.build(an.part.geometry);
        float[] vertColors = com.galacticodyssey.fauna.skin.PatternStamper.stamp(
            data, an.part.geometry, spec.colorSeed + i);
        float[] p = data.positions;

        for (int t = 0; t < data.indices.length; t += 3) {
            int a = data.indices[t] & 0xFFFF;
            int b = data.indices[t + 1] & 0xFFFF;
            int c = data.indices[t + 2] & 0xFFFF;

            mpb.setColor(vertColors[a*4], vertColors[a*4+1], vertColors[a*4+2], vertColors[a*4+3]);
            com.badlogic.gdx.math.Vector3 va = new Vector3(p[a*3], p[a*3+1], p[a*3+2]);
            com.badlogic.gdx.math.Vector3 na = new Vector3(data.normals[a*3], data.normals[a*3+1], data.normals[a*3+2]);

            mpb.setColor(vertColors[b*4], vertColors[b*4+1], vertColors[b*4+2], vertColors[b*4+3]);
            com.badlogic.gdx.math.Vector3 vb = new Vector3(p[b*3], p[b*3+1], p[b*3+2]);
            com.badlogic.gdx.math.Vector3 nb = new Vector3(data.normals[b*3], data.normals[b*3+1], data.normals[b*3+2]);

            mpb.setColor(vertColors[c*4], vertColors[c*4+1], vertColors[c*4+2], vertColors[c*4+3]);
            com.badlogic.gdx.math.Vector3 vc = new Vector3(p[c*3], p[c*3+1], p[c*3+2]);
            com.badlogic.gdx.math.Vector3 nc = new Vector3(data.normals[c*3], data.normals[c*3+1], data.normals[c*3+2]);

            mpb.triangle(
                new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo().setPos(va).setNor(na).setCol(vertColors[a*4], vertColors[a*4+1], vertColors[a*4+2], vertColors[a*4+3]),
                new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo().setPos(vb).setNor(nb).setCol(vertColors[b*4], vertColors[b*4+1], vertColors[b*4+2], vertColors[b*4+3]),
                new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo().setPos(vc).setNor(nc).setCol(vertColors[c*4], vertColors[c*4+1], vertColors[c*4+2], vertColors[c*4+3])
            );
        }

        nodeMap.put(i, node);
    }

    Model model = mb.end();
    ownedModels.add(model);

    for (int i = 0; i < rig.boneCount(); i++) {
        Node node = nodeMap.get(i);
        Bone bone = rig.getBone(i);
        node.localTransform.set(bone.bindPose);
        node.localTransform.scl(spec.allNodes.get(i).scale);

        if (bone.parentIndex >= 0) {
            Node parent = nodeMap.get(bone.parentIndex);
            model.nodes.removeValue(node, true);
            parent.addChild(node);
        }
    }

    model.calculateTransforms();
    ModelInstance instance = new ModelInstance(model);
    instance.userData = spec.skinSpec;
    return instance;
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/CreatureMeshBuilder.java
git commit -m "feat(fauna): emit vertex colors + skinSpec userData in buildSkinned"
```

---

### Task 9: Update GameScreen to use creature skin shader

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/FaunaDebugSpawner.java`

The creature rendering in `GameScreen.renderCreatures()` currently uses `gbufferBatch`. We create a separate `ModelBatch` with `CreatureSkinShaderProvider` for creature rendering.

- [ ] **Step 1: Add creature ModelBatch field to GameScreen**

Find the field declarations area in GameScreen (near other ModelBatch/rendering fields). Add:

```java
private com.badlogic.gdx.graphics.g3d.ModelBatch creatureBatch;
```

In the GameScreen constructor or `show()` method where other batches are initialized, add:

```java
creatureBatch = new com.badlogic.gdx.graphics.g3d.ModelBatch(
    new com.galacticodyssey.fauna.skin.CreatureSkinShaderProvider());
```

In the `dispose()` method, add:

```java
creatureBatch.dispose();
```

- [ ] **Step 2: Update renderCreatures to use creature batch for skinned creatures**

Replace the `renderCreatures()` method:

```java
private void renderCreatures() {
    var creatures = gameWorld.getEngine().getEntitiesFor(
        Family.all(CreatureRenderComponent.class).get());
    creatureRenderQueue.clear();
    for (int i = 0; i < creatures.size(); i++) {
        CreatureRenderComponent render = creatures.get(i).getComponent(CreatureRenderComponent.class);
        if (render.skinnedInstance != null) {
            creatureRenderQueue.add(render.skinnedInstance);
        } else if (render.modelInstance instanceof Array) {
            creatureRenderQueue.addAll((Array<ModelInstance>) render.modelInstance);
        }
    }
    // Use creature skin shader for all creature rendering
    creatureBatch.begin(camera);
    for (int i = 0; i < creatureRenderQueue.size; i++) {
        creatureBatch.render(creatureRenderQueue.get(i));
    }
    creatureBatch.end();
}
```

- [ ] **Step 3: Update FaunaDebugSpawner to copy skinSpec to render component**

In `FaunaDebugSpawner.spawnInFront()`, after setting `render.skinnedInstance`, add:

```java
render.skinSpec = spec.skinSpec;
```

- [ ] **Step 4: Run all fauna tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.*" --info`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java \
        core/src/main/java/com/galacticodyssey/fauna/FaunaDebugSpawner.java
git commit -m "feat(fauna): creature skin shader rendering pipeline in GameScreen"
```

---

### Task 10: CreatureFactory wires skinSpec to render component

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java`

- [ ] **Step 1: Set skinSpec on CreatureRenderComponent in create()**

After `e.add(new CreatureRenderComponent());` (line 38), retrieve the component and set skinSpec:

```java
CreatureRenderComponent renderComp = e.getComponent(CreatureRenderComponent.class);
renderComp.skinSpec = spec.skinSpec;
```

This is a one-line addition. The `CreatureRenderComponent` was just added to the entity on the line above, so `getComponent` will return it.

- [ ] **Step 2: Run all fauna tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.*" --info`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java
git commit -m "feat(fauna): wire skinSpec from CreatureSpec to CreatureRenderComponent in factory"
```
