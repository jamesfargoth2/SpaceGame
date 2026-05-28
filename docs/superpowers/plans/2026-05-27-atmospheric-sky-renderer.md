# Atmospheric Sky Renderer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static gradient sky with physically-based atmospheric scattering, volumetric clouds, day/night cycle, and dynamic fog that matches the sky — fixing the horizon bleed bug.

**Architecture:** `ScatteringParams` derives physical coefficients from the existing `Atmosphere`/`Planet` data. `AtmosphericSkyRenderer` renders a fullscreen quad with analytic Rayleigh+Mie scattering and integrated cloud raymarching. `DayNightCycle` rotates the sun based on planet rotation. Fog color is sampled from the scattering result each frame, eliminating the sky/terrain mismatch.

**Tech Stack:** Java 21, libGDX 1.13.5 (GL20, ShaderProgram, Mesh, PerspectiveCamera), JUnit 5. GLSL shaders embedded as Java string constants.

**Spec:** `docs/superpowers/specs/2026-05-27-atmospheric-sky-renderer-design.md`

---

## File Map

```
NEW FILES:
  core/src/main/java/com/galacticodyssey/planet/ScatteringParams.java
  core/src/main/java/com/galacticodyssey/ui/DayNightCycle.java
  core/src/main/java/com/galacticodyssey/ui/AtmosphericSkyRenderer.java
  core/src/test/java/com/galacticodyssey/planet/ScatteringParamsTest.java
  core/src/test/java/com/galacticodyssey/ui/DayNightCycleTest.java

MODIFIED FILES:
  core/src/main/java/com/galacticodyssey/ui/GameScreen.java
  core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java
```

---

### Task 1: ScatteringParams — Data Class

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/planet/ScatteringParamsTest.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/ScatteringParams.java`

- [ ] **Step 1: Write ScatteringParams tests**

```java
package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScatteringParamsTest {

    @Test
    void earthLikeHasBlueRayleigh() {
        ScatteringParams p = ScatteringParams.earthLike();
        assertTrue(p.rayleighCoeffR < p.rayleighCoeffB,
            "Earth-like Rayleigh blue (" + p.rayleighCoeffB +
            ") should exceed red (" + p.rayleighCoeffR + ")");
    }

    @Test
    void earthLikeHasReasonableDefaults() {
        ScatteringParams p = ScatteringParams.earthLike();
        assertTrue(p.planetRadius > 0f);
        assertTrue(p.atmosphereRadius > p.planetRadius);
        assertTrue(p.scaleHeightRayleigh > 0f);
        assertTrue(p.scaleHeightMie > 0f);
        assertTrue(p.mieCoeff > 0f);
        assertTrue(p.mieG > 0f && p.mieG < 1f);
        assertTrue(p.sunIntensity > 0f);
        assertTrue(p.cloudCoverage >= 0f && p.cloudCoverage <= 1f);
        assertTrue(p.cloudBase < p.cloudTop);
        assertTrue(p.fogDensity > 0f);
    }

    @Test
    void co2AtmosphereShiftsRayleighTowardAmber() {
        Map<Gas, Float> co2Comp = new EnumMap<>(Gas.class);
        co2Comp.put(Gas.CO2, 0.95f);
        co2Comp.put(Gas.N2, 0.05f);
        Atmosphere co2Atmo = new Atmosphere(co2Comp, 1.0f, 1.0f, 250f, 250f,
            false, EnumSet.of(AtmoHazard.NONE));
        Planet co2Planet = new Planet(1L, PlanetType.ARID, 1.0f, 1.0f, 5.5f,
            24f, 10f, false);
        co2Planet.atmosphere = co2Atmo;

        ScatteringParams p = ScatteringParams.fromPlanet(co2Planet);
        assertTrue(p.rayleighCoeffR > p.rayleighCoeffB,
            "CO2-heavy Rayleigh red (" + p.rayleighCoeffR +
            ") should exceed blue (" + p.rayleighCoeffB + ")");
    }

    @Test
    void vacuumAtmosphereNearZeroScattering() {
        Map<Gas, Float> thinComp = new EnumMap<>(Gas.class);
        thinComp.put(Gas.N2, 1.0f);
        Atmosphere vacuum = new Atmosphere(thinComp, 0.001f, 1.0f, 200f, 200f,
            false, EnumSet.of(AtmoHazard.VACUUM));
        Planet barren = new Planet(2L, PlanetType.BARREN, 0.5f, 0.3f, 4.0f,
            48f, 5f, false);
        barren.atmosphere = vacuum;

        ScatteringParams p = ScatteringParams.fromPlanet(barren);
        assertTrue(p.rayleighCoeffR < 1e-7f);
        assertTrue(p.rayleighCoeffG < 1e-7f);
        assertTrue(p.rayleighCoeffB < 1e-7f);
        assertEquals(0f, p.fogDensity, 1e-6f);
        assertEquals(0f, p.cloudCoverage, 1e-6f);
    }

    @Test
    void highPressureLowersScaleHeightAndIncreaseFogDensity() {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.7f);
        comp.put(Gas.O2, 0.3f);
        Atmosphere dense = new Atmosphere(comp, 15.0f, 2.0f, 300f, 600f,
            false, EnumSet.of(AtmoHazard.CRUSHING));
        Planet crushPlanet = new Planet(3L, PlanetType.TOXIC, 1.2f, 1.5f, 6.0f,
            30f, 8f, false);
        crushPlanet.atmosphere = dense;

        ScatteringParams earthP = ScatteringParams.earthLike();
        ScatteringParams crushP = ScatteringParams.fromPlanet(crushPlanet);

        assertTrue(crushP.scaleHeightRayleigh < earthP.scaleHeightRayleigh,
            "Crushing atmosphere should have lower scale height");
        assertTrue(crushP.fogDensity > earthP.fogDensity,
            "Crushing atmosphere should have denser fog");
    }

    @Test
    void oceanPlanetHighCloudCoverage() {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.55f);
        comp.put(Gas.H2O, 0.35f);
        comp.put(Gas.CO2, 0.10f);
        Atmosphere humid = new Atmosphere(comp, 1.2f, 1.5f, 280f, 300f,
            true, EnumSet.of(AtmoHazard.NONE));
        Planet ocean = new Planet(4L, PlanetType.OCEAN, 1.1f, 1.2f, 5.5f,
            22f, 15f, false);
        ocean.atmosphere = humid;

        ScatteringParams p = ScatteringParams.fromPlanet(ocean);
        assertTrue(p.cloudCoverage >= 0.5f,
            "Ocean planet cloud coverage (" + p.cloudCoverage + ") should be >= 0.5");
    }

    @Test
    void nullAtmosphereUsesVacuumDefaults() {
        Planet noAtmo = new Planet(5L, PlanetType.BARREN, 0.4f, 0.2f, 4.0f,
            100f, 2f, false);
        // atmosphere is null by default
        ScatteringParams p = ScatteringParams.fromPlanet(noAtmo);
        assertTrue(p.rayleighCoeffR < 1e-7f);
        assertEquals(0f, p.fogDensity, 1e-6f);
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.ScatteringParamsTest" --info`
Expected: Compilation error — `ScatteringParams` does not exist.

- [ ] **Step 3: Implement ScatteringParams**

```java
package com.galacticodyssey.planet;

public final class ScatteringParams {

    // Rayleigh scattering coefficients (per-wavelength: R, G, B)
    public final float rayleighCoeffR;
    public final float rayleighCoeffG;
    public final float rayleighCoeffB;

    // Mie scattering
    public final float mieCoeff;
    public final float mieG;

    // Absorption (ozone-like, per-wavelength)
    public final float absorptionCoeffR;
    public final float absorptionCoeffG;
    public final float absorptionCoeffB;

    // Scale heights (render units)
    public final float scaleHeightRayleigh;
    public final float scaleHeightMie;

    // Atmosphere geometry (render units)
    public final float planetRadius;
    public final float atmosphereRadius;

    // Sun
    public final float sunIntensity;
    public final float sunAngularRadius;

    // Clouds
    public final float cloudBase;
    public final float cloudTop;
    public final float cloudCoverage;

    // Fog
    public final float fogDensity;

    private ScatteringParams(float rayleighR, float rayleighG, float rayleighB,
                             float mieCoeff, float mieG,
                             float absR, float absG, float absB,
                             float scaleHeightR, float scaleHeightM,
                             float planetRadius, float atmosphereRadius,
                             float sunIntensity, float sunAngularRadius,
                             float cloudBase, float cloudTop, float cloudCoverage,
                             float fogDensity) {
        this.rayleighCoeffR = rayleighR;
        this.rayleighCoeffG = rayleighG;
        this.rayleighCoeffB = rayleighB;
        this.mieCoeff = mieCoeff;
        this.mieG = mieG;
        this.absorptionCoeffR = absR;
        this.absorptionCoeffG = absG;
        this.absorptionCoeffB = absB;
        this.scaleHeightRayleigh = scaleHeightR;
        this.scaleHeightMie = scaleHeightM;
        this.planetRadius = planetRadius;
        this.atmosphereRadius = atmosphereRadius;
        this.sunIntensity = sunIntensity;
        this.sunAngularRadius = sunAngularRadius;
        this.cloudBase = cloudBase;
        this.cloudTop = cloudTop;
        this.cloudCoverage = cloudCoverage;
        this.fogDensity = fogDensity;
    }

    public static ScatteringParams earthLike() {
        float pR = 6371f;
        float scaleH = 8.5f;
        return new ScatteringParams(
            5.5e-6f, 13.0e-6f, 22.4e-6f,   // Rayleigh RGB (blue > red)
            21e-6f, 0.76f,                   // Mie coeff + asymmetry
            2.1e-6f, 0.0f, 0.0f,            // Absorption (ozone-like, mostly red channel)
            scaleH, 1.2f,                    // Scale heights
            pR, pR + scaleH * 6f,            // Planet + atmosphere radius
            22.0f, 0.00465f,                 // Sun intensity + angular radius (radians)
            1.5f, 4.0f, 0.45f,              // Cloud base, top, coverage
            0.004f                           // Fog density
        );
    }

    public static ScatteringParams fromPlanet(Planet planet) {
        if (planet.atmosphere == null || planet.atmosphere.surfacePressure < 0.01f) {
            return vacuum(planet.radius);
        }

        Atmosphere atmo = planet.atmosphere;
        float pressure = atmo.surfacePressure;

        float n2Frac = atmo.composition.getOrDefault(Gas.N2, 0f);
        float o2Frac = atmo.composition.getOrDefault(Gas.O2, 0f);
        float co2Frac = atmo.composition.getOrDefault(Gas.CO2, 0f);
        float h2oFrac = atmo.composition.getOrDefault(Gas.H2O, 0f);
        float so2Frac = atmo.composition.getOrDefault(Gas.SO2, 0f);

        float earthLikeFrac = n2Frac + o2Frac;
        float heavyFrac = co2Frac + so2Frac;

        // Rayleigh: wavelength-dependent, scaled by pressure and composition
        // Earth baseline: R=5.5e-6, G=13.0e-6, B=22.4e-6
        // CO2/SO2-heavy shifts red channel up, blue channel down
        float pressureScale = pressure;
        float rBase = 5.5e-6f * earthLikeFrac + 18.0e-6f * heavyFrac;
        float gBase = 13.0e-6f * earthLikeFrac + 14.0e-6f * heavyFrac;
        float bBase = 22.4e-6f * earthLikeFrac + 8.0e-6f * heavyFrac;

        float rayleighR = rBase * pressureScale;
        float rayleighG = gBase * pressureScale;
        float rayleighB = bBase * pressureScale;

        // Scale height: inversely related to pressure
        float scaleH = 8.5f / (float) Math.sqrt(Math.max(0.1f, pressure));
        scaleH = Math.max(4f, Math.min(12f, scaleH));

        // Mie: driven by planet type (aerosol proxy)
        float mieCoeff;
        float mieG;
        switch (planet.type) {
            case ARID:
                mieCoeff = 30e-6f * pressure;
                mieG = 0.85f;
                break;
            case TOXIC:
                mieCoeff = 50e-6f * pressure;
                mieG = 0.6f;
                break;
            case OCEAN:
                mieCoeff = 5e-6f * pressure;
                mieG = 0.76f;
                break;
            case ICE_WORLD:
                mieCoeff = 10e-6f * pressure;
                mieG = 0.7f;
                break;
            default:
                mieCoeff = 21e-6f * pressure;
                mieG = 0.76f;
                break;
        }

        // Absorption: O2-rich gets ozone-like red absorption, SO2 gets yellow tint
        float absR = o2Frac > 0.1f ? 2.1e-6f * pressure : 0f;
        float absG = so2Frac > 0.05f ? 1.5e-6f * pressure : 0f;
        float absB = 0f;

        // Planet geometry
        float pR = planet.radius * 6371f;
        float atmoR = pR + scaleH * 6f;

        // Sun intensity from equilibrium temperature
        float sunIntensity = 22.0f * (atmo.equilibriumTemp / 255f);

        // Clouds: driven by moisture (H2O fraction)
        float cloudCoverage;
        switch (planet.type) {
            case OCEAN:     cloudCoverage = 0.6f + h2oFrac * 0.3f; break;
            case ARID:      cloudCoverage = 0.05f + h2oFrac * 0.15f; break;
            case ICE_WORLD: cloudCoverage = 0.3f + h2oFrac * 0.2f; break;
            case TOXIC:     cloudCoverage = 0.4f + so2Frac * 0.4f; break;
            default:        cloudCoverage = 0.3f + h2oFrac * 0.4f; break;
        }
        cloudCoverage = Math.max(0f, Math.min(1f, cloudCoverage));

        float cloudBase = 1.5f + (1f / Math.max(0.1f, pressure));
        float cloudTop = cloudBase + 2f + pressure * 0.5f;
        cloudBase = Math.max(1f, Math.min(4f, cloudBase));
        cloudTop = Math.max(cloudBase + 1f, Math.min(8f, cloudTop));

        // Fog density from pressure
        float fogDensity;
        if (pressure < 0.1f) fogDensity = 0.001f;
        else if (pressure < 2f) fogDensity = 0.004f * pressure;
        else if (pressure < 10f) fogDensity = 0.008f + (pressure - 2f) * 0.001f;
        else fogDensity = 0.02f + (pressure - 10f) * 0.002f;
        fogDensity = Math.min(0.05f, fogDensity);

        return new ScatteringParams(
            rayleighR, rayleighG, rayleighB,
            mieCoeff, mieG,
            absR, absG, absB,
            scaleH, 1.2f / (float) Math.sqrt(Math.max(0.1f, pressure)),
            pR, atmoR,
            sunIntensity, 0.00465f,
            cloudBase, cloudTop, cloudCoverage,
            fogDensity
        );
    }

    private static ScatteringParams vacuum(float radiusEarthRadii) {
        float pR = radiusEarthRadii * 6371f;
        return new ScatteringParams(
            0f, 0f, 0f,        // No scattering
            0f, 0.76f,          // No Mie
            0f, 0f, 0f,         // No absorption
            8.5f, 1.2f,         // Scale heights (unused but non-zero for safety)
            pR, pR + 50f,       // Thin shell
            22.0f, 0.00465f,    // Sun still visible
            0f, 0f, 0f,         // No clouds
            0f                   // No fog
        );
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.ScatteringParamsTest" --info`
Expected: All 7 tests pass.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/planet/ScatteringParams.java \
        core/src/test/java/com/galacticodyssey/planet/ScatteringParamsTest.java
git commit -m "feat(planet): add ScatteringParams with atmosphere-driven derivation"
```

---

### Task 2: DayNightCycle

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/ui/DayNightCycleTest.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/DayNightCycle.java`

- [ ] **Step 1: Write DayNightCycle tests**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DayNightCycleTest {

    @Test
    void timeOfDayStartsAtNoon() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertEquals(0.5f, cycle.getTimeOfDay(), 1e-5f);
    }

    @Test
    void timeAdvancesOverDayLength() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        cycle.update(50f); // half a day from noon → midnight
        assertEquals(0.0f, cycle.getTimeOfDay(), 0.01f);
    }

    @Test
    void timeWrapsAroundAfterFullDay() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        cycle.update(100f); // full cycle back to noon
        assertEquals(0.5f, cycle.getTimeOfDay(), 0.01f);
    }

    @Test
    void sunDirectionChangesOverTime() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        Vector3 sunAtNoon = new Vector3(cycle.getSunDirection());
        cycle.update(25f); // quarter day
        Vector3 sunLater = new Vector3(cycle.getSunDirection());
        assertNotEquals(sunAtNoon.x, sunLater.x, 0.01f);
    }

    @Test
    void sunIsAboveHorizonAtNoon() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertTrue(cycle.getSunAltitude() > 0f, "Sun should be above horizon at noon");
        assertFalse(cycle.isNight());
    }

    @Test
    void sunIsBelowHorizonAtMidnight() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        cycle.update(50f); // noon → midnight
        assertTrue(cycle.getSunAltitude() < 0f, "Sun should be below horizon at midnight");
        assertTrue(cycle.isNight());
    }

    @Test
    void sunIntensityZeroAtNight() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        cycle.update(50f); // midnight
        assertEquals(0f, cycle.getSunIntensity(), 0.01f);
    }

    @Test
    void sunIntensityOneAtNoon() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertEquals(1f, cycle.getSunIntensity(), 0.1f);
    }

    @Test
    void ambientIntensityAlwaysPositive() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertTrue(cycle.getAmbientIntensity() > 0f);
        cycle.update(50f); // midnight
        assertTrue(cycle.getAmbientIntensity() > 0f, "Ambient should be > 0 even at night");
    }

    @Test
    void starVisibilityOneAtNightZeroAtDay() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertEquals(0f, cycle.getStarVisibility(), 0.1f);
        cycle.update(50f); // midnight
        assertEquals(1f, cycle.getStarVisibility(), 0.1f);
    }

    @Test
    void tidallyLockedSunDoesNotMove() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, true);
        Vector3 sunA = new Vector3(cycle.getSunDirection());
        cycle.update(50f);
        Vector3 sunB = new Vector3(cycle.getSunDirection());
        assertEquals(sunA.x, sunB.x, 1e-5f);
        assertEquals(sunA.y, sunB.y, 1e-5f);
        assertEquals(sunA.z, sunB.z, 1e-5f);
    }

    @Test
    void sunDirectionIsNormalized() {
        DayNightCycle cycle = new DayNightCycle(100f, 23.5f, false);
        for (float t = 0; t < 100f; t += 5f) {
            cycle.update(5f);
            Vector3 dir = cycle.getSunDirection();
            assertEquals(1f, dir.len(), 0.001f,
                "Sun direction not normalized at time " + t);
        }
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.DayNightCycleTest" --info`
Expected: Compilation error — `DayNightCycle` does not exist.

- [ ] **Step 3: Implement DayNightCycle**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public final class DayNightCycle {

    private final float dayLengthSeconds;
    private final float axialTiltRadians;
    private final boolean tidallyLocked;

    private float elapsed;
    private final Vector3 sunDirection = new Vector3();

    public DayNightCycle(float dayLengthSeconds, float axialTiltDegrees,
                         boolean tidallyLocked) {
        this.dayLengthSeconds = Math.max(1f, dayLengthSeconds);
        this.axialTiltRadians = axialTiltDegrees * MathUtils.degreesToRadians;
        this.tidallyLocked = tidallyLocked;
        this.elapsed = dayLengthSeconds * 0.5f;
        recalcSunDirection();
    }

    public void update(float delta) {
        if (tidallyLocked) return;
        elapsed += delta;
        if (elapsed > dayLengthSeconds) {
            elapsed -= dayLengthSeconds * (int) (elapsed / dayLengthSeconds);
        }
        recalcSunDirection();
    }

    public Vector3 getSunDirection() {
        return sunDirection;
    }

    public float getSunAltitude() {
        return sunDirection.y;
    }

    public float getTimeOfDay() {
        return elapsed / dayLengthSeconds;
    }

    public boolean isNight() {
        return sunDirection.y < -0.05f;
    }

    public float getSunIntensity() {
        return smoothstep(-0.1f, 0.2f, sunDirection.y);
    }

    public float getAmbientIntensity() {
        float dayAmbient = 0.35f;
        float nightAmbient = 0.06f;
        float t = smoothstep(-0.1f, 0.2f, sunDirection.y);
        return nightAmbient + (dayAmbient - nightAmbient) * t;
    }

    public float getStarVisibility() {
        return 1f - smoothstep(-0.15f, 0.05f, sunDirection.y);
    }

    private void recalcSunDirection() {
        float angle = (elapsed / dayLengthSeconds) * MathUtils.PI2;
        float cosA = MathUtils.cos(angle);
        float sinA = MathUtils.sin(angle);

        float tiltCos = MathUtils.cos(axialTiltRadians);
        float tiltSin = MathUtils.sin(axialTiltRadians);

        sunDirection.set(
            sinA,
            cosA * tiltCos,
            cosA * tiltSin
        ).nor();
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.DayNightCycleTest" --info`
Expected: All 12 tests pass.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/DayNightCycle.java \
        core/src/test/java/com/galacticodyssey/ui/DayNightCycleTest.java
git commit -m "feat(ui): add DayNightCycle with sun rotation and lighting multipliers"
```

---

### Task 3: AtmosphericSkyRenderer — Shader + Renderer

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/AtmosphericSkyRenderer.java`

This is the core rendering class. No unit tests (it requires a GL context), but it will be visually verified in Task 5.

- [ ] **Step 1: Implement AtmosphericSkyRenderer**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.planet.ScatteringParams;

public final class AtmosphericSkyRenderer implements Disposable {

    private static final String VERT_SHADER =
        "attribute vec2 a_position;\n" +
        "uniform mat4 u_invViewProj;\n" +
        "uniform vec3 u_cameraPos;\n" +
        "varying vec3 v_rayDir;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(a_position, 0.9999, 1.0);\n" +
        "    vec4 farPoint = u_invViewProj * vec4(a_position, 1.0, 1.0);\n" +
        "    v_rayDir = farPoint.xyz / farPoint.w - u_cameraPos;\n" +
        "}\n";

    private static final String FRAG_SHADER =
        "#ifdef GL_ES\n" +
        "precision highp float;\n" +
        "#endif\n" +
        "\n" +
        "varying vec3 v_rayDir;\n" +
        "\n" +
        "// Atmosphere geometry\n" +
        "uniform float u_planetRadius;\n" +
        "uniform float u_atmosphereRadius;\n" +
        "\n" +
        "// Scattering\n" +
        "uniform vec3 u_rayleighCoeff;\n" +
        "uniform float u_mieCoeff;\n" +
        "uniform float u_mieG;\n" +
        "uniform vec3 u_absorptionCoeff;\n" +
        "uniform float u_scaleHeightRayleigh;\n" +
        "uniform float u_scaleHeightMie;\n" +
        "\n" +
        "// Sun\n" +
        "uniform vec3 u_sunDirection;\n" +
        "uniform float u_sunIntensity;\n" +
        "uniform float u_sunAngularRadius;\n" +
        "\n" +
        "// Clouds\n" +
        "uniform float u_cloudBase;\n" +
        "uniform float u_cloudTop;\n" +
        "uniform float u_cloudCoverage;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_windDirection;\n" +
        "\n" +
        "// Camera\n" +
        "uniform vec3 u_cameraPos;\n" +
        "\n" +
        "const int PRIMARY_STEPS = 12;\n" +
        "const int LIGHT_STEPS = 4;\n" +
        "const float PI = 3.14159265;\n" +
        "\n" +
        "// Ray-sphere intersection. Returns (near, far) or (-1,-1) if no hit.\n" +
        "vec2 raySphere(vec3 origin, vec3 dir, float radius) {\n" +
        "    float b = dot(origin, dir);\n" +
        "    float c = dot(origin, origin) - radius * radius;\n" +
        "    float d = b * b - c;\n" +
        "    if (d < 0.0) return vec2(-1.0);\n" +
        "    d = sqrt(d);\n" +
        "    return vec2(-b - d, -b + d);\n" +
        "}\n" +
        "\n" +
        "// Rayleigh phase function\n" +
        "float rayleighPhase(float cosTheta) {\n" +
        "    return (3.0 / (16.0 * PI)) * (1.0 + cosTheta * cosTheta);\n" +
        "}\n" +
        "\n" +
        "// Henyey-Greenstein phase function for Mie scattering\n" +
        "float miePhase(float cosTheta, float g) {\n" +
        "    float g2 = g * g;\n" +
        "    float num = (1.0 - g2);\n" +
        "    float denom = 4.0 * PI * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5);\n" +
        "    return num / denom;\n" +
        "}\n" +
        "\n" +
        "// Hash for noise\n" +
        "float hash(vec3 p) {\n" +
        "    p = fract(p * vec3(443.897, 441.423, 437.195));\n" +
        "    p += dot(p, p.yzx + 19.19);\n" +
        "    return fract((p.x + p.y) * p.z);\n" +
        "}\n" +
        "\n" +
        "// Value noise 3D\n" +
        "float noise3D(vec3 p) {\n" +
        "    vec3 i = floor(p);\n" +
        "    vec3 f = fract(p);\n" +
        "    f = f * f * (3.0 - 2.0 * f);\n" +
        "    return mix(\n" +
        "        mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),\n" +
        "            mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),\n" +
        "        mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),\n" +
        "            mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y),\n" +
        "        f.z);\n" +
        "}\n" +
        "\n" +
        "// FBM for clouds\n" +
        "float fbm(vec3 p) {\n" +
        "    float v = 0.0;\n" +
        "    float a = 0.5;\n" +
        "    for (int i = 0; i < 4; i++) {\n" +
        "        v += a * noise3D(p);\n" +
        "        p = p * 2.0 + vec3(1.7, 9.2, 3.1);\n" +
        "        a *= 0.5;\n" +
        "    }\n" +
        "    return v;\n" +
        "}\n" +
        "\n" +
        "// Cloud density at a world position\n" +
        "float cloudDensity(vec3 pos, float altitude) {\n" +
        "    if (u_cloudCoverage < 0.01) return 0.0;\n" +
        "    float cloudThickness = u_cloudTop - u_cloudBase;\n" +
        "    float heightFrac = (altitude - u_cloudBase) / cloudThickness;\n" +
        "    // Altitude ramp: peak at 0.3, fade at edges\n" +
        "    float altitudeRamp = smoothstep(0.0, 0.3, heightFrac) * smoothstep(1.0, 0.7, heightFrac);\n" +
        "    vec3 windOffset = vec3(u_windDirection.x, 0.0, u_windDirection.y) * u_time * 0.01;\n" +
        "    float n = fbm(pos * 0.003 + windOffset);\n" +
        "    // Domain warp\n" +
        "    float warp = fbm(pos * 0.001 + vec3(5.3, 1.7, 8.9) + windOffset * 0.5);\n" +
        "    n = fbm(pos * 0.003 + windOffset + warp * 0.5);\n" +
        "    float density = smoothstep(1.0 - u_cloudCoverage, 1.0, n) * altitudeRamp;\n" +
        "    return density * 0.4;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec3 ray = normalize(v_rayDir);\n" +
        "    \n" +
        "    // Camera position on the planet surface (at planetRadius)\n" +
        "    vec3 origin = vec3(0.0, u_planetRadius + u_cameraPos.y, 0.0);\n" +
        "    \n" +
        "    // Intersect with atmosphere\n" +
        "    vec2 atmoHit = raySphere(origin, ray, u_atmosphereRadius);\n" +
        "    if (atmoHit.y < 0.0) {\n" +
        "        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "        return;\n" +
        "    }\n" +
        "    \n" +
        "    // Check if ray hits planet surface\n" +
        "    vec2 planetHit = raySphere(origin, ray, u_planetRadius);\n" +
        "    float maxDist = (planetHit.x > 0.0) ? planetHit.x : atmoHit.y;\n" +
        "    float startDist = max(0.0, atmoHit.x);\n" +
        "    \n" +
        "    // March through atmosphere\n" +
        "    float stepSize = (maxDist - startDist) / float(PRIMARY_STEPS);\n" +
        "    float cosTheta = dot(ray, u_sunDirection);\n" +
        "    float rPhase = rayleighPhase(cosTheta);\n" +
        "    float mPhase = miePhase(cosTheta, u_mieG);\n" +
        "    \n" +
        "    vec3 totalRayleigh = vec3(0.0);\n" +
        "    vec3 totalMie = vec3(0.0);\n" +
        "    float opticalDepthR = 0.0;\n" +
        "    float opticalDepthM = 0.0;\n" +
        "    \n" +
        "    // Cloud accumulation\n" +
        "    float cloudTransmittance = 1.0;\n" +
        "    vec3 cloudColor = vec3(0.0);\n" +
        "    \n" +
        "    for (int i = 0; i < PRIMARY_STEPS; i++) {\n" +
        "        float dist = startDist + stepSize * (float(i) + 0.5);\n" +
        "        vec3 pos = origin + ray * dist;\n" +
        "        float altitude = length(pos) - u_planetRadius;\n" +
        "        \n" +
        "        // Atmospheric density at this altitude\n" +
        "        float densityR = exp(-altitude / u_scaleHeightRayleigh) * stepSize;\n" +
        "        float densityM = exp(-altitude / u_scaleHeightMie) * stepSize;\n" +
        "        opticalDepthR += densityR;\n" +
        "        opticalDepthM += densityM;\n" +
        "        \n" +
        "        // Light march toward sun\n" +
        "        vec2 sunHit = raySphere(pos, u_sunDirection, u_atmosphereRadius);\n" +
        "        float sunStepSize = sunHit.y / float(LIGHT_STEPS);\n" +
        "        float sunOptR = 0.0;\n" +
        "        float sunOptM = 0.0;\n" +
        "        bool occluded = false;\n" +
        "        \n" +
        "        for (int j = 0; j < LIGHT_STEPS; j++) {\n" +
        "            vec3 sunPos = pos + u_sunDirection * sunStepSize * (float(j) + 0.5);\n" +
        "            float sunAlt = length(sunPos) - u_planetRadius;\n" +
        "            if (sunAlt < 0.0) { occluded = true; break; }\n" +
        "            sunOptR += exp(-sunAlt / u_scaleHeightRayleigh) * sunStepSize;\n" +
        "            sunOptM += exp(-sunAlt / u_scaleHeightMie) * sunStepSize;\n" +
        "        }\n" +
        "        \n" +
        "        if (!occluded) {\n" +
        "            vec3 tau = u_rayleighCoeff * (opticalDepthR + sunOptR) +\n" +
        "                       vec3(u_mieCoeff) * 1.1 * (opticalDepthM + sunOptM) +\n" +
        "                       u_absorptionCoeff * (opticalDepthR + sunOptR);\n" +
        "            vec3 attenuation = exp(-tau);\n" +
        "            totalRayleigh += densityR * attenuation;\n" +
        "            totalMie += densityM * attenuation;\n" +
        "        }\n" +
        "        \n" +
        "        // Cloud sampling\n" +
        "        if (altitude > u_cloudBase && altitude < u_cloudTop && cloudTransmittance > 0.01) {\n" +
        "            float cd = cloudDensity(pos, altitude);\n" +
        "            if (cd > 0.0) {\n" +
        "                // Beer-powder approximation for cloud lighting\n" +
        "                float beer = exp(-cd * stepSize * 8.0);\n" +
        "                float powder = 1.0 - exp(-cd * stepSize * 16.0);\n" +
        "                float lightEnergy = 2.0 * beer * powder;\n" +
        "                \n" +
        "                // Sun color reaching this cloud point\n" +
        "                vec3 sunTau = u_rayleighCoeff * opticalDepthR + vec3(u_mieCoeff) * opticalDepthM;\n" +
        "                vec3 sunAtten = exp(-sunTau);\n" +
        "                vec3 cloudLit = sunAtten * u_sunIntensity * lightEnergy;\n" +
        "                \n" +
        "                // Ambient from sky\n" +
        "                cloudLit += vec3(0.05, 0.07, 0.1);\n" +
        "                \n" +
        "                cloudColor += cloudTransmittance * cloudLit * cd * stepSize;\n" +
        "                cloudTransmittance *= exp(-cd * stepSize * 8.0);\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    \n" +
        "    // Combine scattering\n" +
        "    vec3 sky = u_sunIntensity * (rPhase * u_rayleighCoeff * totalRayleigh +\n" +
        "                                  mPhase * vec3(u_mieCoeff) * totalMie);\n" +
        "    \n" +
        "    // Sun disc\n" +
        "    float sunCos = cos(u_sunAngularRadius);\n" +
        "    if (cosTheta > sunCos) {\n" +
        "        float edge = smoothstep(sunCos, sunCos + 0.0005, cosTheta);\n" +
        "        vec3 sunTau = u_rayleighCoeff * opticalDepthR + vec3(u_mieCoeff) * opticalDepthM;\n" +
        "        sky += exp(-sunTau) * u_sunIntensity * 4.0 * edge;\n" +
        "    }\n" +
        "    \n" +
        "    // Composite clouds over sky\n" +
        "    vec3 color = sky * cloudTransmittance + cloudColor;\n" +
        "    \n" +
        "    // Tone mapping (simple Reinhard)\n" +
        "    color = color / (1.0 + color);\n" +
        "    \n" +
        "    // Gamma correction\n" +
        "    color = pow(color, vec3(1.0 / 2.2));\n" +
        "    \n" +
        "    gl_FragColor = vec4(color, 1.0);\n" +
        "}\n";

    private final Mesh quad;
    private final ShaderProgram shader;
    private final Matrix4 invViewProj = new Matrix4();

    private ScatteringParams params;
    private final Vector3 sunDirection = new Vector3(0f, 1f, 0f);
    private float time;

    private final Vector3 cachedHorizonColor = new Vector3(0.6f, 0.55f, 0.45f);
    private float cachedFogDensity = 0.004f;
    private float cachedAmbientIntensity = 0.35f;

    public AtmosphericSkyRenderer() {
        quad = buildFullscreenQuad();
        shader = new ShaderProgram(VERT_SHADER, FRAG_SHADER);
        if (!shader.isCompiled()) {
            Gdx.app.error("AtmosphericSkyRenderer", "Shader compile error:\n" + shader.getLog());
        }
        params = ScatteringParams.earthLike();
    }

    public void setScatteringParams(ScatteringParams params) {
        this.params = params;
        this.cachedFogDensity = params.fogDensity;
    }

    public void setSunDirection(Vector3 dir) {
        sunDirection.set(dir);
    }

    public void setTime(float elapsedSeconds) {
        this.time = elapsedSeconds;
    }

    public void render(PerspectiveCamera camera) {
        invViewProj.set(camera.combined).inv();

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        shader.bind();

        // Camera
        shader.setUniformMatrix("u_invViewProj", invViewProj);
        shader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);

        // Atmosphere geometry
        shader.setUniformf("u_planetRadius", params.planetRadius);
        shader.setUniformf("u_atmosphereRadius", params.atmosphereRadius);

        // Scattering coefficients
        shader.setUniformf("u_rayleighCoeff",
            params.rayleighCoeffR, params.rayleighCoeffG, params.rayleighCoeffB);
        shader.setUniformf("u_mieCoeff", params.mieCoeff);
        shader.setUniformf("u_mieG", params.mieG);
        shader.setUniformf("u_absorptionCoeff",
            params.absorptionCoeffR, params.absorptionCoeffG, params.absorptionCoeffB);
        shader.setUniformf("u_scaleHeightRayleigh", params.scaleHeightRayleigh);
        shader.setUniformf("u_scaleHeightMie", params.scaleHeightMie);

        // Sun
        shader.setUniformf("u_sunDirection", sunDirection.x, sunDirection.y, sunDirection.z);
        shader.setUniformf("u_sunIntensity", params.sunIntensity);
        shader.setUniformf("u_sunAngularRadius", params.sunAngularRadius);

        // Clouds
        shader.setUniformf("u_cloudBase", params.cloudBase);
        shader.setUniformf("u_cloudTop", params.cloudTop);
        shader.setUniformf("u_cloudCoverage", params.cloudCoverage);
        shader.setUniformf("u_time", time);
        shader.setUniformf("u_windDirection", 1f, 0.3f);

        quad.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        updateCachedFogValues(camera);
    }

    public Vector3 getHorizonColor() {
        return cachedHorizonColor;
    }

    public float getFogDensity() {
        return cachedFogDensity;
    }

    public Vector3 getSunDirection() {
        return sunDirection;
    }

    public float getAmbientIntensity() {
        return cachedAmbientIntensity;
    }

    @Override
    public void dispose() {
        quad.dispose();
        shader.dispose();
    }

    private void updateCachedFogValues(PerspectiveCamera camera) {
        // Approximate horizon color from scattering at sun altitude
        float sunAlt = sunDirection.y;
        float dayFactor = Math.max(0f, Math.min(1f, (sunAlt + 0.1f) / 0.3f));

        // Horizon color transitions with sun position
        float rR = params.rayleighCoeffR;
        float rG = params.rayleighCoeffG;
        float rB = params.rayleighCoeffB;
        float totalCoeff = rR + rG + rB + 0.0001f;

        // At noon: sky-colored horizon. At sunset: warm. At night: dark.
        float noonR = 0.5f + (1f - rR / totalCoeff) * 0.3f;
        float noonG = 0.55f + (1f - rG / totalCoeff) * 0.2f;
        float noonB = 0.6f + (rB / totalCoeff) * 0.3f;

        float sunsetR = 0.85f;
        float sunsetG = 0.45f;
        float sunsetB = 0.25f;

        float nightR = 0.02f;
        float nightG = 0.02f;
        float nightB = 0.04f;

        float sunsetFactor = (float) Math.exp(-sunAlt * sunAlt * 50f);

        if (dayFactor > 0.01f) {
            float r = noonR * (1f - sunsetFactor) + sunsetR * sunsetFactor;
            float g = noonG * (1f - sunsetFactor) + sunsetG * sunsetFactor;
            float b = noonB * (1f - sunsetFactor) + sunsetB * sunsetFactor;
            cachedHorizonColor.set(
                r * dayFactor + nightR * (1f - dayFactor),
                g * dayFactor + nightG * (1f - dayFactor),
                b * dayFactor + nightB * (1f - dayFactor));
        } else {
            cachedHorizonColor.set(nightR, nightG, nightB);
        }

        cachedFogDensity = params.fogDensity;
        cachedAmbientIntensity = 0.06f + 0.29f * dayFactor;
    }

    private static Mesh buildFullscreenQuad() {
        Mesh mesh = new Mesh(true, 4, 6,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
        mesh.setVertices(new float[]{-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f});
        mesh.setIndices(new short[]{0, 1, 2, 0, 2, 3});
        return mesh;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava --info`
Expected: Compiles successfully with no errors.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/AtmosphericSkyRenderer.java
git commit -m "feat(ui): add AtmosphericSkyRenderer with Rayleigh/Mie scattering and volumetric clouds"
```

---

### Task 4: GameScreen Integration + FogShaderProvider Dynamic Fog

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java`

- [ ] **Step 1: Update FogShaderProvider to accept dynamic light direction and ambient**

In `core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java`, make `lightDir` and `ambientColor` settable so GameScreen can feed them from DayNightCycle:

Replace the field declarations:

```java
    final Vector3 lightDir = new Vector3(-0.4f, -0.8f, -0.3f);
    final float[] ambientColor = {0.3f, 0.3f, 0.35f, 1f};
```

With:

```java
    final Vector3 lightDir = new Vector3(-0.4f, -0.8f, -0.3f);
    final float[] ambientColor = {0.3f, 0.3f, 0.35f, 1f};

    public void setLightDir(Vector3 dir) {
        lightDir.set(dir);
    }

    public void setAmbientColor(float r, float g, float b) {
        ambientColor[0] = r;
        ambientColor[1] = g;
        ambientColor[2] = b;
    }
```

- [ ] **Step 2: Replace SkyRenderer with AtmosphericSkyRenderer in GameScreen fields**

In `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`, replace the field declarations.

Replace:

```java
    private static final float FOG_DENSITY = 0.004f;
    private float fogDensity = FOG_DENSITY;
    private final Vector3 horizonColor = new Vector3(0.6f, 0.55f, 0.45f);
    private final Vector3 sunDirection = new Vector3(-0.4f, -0.8f, -0.3f).nor();

    private SkyRenderer skyRenderer;
    private FogShaderProvider fogShaderProvider;
    private ModelBatch fogModelBatch;
```

With:

```java
    private AtmosphericSkyRenderer atmosphericSkyRenderer;
    private DayNightCycle dayNightCycle;
    private FogShaderProvider fogShaderProvider;
    private ModelBatch fogModelBatch;
    private float gameTime;
```

- [ ] **Step 3: Update initializeWorld() to create AtmosphericSkyRenderer and DayNightCycle**

Replace the last three lines of `initializeWorld()`:

```java
        skyRenderer = new SkyRenderer();
        fogShaderProvider = new FogShaderProvider();
        fogModelBatch = new ModelBatch(fogShaderProvider);
```

With:

```java
        atmosphericSkyRenderer = new AtmosphericSkyRenderer();
        dayNightCycle = new DayNightCycle(600f, 23.5f, false);
        fogShaderProvider = new FogShaderProvider();
        fogModelBatch = new ModelBatch(fogShaderProvider);
```

- [ ] **Step 4: Update render() to use the new sky renderer and dynamic fog**

Replace the entire `render()` method:

```java
    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.0f, 0.0f, 0.0f, 1f, true);

        if (!paused) {
            float clampedDelta = Math.min(delta, 1f / 30f);
            gameWorld.update(clampedDelta);
            dayNightCycle.update(clampedDelta);
            gameTime += clampedDelta;
        }

        atmosphericSkyRenderer.setSunDirection(dayNightCycle.getSunDirection());
        atmosphericSkyRenderer.setTime(gameTime);
        atmosphericSkyRenderer.render(camera);

        if (!paused) {
            WorldPopulator.updateAnimals(populatedWorld, delta,
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);
        }

        syncBoxTransforms();
        renderTerrain();
        renderBoxes();
        renderWorldObjects();
        renderShips();

        gameWorld.getCockpitHUDSystem().render(delta);
        gameWorld.getDebugHudSystem().render(delta);

        if (paused) {
            pauseStage.act(delta);
            pauseStage.draw();
        }
    }
```

- [ ] **Step 5: Update renderTerrain() to use dynamic fog and light from DayNightCycle**

Replace the `renderTerrain()` method:

```java
    private void renderTerrain() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);

        Vector3 fogCol = atmosphericSkyRenderer.getHorizonColor();
        float fogDens = atmosphericSkyRenderer.getFogDensity();
        Vector3 sunDir = dayNightCycle.getSunDirection();
        float ambientScale = dayNightCycle.getAmbientIntensity();

        ShaderProgram shader = getTerrainShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        Matrix4 modelMat = new Matrix4();
        shader.setUniformMatrix("u_worldTrans", modelMat);
        shader.setUniformf("u_lightDir", -sunDir.x, -sunDir.y, -sunDir.z);
        shader.setUniformf("u_ambientColor", ambientScale, ambientScale, ambientScale + 0.05f, 1f);
        shader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_fogDensity", fogDens);
        shader.setUniformf("u_fogColor", fogCol.x, fogCol.y, fogCol.z);

        terrainMesh.render(shader, GL20.GL_TRIANGLES);
    }
```

- [ ] **Step 6: Update renderBoxes() and renderWorldObjects() to use dynamic fog**

Replace `renderBoxes()`:

```java
    private void renderBoxes() {
        Vector3 fogCol = atmosphericSkyRenderer.getHorizonColor();
        float fogDens = atmosphericSkyRenderer.getFogDensity();
        Vector3 sunDir = dayNightCycle.getSunDirection();
        fogShaderProvider.setFogParams(fogDens, fogCol);
        fogShaderProvider.setLightDir(new Vector3(-sunDir.x, -sunDir.y, -sunDir.z));
        float amb = dayNightCycle.getAmbientIntensity();
        fogShaderProvider.setAmbientColor(amb, amb, amb + 0.05f);

        fogModelBatch.begin(camera);
        for (int i = 0; i < boxInstances.size; i++) {
            fogModelBatch.render(boxInstances.get(i), environment);
        }
        fogModelBatch.end();
    }
```

Replace the first two lines of `renderWorldObjects()`:

```java
    private void renderWorldObjects() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);

        Vector3 fogCol = atmosphericSkyRenderer.getHorizonColor();
        float fogDens = atmosphericSkyRenderer.getFogDensity();
        Vector3 sunDir = dayNightCycle.getSunDirection();
        fogShaderProvider.setFogParams(fogDens, fogCol);
        fogShaderProvider.setLightDir(new Vector3(-sunDir.x, -sunDir.y, -sunDir.z));
        float amb = dayNightCycle.getAmbientIntensity();
        fogShaderProvider.setAmbientColor(amb, amb, amb + 0.05f);

        fogModelBatch.begin(camera);
```

The rest of `renderWorldObjects()` (tree/rock/grass/animal/water rendering) stays unchanged.

- [ ] **Step 7: Update renderShips() to use dynamic fog and light**

In the `renderShips()` method, replace the hardcoded fog/light uniforms:

Replace:

```java
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);
        shader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_fogDensity", fogDensity);
        shader.setUniformf("u_fogColor", horizonColor.x, horizonColor.y, horizonColor.z);
```

With:

```java
        Vector3 sunDir = dayNightCycle.getSunDirection();
        shader.setUniformf("u_lightDir", -sunDir.x, -sunDir.y, -sunDir.z);
        float amb = dayNightCycle.getAmbientIntensity();
        shader.setUniformf("u_ambientColor", amb, amb, amb + 0.05f, 1f);
        shader.setUniformf("u_cameraPos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_fogDensity", atmosphericSkyRenderer.getFogDensity());
        Vector3 fogCol = atmosphericSkyRenderer.getHorizonColor();
        shader.setUniformf("u_fogColor", fogCol.x, fogCol.y, fogCol.z);
```

- [ ] **Step 8: Update dispose() to clean up new objects**

Replace:

```java
        if (skyRenderer != null) {
            skyRenderer.dispose();
            skyRenderer = null;
        }
```

With:

```java
        if (atmosphericSkyRenderer != null) {
            atmosphericSkyRenderer.dispose();
            atmosphericSkyRenderer = null;
        }
```

Also remove the `shipShader` reference to `horizonColor` if present — all fog color references now go through `atmosphericSkyRenderer.getHorizonColor()`.

- [ ] **Step 9: Remove unused imports**

Remove the `SkyRenderer` import from GameScreen since it's no longer used:

```java
// Remove this line:
// import com.galacticodyssey.ui.SkyRenderer;
```

`SkyRenderer` already lives in the same package, so there may not be an explicit import — but verify no compile errors from the removed field references (`fogDensity`, `horizonColor`, `sunDirection` fields).

- [ ] **Step 10: Verify compilation**

Run: `./gradlew :core:compileJava --info`
Expected: Compiles successfully with no errors.

- [ ] **Step 11: Run existing tests to verify no regressions**

Run: `./gradlew :core:test --info`
Expected: All existing tests pass (ScatteringParamsTest, DayNightCycleTest, WorldPopulatorColorTest, all galaxy tests, etc.)

- [ ] **Step 12: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java \
        core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java
git commit -m "feat(ui): integrate AtmosphericSkyRenderer with dynamic fog and day/night cycle

Replaces static SkyRenderer with physically-based atmospheric scattering.
Fog color now derived from sky scattering each frame, fixing the horizon
bleed bug. Terrain, ships, and world objects all use dynamic lighting
from the day/night cycle."
```

---

### Task 5: Visual Verification

**Files:** None (runtime testing)

- [ ] **Step 1: Launch the game**

Run: `./gradlew :desktop:run`

Verify:
1. Sky shows a blue gradient at noon (not the old tan/warm gradient)
2. Sun disc is visible with a physically-correct corona glow
3. Clouds are visible as volumetric shapes in the cloud layer, above the terrain
4. Fog at the horizon matches the sky color — no visible seam between terrain and sky
5. Mountains do NOT have sky/clouds bleeding through below them

- [ ] **Step 2: Observe day/night transition**

Wait for the day/night cycle to progress (default 600 second = 10 minute day). Or temporarily shorten it by changing the `DayNightCycle` constructor in `initializeWorld()` to use `60f` seconds for testing. Verify:
1. Sunrise/sunset produces warm orange/red sky colors
2. Night sky is dark (near black)
3. Fog color transitions smoothly — terrain fades to dark at night, warm at sunset
4. Objects (trees, rocks, ships) are dimly lit at night, fully lit at noon

- [ ] **Step 3: Restore default day length if changed for testing**

If you changed the day length in Step 2, restore it to `600f`. Commit if any tuning changes were made.

- [ ] **Step 4: Final commit (if tuning needed)**

```
git add -A
git commit -m "fix(ui): tune atmospheric scattering parameters for visual quality"
```

---

### Task 6: Full Test Suite Verification

- [ ] **Step 1: Run all tests**

Run: `./gradlew :core:test --info`
Expected: ALL tests pass — no regressions from the atmospheric rendering changes.

- [ ] **Step 2: Verify file structure**

Confirm these files exist:
```
core/src/main/java/com/galacticodyssey/planet/ScatteringParams.java
core/src/main/java/com/galacticodyssey/ui/AtmosphericSkyRenderer.java
core/src/main/java/com/galacticodyssey/ui/DayNightCycle.java
core/src/test/java/com/galacticodyssey/planet/ScatteringParamsTest.java
core/src/test/java/com/galacticodyssey/ui/DayNightCycleTest.java
```

And these files were modified:
```
core/src/main/java/com/galacticodyssey/ui/GameScreen.java
core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java
```
