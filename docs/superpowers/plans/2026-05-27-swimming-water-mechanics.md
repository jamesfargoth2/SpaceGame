# Swimming & Water Mechanics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add player swimming, underwater exploration (surface to 1000m+), dynamic weather/storms, and ship-water interaction (deck wash, flooding, bailing) with a Sea of Thieves feel.

**Architecture:** Six new ECS systems layer onto the existing `WaveSystem`, `BuoyancySystem`, `FloodingSystem`, and `PlayerMovementSystem`. The `SwimmingSystem` takes over from `PlayerMovementSystem` when the player enters water. The `WeatherSystem` drives wave parameters. `DeckWashSystem` and `HatchFloodingSystem` add new water ingress sources to the existing `FloodingSystem`. All communication is event-driven via `EventBus`. All gameplay tuning values live in JSON data files.

**Tech Stack:** Java 17, libGDX 1.13+, Ashley ECS, gdx-bullet (Bullet physics), JUnit 5

**Design spec:** `docs/superpowers/specs/2026-05-27-swimming-water-mechanics-design.md`

---

## File Structure

### New files to create

**Enums** (`core/src/main/java/com/galacticodyssey/water/`):
- `SwimState.java` — DRY, WADING, SURFACE, DIVING, SUBMERGED, DROWNING
- `DepthZone.java` — SUNLIT, TWILIGHT, MIDNIGHT, ABYSSAL, HADAL
- `WeatherPhase.java` — CALM, BUILDING, STORM, SUBSIDING

**Components** (`core/src/main/java/com/galacticodyssey/water/`):
- `SwimmingStateComponent.java` — swim state, breath, depth, immersion
- `DepthZoneComponent.java` — current zone, pressure, visibility, fog
- `DiveGearComponent.java` — O2, pressure rating, light, speed modifier
- `WeatherStateComponent.java` — global weather entity marker, active storm cell tracking
- `StormCellComponent.java` — storm center, radius, phase, wind, drift
- `DeckWashComponent.java` — gunwale sample indices, deck height
- `HatchComponent.java` — hatches array with open/close state

**Systems** (`core/src/main/java/com/galacticodyssey/water/systems/`):
- `SwimmingSystem.java` — water detection, state machine, buoyancy/drag/propulsion
- `UnderwaterSystem.java` — depth zones, pressure damage, visibility
- `WeatherSystem.java` — storm state machine, wave param interpolation, storm cells
- `DeckWashSystem.java` — wave overtopping at gunwale points
- `HatchFloodingSystem.java` — water flow through open hatches
- `SwimCameraSystem.java` — camera Y tracking, roll, underwater effects

**Events** (`core/src/main/java/com/galacticodyssey/water/events/`):
- `PlayerEnteredWaterEvent.java`
- `PlayerExitedWaterEvent.java`
- `PlayerStartedDivingEvent.java`
- `PlayerSurfacedEvent.java`
- `BreathDepletedEvent.java`
- `PlayerDrowningEvent.java`
- `AscentSicknessEvent.java`
- `DepthZoneChangedEvent.java`
- `PressureDamageEvent.java`
- `StormApproachingEvent.java`
- `StormEnteredEvent.java`
- `StormExitedEvent.java`
- `StormPhaseChangedEvent.java`
- `StormIntensifiedEvent.java`
- `DeckWashEvent.java`
- `BilgeAlarmEvent.java`
- `DeckAwashEvent.java`
- `ShipSinkingEvent.java`
- `PlayerBailingEvent.java`
- `HullRepairEvent.java`

**Data models** (`core/src/main/java/com/galacticodyssey/water/data/`):
- `SwimConfigData.java` — swim speeds, drag, stamina, breath, buoyancy spring
- `DepthZoneData.java` — zone definition (depth range, visibility, fog color, pressure)
- `DepthZonesConfig.java` — wrapper holding array of DepthZoneData
- `DiveGearDefinition.java` — gear tier definition
- `WeatherProfileData.java` — wave profile for a weather phase
- `StormConfigData.java` — storm spawn rates, radius ranges, drift speeds
- `WaterDataRegistry.java` — loads and serves all water JSON data

**JSON data files** (`core/src/main/resources/data/water/`):
- `swim_config.json`
- `depth_zones.json`
- `dive_gear.json`
- `weather_profiles.json`
- `storm_config.json`

**Tests** (`core/src/test/java/com/galacticodyssey/water/`):
- `systems/SwimmingSystemTest.java`
- `systems/UnderwaterSystemTest.java`
- `systems/WeatherSystemTest.java`
- `systems/DeckWashSystemTest.java`
- `systems/HatchFloodingSystemTest.java`
- `data/WaterDataRegistryTest.java`

### Files to modify

- `core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java` — add swim-state guard
- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — register new systems + data registry

---

## Task 1: Core Enums

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/SwimState.java`
- Create: `core/src/main/java/com/galacticodyssey/water/DepthZone.java`
- Create: `core/src/main/java/com/galacticodyssey/water/WeatherPhase.java`

- [ ] **Step 1: Create SwimState enum**

```java
package com.galacticodyssey.water;

public enum SwimState {
    DRY,
    WADING,
    SURFACE,
    DIVING,
    SUBMERGED,
    DROWNING
}
```

- [ ] **Step 2: Create DepthZone enum**

```java
package com.galacticodyssey.water;

public enum DepthZone {
    SUNLIT,
    TWILIGHT,
    MIDNIGHT,
    ABYSSAL,
    HADAL
}
```

- [ ] **Step 3: Create WeatherPhase enum**

```java
package com.galacticodyssey.water;

public enum WeatherPhase {
    CALM,
    BUILDING,
    STORM,
    SUBSIDING
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/SwimState.java \
       core/src/main/java/com/galacticodyssey/water/DepthZone.java \
       core/src/main/java/com/galacticodyssey/water/WeatherPhase.java
git commit -m "feat(water): add SwimState, DepthZone, and WeatherPhase enums"
```

---

## Task 2: Swimming Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/events/PlayerEnteredWaterEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/PlayerExitedWaterEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/PlayerStartedDivingEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/PlayerSurfacedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/BreathDepletedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/PlayerDrowningEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/AscentSicknessEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/DepthZoneChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/PressureDamageEvent.java`

All events follow the existing pattern: final class, public final fields, single constructor.

- [ ] **Step 1: Create player water events**

```java
// PlayerEnteredWaterEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerEnteredWaterEvent {
    public final Entity player;
    public final Entity waterBody;

    public PlayerEnteredWaterEvent(Entity player, Entity waterBody) {
        this.player = player;
        this.waterBody = waterBody;
    }
}
```

```java
// PlayerExitedWaterEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerExitedWaterEvent {
    public final Entity player;

    public PlayerExitedWaterEvent(Entity player) {
        this.player = player;
    }
}
```

```java
// PlayerStartedDivingEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerStartedDivingEvent {
    public final Entity player;
    public final float depth;

    public PlayerStartedDivingEvent(Entity player, float depth) {
        this.player = player;
        this.depth = depth;
    }
}
```

```java
// PlayerSurfacedEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerSurfacedEvent {
    public final Entity player;

    public PlayerSurfacedEvent(Entity player) {
        this.player = player;
    }
}
```

```java
// BreathDepletedEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class BreathDepletedEvent {
    public final Entity player;

    public BreathDepletedEvent(Entity player) {
        this.player = player;
    }
}
```

```java
// PlayerDrowningEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerDrowningEvent {
    public final Entity player;

    public PlayerDrowningEvent(Entity player) {
        this.player = player;
    }
}
```

- [ ] **Step 2: Create underwater and depth events**

```java
// AscentSicknessEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class AscentSicknessEvent {
    public final Entity player;
    public final float ascentSpeed;
    public final float duration;

    public AscentSicknessEvent(Entity player, float ascentSpeed, float duration) {
        this.player = player;
        this.ascentSpeed = ascentSpeed;
        this.duration = duration;
    }
}
```

```java
// DepthZoneChangedEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.water.DepthZone;

public final class DepthZoneChangedEvent {
    public final Entity entity;
    public final DepthZone previousZone;
    public final DepthZone newZone;
    public final float depth;

    public DepthZoneChangedEvent(Entity entity, DepthZone previousZone,
                                  DepthZone newZone, float depth) {
        this.entity = entity;
        this.previousZone = previousZone;
        this.newZone = newZone;
        this.depth = depth;
    }
}
```

```java
// PressureDamageEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PressureDamageEvent {
    public final Entity entity;
    public final float damage;
    public final float ambientPressure;
    public final float gearMaxPressure;

    public PressureDamageEvent(Entity entity, float damage,
                                float ambientPressure, float gearMaxPressure) {
        this.entity = entity;
        this.damage = damage;
        this.ambientPressure = ambientPressure;
        this.gearMaxPressure = gearMaxPressure;
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/events/PlayerEnteredWaterEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/PlayerExitedWaterEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/PlayerStartedDivingEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/PlayerSurfacedEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/BreathDepletedEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/PlayerDrowningEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/AscentSicknessEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/DepthZoneChangedEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/PressureDamageEvent.java
git commit -m "feat(water): add swimming, diving, and underwater event classes"
```

---

## Task 3: Weather and Ship-Water Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/events/StormApproachingEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/StormEnteredEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/StormExitedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/StormPhaseChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/StormIntensifiedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/DeckWashEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/BilgeAlarmEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/DeckAwashEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/ShipSinkingEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/PlayerBailingEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/events/HullRepairEvent.java`

- [ ] **Step 1: Create weather events**

```java
// StormApproachingEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class StormApproachingEvent {
    public final Entity stormEntity;
    public final float distance;
    public final float bearing;

    public StormApproachingEvent(Entity stormEntity, float distance, float bearing) {
        this.stormEntity = stormEntity;
        this.distance = distance;
        this.bearing = bearing;
    }
}
```

```java
// StormEnteredEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class StormEnteredEvent {
    public final Entity stormEntity;
    public final float intensity;

    public StormEnteredEvent(Entity stormEntity, float intensity) {
        this.stormEntity = stormEntity;
        this.intensity = intensity;
    }
}
```

```java
// StormExitedEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class StormExitedEvent {
    public final Entity stormEntity;

    public StormExitedEvent(Entity stormEntity) {
        this.stormEntity = stormEntity;
    }
}
```

```java
// StormPhaseChangedEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.water.WeatherPhase;

public final class StormPhaseChangedEvent {
    public final Entity stormEntity;
    public final WeatherPhase oldPhase;
    public final WeatherPhase newPhase;

    public StormPhaseChangedEvent(Entity stormEntity, WeatherPhase oldPhase,
                                   WeatherPhase newPhase) {
        this.stormEntity = stormEntity;
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
    }
}
```

```java
// StormIntensifiedEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class StormIntensifiedEvent {
    public final Entity stormEntity;
    public final float newIntensity;

    public StormIntensifiedEvent(Entity stormEntity, float newIntensity) {
        this.stormEntity = stormEntity;
        this.newIntensity = newIntensity;
    }
}
```

- [ ] **Step 2: Create ship-water events**

```java
// DeckWashEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class DeckWashEvent {
    public final Entity shipEntity;
    public final float flowRate;

    public DeckWashEvent(Entity shipEntity, float flowRate) {
        this.shipEntity = shipEntity;
        this.flowRate = flowRate;
    }
}
```

```java
// BilgeAlarmEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class BilgeAlarmEvent {
    public final Entity shipEntity;
    public final String compartmentId;
    public final float fillFraction;

    public BilgeAlarmEvent(Entity shipEntity, String compartmentId, float fillFraction) {
        this.shipEntity = shipEntity;
        this.compartmentId = compartmentId;
        this.fillFraction = fillFraction;
    }
}
```

```java
// DeckAwashEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class DeckAwashEvent {
    public final Entity shipEntity;

    public DeckAwashEvent(Entity shipEntity) {
        this.shipEntity = shipEntity;
    }
}
```

```java
// ShipSinkingEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class ShipSinkingEvent {
    public final Entity shipEntity;
    public final float totalFloodedMass;

    public ShipSinkingEvent(Entity shipEntity, float totalFloodedMass) {
        this.shipEntity = shipEntity;
        this.totalFloodedMass = totalFloodedMass;
    }
}
```

```java
// PlayerBailingEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class PlayerBailingEvent {
    public final Entity player;
    public final String compartmentId;
    public final float volumeRemoved;

    public PlayerBailingEvent(Entity player, String compartmentId, float volumeRemoved) {
        this.player = player;
        this.compartmentId = compartmentId;
        this.volumeRemoved = volumeRemoved;
    }
}
```

```java
// HullRepairEvent.java
package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public final class HullRepairEvent {
    public final Entity player;
    public final Entity shipEntity;
    public final String compartmentId;

    public HullRepairEvent(Entity player, Entity shipEntity, String compartmentId) {
        this.player = player;
        this.shipEntity = shipEntity;
        this.compartmentId = compartmentId;
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/events/StormApproachingEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/StormEnteredEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/StormExitedEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/StormPhaseChangedEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/StormIntensifiedEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/DeckWashEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/BilgeAlarmEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/DeckAwashEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/ShipSinkingEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/PlayerBailingEvent.java \
       core/src/main/java/com/galacticodyssey/water/events/HullRepairEvent.java
git commit -m "feat(water): add weather and ship-water interaction event classes"
```

---

## Task 4: Data Models and JSON Files

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/data/SwimConfigData.java`
- Create: `core/src/main/java/com/galacticodyssey/water/data/DepthZoneData.java`
- Create: `core/src/main/java/com/galacticodyssey/water/data/DepthZonesConfig.java`
- Create: `core/src/main/java/com/galacticodyssey/water/data/DiveGearDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/water/data/WeatherProfileData.java`
- Create: `core/src/main/java/com/galacticodyssey/water/data/StormConfigData.java`
- Create: `core/src/main/java/com/galacticodyssey/water/data/WaterDataRegistry.java`
- Create: `core/src/main/resources/data/water/swim_config.json`
- Create: `core/src/main/resources/data/water/depth_zones.json`
- Create: `core/src/main/resources/data/water/dive_gear.json`
- Create: `core/src/main/resources/data/water/weather_profiles.json`
- Create: `core/src/main/resources/data/water/storm_config.json`
- Test: `core/src/test/java/com/galacticodyssey/water/data/WaterDataRegistryTest.java`

- [ ] **Step 1: Create data model POJOs**

All data models use public fields for libGDX `Json.readValue()` reflection deserialization — matching the existing pattern in `AmmoTypeData`, `CombatDataRegistry`, etc.

```java
// SwimConfigData.java
package com.galacticodyssey.water.data;

public class SwimConfigData {
    public float surfaceSwimSpeed = 2.0f;
    public float diveSwimSpeed = 2.5f;
    public float sprintSwimMultiplier = 1.6f;
    public float wadingSpeedMin = 0.4f;
    public float playerDragCoefficient = 1.2f;
    public float playerCrossSectionArea = 0.7f;
    public float buoyancySpringK = 50.0f;
    public float buoyancyDamping = 8.0f;
    public float surfaceSwimForce = 40.0f;
    public float diveSwimForce = 50.0f;

    public float surfaceStaminaDrain = 2.0f;
    public float sprintSwimStaminaDrain = 5.0f;
    public float wadingStaminaDrain = 0.5f;
    public float stormStaminaDrainMultiplier = 2.0f;

    public float maxBreath = 30.0f;
    public float breathDrainRate = 1.0f;
    public float sprintBreathDrainRate = 1.5f;
    public float breathRefillRate = 3.0f;

    public float drowningDamageRate = 10.0f;
    public float drowningFloatSpeed = 1.5f;

    public float wadeDepthFoot = 0.2f;
    public float wadeDepthChest = 1.2f;
    public float surfaceEyeOffset = 0.3f;

    public float diveToSubmergedDepth = 5.0f;
    public float submergedToDiveDepth = 4.0f;

    public float ascentSicknessSpeedThreshold = 10.0f;
    public float ascentSicknessMinDepth = 30.0f;
    public float ascentSicknessMinDuration = 5.0f;
    public float ascentSicknessMaxDuration = 10.0f;

    public float windCurrentFactor = 0.03f;
}
```

```java
// DepthZoneData.java
package com.galacticodyssey.water.data;

public class DepthZoneData {
    public String id;
    public float minDepth;
    public float maxDepth;
    public float visibilityStart;
    public float visibilityEnd;
    public float fogColorR;
    public float fogColorG;
    public float fogColorB;
    public boolean requiresLight;
    public float pressureDamageRate;
    public String gearRequired;
}
```

```java
// DepthZonesConfig.java
package com.galacticodyssey.water.data;

public class DepthZonesConfig {
    public DepthZoneData[] zones;
    public float maxVisibilityDistance = 100.0f;
    public float maxRenderDepth = 1200.0f;
    public float maxLightDepth = 800.0f;
    public float noGearMaxPressure = 2.0f;
}
```

```java
// DiveGearDefinition.java
package com.galacticodyssey.water.data;

public class DiveGearDefinition {
    public String id;
    public String name;
    public float oxygenCapacity;
    public float maxPressure;
    public boolean providesLight;
    public float lightRadius;
    public float swimSpeedModifier = 1.0f;
    public String depthRating;
}
```

```java
// WeatherProfileData.java
package com.galacticodyssey.water.data;

public class WeatherProfileData {
    public String phase;
    public float minDuration;
    public float maxDuration;
    public int waveCount;
    public float minAmplitude;
    public float maxAmplitude;
    public float minSteepness;
    public float maxSteepness;
    public float directionSpread;
    public float minWindSpeed;
    public float maxWindSpeed;
    public float lerpRate;
    public float staminaDrainMultiplier = 1.0f;
}
```

```java
// StormConfigData.java
package com.galacticodyssey.water.data;

public class StormConfigData {
    public float minRadius = 1000f;
    public float maxRadius = 5000f;
    public float minDriftSpeed = 5f;
    public float maxDriftSpeed = 20f;
    public float spawnIntervalMin = 300f;
    public float spawnIntervalMax = 900f;
    public float intensificationChance = 0.1f;
    public float intensificationAmplitudeBoost = 0.3f;
    public float edgeBandFraction = 0.3f;
    public float approachWarningDistance = 500f;
}
```

- [ ] **Step 2: Create WaterDataRegistry**

```java
// WaterDataRegistry.java
package com.galacticodyssey.water.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

public class WaterDataRegistry {

    private SwimConfigData swimConfig;
    private DepthZonesConfig depthZonesConfig;
    private final Map<String, DiveGearDefinition> diveGear = new HashMap<>();
    private final Map<String, WeatherProfileData> weatherProfiles = new HashMap<>();
    private StormConfigData stormConfig;

    public void loadFromFiles() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();

        swimConfig = json.fromJson(SwimConfigData.class,
            Gdx.files.internal("data/water/swim_config.json"));

        depthZonesConfig = json.fromJson(DepthZonesConfig.class,
            Gdx.files.internal("data/water/depth_zones.json"));

        JsonValue gearRoot = reader.parse(Gdx.files.internal("data/water/dive_gear.json"));
        for (JsonValue entry = gearRoot.child; entry != null; entry = entry.next) {
            DiveGearDefinition def = json.readValue(DiveGearDefinition.class, entry);
            diveGear.put(def.id, def);
        }

        JsonValue profilesRoot = reader.parse(
            Gdx.files.internal("data/water/weather_profiles.json"));
        for (JsonValue entry = profilesRoot.child; entry != null; entry = entry.next) {
            WeatherProfileData profile = json.readValue(WeatherProfileData.class, entry);
            weatherProfiles.put(profile.phase, profile);
        }

        stormConfig = json.fromJson(StormConfigData.class,
            Gdx.files.internal("data/water/storm_config.json"));
    }

    public SwimConfigData getSwimConfig() { return swimConfig; }
    public DepthZonesConfig getDepthZonesConfig() { return depthZonesConfig; }
    public DiveGearDefinition getDiveGear(String id) { return diveGear.get(id); }
    public WeatherProfileData getWeatherProfile(String phase) { return weatherProfiles.get(phase); }
    public StormConfigData getStormConfig() { return stormConfig; }

    public void setSwimConfig(SwimConfigData config) { this.swimConfig = config; }
    public void setDepthZonesConfig(DepthZonesConfig config) { this.depthZonesConfig = config; }
    public void setStormConfig(StormConfigData config) { this.stormConfig = config; }
    public void registerDiveGear(DiveGearDefinition def) { diveGear.put(def.id, def); }
    public void registerWeatherProfile(WeatherProfileData profile) {
        weatherProfiles.put(profile.phase, profile);
    }
}
```

- [ ] **Step 3: Create JSON data files**

`core/src/main/resources/data/water/swim_config.json`:
```json
{
  "surfaceSwimSpeed": 2.0,
  "diveSwimSpeed": 2.5,
  "sprintSwimMultiplier": 1.6,
  "wadingSpeedMin": 0.4,
  "playerDragCoefficient": 1.2,
  "playerCrossSectionArea": 0.7,
  "buoyancySpringK": 50.0,
  "buoyancyDamping": 8.0,
  "surfaceSwimForce": 40.0,
  "diveSwimForce": 50.0,
  "surfaceStaminaDrain": 2.0,
  "sprintSwimStaminaDrain": 5.0,
  "wadingStaminaDrain": 0.5,
  "stormStaminaDrainMultiplier": 2.0,
  "maxBreath": 30.0,
  "breathDrainRate": 1.0,
  "sprintBreathDrainRate": 1.5,
  "breathRefillRate": 3.0,
  "drowningDamageRate": 10.0,
  "drowningFloatSpeed": 1.5,
  "wadeDepthFoot": 0.2,
  "wadeDepthChest": 1.2,
  "surfaceEyeOffset": 0.3,
  "diveToSubmergedDepth": 5.0,
  "submergedToDiveDepth": 4.0,
  "ascentSicknessSpeedThreshold": 10.0,
  "ascentSicknessMinDepth": 30.0,
  "ascentSicknessMinDuration": 5.0,
  "ascentSicknessMaxDuration": 10.0,
  "windCurrentFactor": 0.03
}
```

`core/src/main/resources/data/water/depth_zones.json`:
```json
{
  "maxVisibilityDistance": 100.0,
  "maxRenderDepth": 1200.0,
  "maxLightDepth": 800.0,
  "noGearMaxPressure": 2.0,
  "zones": [
    {"id":"SUNLIT","minDepth":0,"maxDepth":10,"visibilityStart":1.0,"visibilityEnd":0.6,"fogColorR":0.1,"fogColorG":0.4,"fogColorB":0.7,"requiresLight":false,"pressureDamageRate":0,"gearRequired":"none"},
    {"id":"TWILIGHT","minDepth":10,"maxDepth":50,"visibilityStart":0.6,"visibilityEnd":0.3,"fogColorR":0.05,"fogColorG":0.2,"fogColorB":0.5,"requiresLight":false,"pressureDamageRate":0.5,"gearRequired":"basic_rebreather"},
    {"id":"MIDNIGHT","minDepth":50,"maxDepth":200,"visibilityStart":0.3,"visibilityEnd":0.05,"fogColorR":0.02,"fogColorG":0.05,"fogColorB":0.15,"requiresLight":true,"pressureDamageRate":2.0,"gearRequired":"dive_suit"},
    {"id":"ABYSSAL","minDepth":200,"maxDepth":500,"visibilityStart":0.05,"visibilityEnd":0.01,"fogColorR":0.01,"fogColorG":0.02,"fogColorB":0.05,"requiresLight":true,"pressureDamageRate":5.0,"gearRequired":"pressure_suit"},
    {"id":"HADAL","minDepth":500,"maxDepth":99999,"visibilityStart":0.01,"visibilityEnd":0.0,"fogColorR":0.0,"fogColorG":0.0,"fogColorB":0.01,"requiresLight":true,"pressureDamageRate":10.0,"gearRequired":"exosuit"}
  ]
}
```

`core/src/main/resources/data/water/dive_gear.json`:
```json
[
  {"id":"basic_rebreather","name":"Basic Rebreather","oxygenCapacity":300,"maxPressure":6,"providesLight":false,"lightRadius":0,"swimSpeedModifier":1.0,"depthRating":"Twilight-rated (60m)"},
  {"id":"dive_suit","name":"Dive Suit with Lamp","oxygenCapacity":600,"maxPressure":20,"providesLight":true,"lightRadius":15,"swimSpeedModifier":0.9,"depthRating":"Midnight-rated (200m)"},
  {"id":"pressure_suit","name":"Pressure Suit","oxygenCapacity":1200,"maxPressure":50,"providesLight":true,"lightRadius":20,"swimSpeedModifier":0.8,"depthRating":"Abyssal-rated (500m)"},
  {"id":"exosuit","name":"Deep-Sea Exosuit","oxygenCapacity":1800,"maxPressure":120,"providesLight":true,"lightRadius":25,"swimSpeedModifier":0.7,"depthRating":"Hadal-rated (1200m)"}
]
```

`core/src/main/resources/data/water/weather_profiles.json`:
```json
[
  {"phase":"CALM","minDuration":60,"maxDuration":300,"waveCount":2,"minAmplitude":0.3,"maxAmplitude":0.8,"minSteepness":0.05,"maxSteepness":0.15,"directionSpread":15,"minWindSpeed":0,"maxWindSpeed":5,"lerpRate":0.5,"staminaDrainMultiplier":1.0},
  {"phase":"BUILDING","minDuration":30,"maxDuration":120,"waveCount":4,"minAmplitude":0.8,"maxAmplitude":3.0,"minSteepness":0.15,"maxSteepness":0.4,"directionSpread":30,"minWindSpeed":5,"maxWindSpeed":15,"lerpRate":0.3,"staminaDrainMultiplier":1.2},
  {"phase":"STORM","minDuration":60,"maxDuration":180,"waveCount":6,"minAmplitude":3.0,"maxAmplitude":6.0,"minSteepness":0.4,"maxSteepness":0.8,"directionSpread":60,"minWindSpeed":15,"maxWindSpeed":30,"lerpRate":0.2,"staminaDrainMultiplier":2.0},
  {"phase":"SUBSIDING","minDuration":60,"maxDuration":120,"waveCount":3,"minAmplitude":0.8,"maxAmplitude":2.0,"minSteepness":0.1,"maxSteepness":0.3,"directionSpread":25,"minWindSpeed":5,"maxWindSpeed":10,"lerpRate":0.4,"staminaDrainMultiplier":1.5}
]
```

`core/src/main/resources/data/water/storm_config.json`:
```json
{
  "minRadius": 1000,
  "maxRadius": 5000,
  "minDriftSpeed": 5,
  "maxDriftSpeed": 20,
  "spawnIntervalMin": 300,
  "spawnIntervalMax": 900,
  "intensificationChance": 0.1,
  "intensificationAmplitudeBoost": 0.3,
  "edgeBandFraction": 0.3,
  "approachWarningDistance": 500
}
```

- [ ] **Step 4: Write WaterDataRegistry test**

```java
// core/src/test/java/com/galacticodyssey/water/data/WaterDataRegistryTest.java
package com.galacticodyssey.water.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterDataRegistryTest {

    private WaterDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WaterDataRegistry();
    }

    @Test
    void programmaticSwimConfigRegistration() {
        SwimConfigData config = new SwimConfigData();
        config.surfaceSwimSpeed = 3.0f;
        config.maxBreath = 45f;

        registry.setSwimConfig(config);

        assertEquals(3.0f, registry.getSwimConfig().surfaceSwimSpeed, 0.001f);
        assertEquals(45f, registry.getSwimConfig().maxBreath, 0.001f);
    }

    @Test
    void programmaticDiveGearRegistration() {
        DiveGearDefinition gear = new DiveGearDefinition();
        gear.id = "test_rebreather";
        gear.name = "Test Rebreather";
        gear.oxygenCapacity = 500f;
        gear.maxPressure = 10f;
        gear.providesLight = false;
        gear.lightRadius = 0f;
        gear.swimSpeedModifier = 1.0f;

        registry.registerDiveGear(gear);

        DiveGearDefinition result = registry.getDiveGear("test_rebreather");
        assertNotNull(result);
        assertEquals(500f, result.oxygenCapacity, 0.001f);
        assertEquals(10f, result.maxPressure, 0.001f);
    }

    @Test
    void programmaticWeatherProfileRegistration() {
        WeatherProfileData profile = new WeatherProfileData();
        profile.phase = "STORM";
        profile.waveCount = 6;
        profile.maxAmplitude = 6.0f;

        registry.registerWeatherProfile(profile);

        WeatherProfileData result = registry.getWeatherProfile("STORM");
        assertNotNull(result);
        assertEquals(6, result.waveCount);
        assertEquals(6.0f, result.maxAmplitude, 0.001f);
    }

    @Test
    void programmaticDepthZonesRegistration() {
        DepthZonesConfig config = new DepthZonesConfig();
        config.maxVisibilityDistance = 150f;
        config.noGearMaxPressure = 2.0f;
        config.zones = new DepthZoneData[1];
        config.zones[0] = new DepthZoneData();
        config.zones[0].id = "SUNLIT";
        config.zones[0].minDepth = 0f;
        config.zones[0].maxDepth = 10f;

        registry.setDepthZonesConfig(config);

        assertEquals(150f, registry.getDepthZonesConfig().maxVisibilityDistance, 0.001f);
        assertEquals(1, registry.getDepthZonesConfig().zones.length);
        assertEquals("SUNLIT", registry.getDepthZonesConfig().zones[0].id);
    }

    @Test
    void unknownDiveGearReturnsNull() {
        assertNull(registry.getDiveGear("nonexistent"));
    }

    @Test
    void unknownWeatherProfileReturnsNull() {
        assertNull(registry.getWeatherProfile("TORNADO"));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.data.WaterDataRegistryTest" -i`
Expected: 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/data/ \
       core/src/main/resources/data/water/ \
       core/src/test/java/com/galacticodyssey/water/data/WaterDataRegistryTest.java
git commit -m "feat(water): add data models, JSON configs, and WaterDataRegistry"
```

---

## Task 5: SwimmingStateComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/SwimmingStateComponent.java`

- [ ] **Step 1: Create the component**

```java
package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;

public class SwimmingStateComponent implements Component {
    public SwimState swimState = SwimState.DRY;
    public SwimState previousState = SwimState.DRY;

    public float breath;
    public float maxBreath = 30f;

    public float currentDepth;
    public float immersionFraction;
    public float waterSurfaceHeight;

    public boolean isInInteriorWater;
    public float interiorWaterLevel;

    public float previousDepth;
    public float verticalSpeed;

    public float ascentSicknessTimer;
    public boolean hasAscentSickness;
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/SwimmingStateComponent.java
git commit -m "feat(water): add SwimmingStateComponent"
```

---

## Task 6: SwimmingSystem — Water Detection and State Machine

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/systems/SwimmingSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/water/systems/SwimmingSystemTest.java`

- [ ] **Step 1: Write failing tests for state transitions**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.water.SwimState;
import com.galacticodyssey.water.SwimmingStateComponent;
import com.galacticodyssey.water.data.SwimConfigData;
import com.galacticodyssey.water.data.WaterDataRegistry;
import com.galacticodyssey.water.events.PlayerEnteredWaterEvent;
import com.galacticodyssey.water.events.PlayerExitedWaterEvent;
import com.galacticodyssey.water.events.PlayerStartedDivingEvent;
import com.galacticodyssey.water.events.PlayerSurfacedEvent;
import com.galacticodyssey.water.events.BreathDepletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwimmingSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private SwimmingSystem swimmingSystem;
    private Entity player;
    private SwimmingStateComponent swimState;
    private PlayerInputComponent input;
    private MovementStateComponent movement;
    private TransformComponent transform;

    private final List<PlayerEnteredWaterEvent> enteredEvents = new ArrayList<>();
    private final List<PlayerExitedWaterEvent> exitedEvents = new ArrayList<>();
    private final List<PlayerStartedDivingEvent> diveEvents = new ArrayList<>();
    private final List<PlayerSurfacedEvent> surfacedEvents = new ArrayList<>();
    private final List<BreathDepletedEvent> breathEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        WaterDataRegistry registry = new WaterDataRegistry();
        registry.setSwimConfig(new SwimConfigData());

        swimmingSystem = new SwimmingSystem(15, eventBus, registry);
        engine = new Engine();
        engine.addSystem(swimmingSystem);

        player = new Entity();
        swimState = new SwimmingStateComponent();
        input = new PlayerInputComponent();
        movement = new MovementStateComponent();
        transform = new TransformComponent();

        PhysicsBodyComponent physics = new PhysicsBodyComponent();

        player.add(swimState);
        player.add(input);
        player.add(movement);
        player.add(transform);
        player.add(physics);
        engine.addEntity(player);

        eventBus.subscribe(PlayerEnteredWaterEvent.class, enteredEvents::add);
        eventBus.subscribe(PlayerExitedWaterEvent.class, exitedEvents::add);
        eventBus.subscribe(PlayerStartedDivingEvent.class, diveEvents::add);
        eventBus.subscribe(PlayerSurfacedEvent.class, surfacedEvents::add);
        eventBus.subscribe(BreathDepletedEvent.class, breathEvents::add);
    }

    @Test
    void startsInDryState() {
        assertEquals(SwimState.DRY, swimState.swimState);
    }

    @Test
    void transitionsDryToWadingWhenFeetInWater() {
        transform.position.set(0, 0.5f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.7f);
        movement.isGrounded = true;

        engine.update(1f / 60f);

        assertEquals(SwimState.WADING, swimState.swimState);
        assertEquals(1, enteredEvents.size());
    }

    @Test
    void transitionsWadingToDryWhenWaterRecedes() {
        swimState.swimState = SwimState.WADING;
        transform.position.set(0, 2.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(1.5f);
        movement.isGrounded = true;

        engine.update(1f / 60f);

        assertEquals(SwimState.DRY, swimState.swimState);
        assertEquals(1, exitedEvents.size());
    }

    @Test
    void transitionsWadingToSurfaceWhenDeep() {
        swimState.swimState = SwimState.WADING;
        transform.position.set(0, 0.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(1.5f);
        movement.isGrounded = false;

        engine.update(1f / 60f);

        assertEquals(SwimState.SURFACE, swimState.swimState);
    }

    @Test
    void transitionsSurfaceToDivingOnCrouchInput() {
        swimState.swimState = SwimState.SURFACE;
        swimState.breath = 30f;
        transform.position.set(0, 0.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.3f);
        input.crouch = true;

        engine.update(1f / 60f);

        assertEquals(SwimState.DIVING, swimState.swimState);
        assertEquals(1, diveEvents.size());
    }

    @Test
    void transitionsDivingToSubmergedAtDepthThreshold() {
        swimState.swimState = SwimState.DIVING;
        swimState.breath = 30f;
        transform.position.set(0, -5.5f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);

        engine.update(1f / 60f);

        assertEquals(SwimState.SUBMERGED, swimState.swimState);
    }

    @Test
    void transitionsSubmergedToDivingOnAscentAboveThreshold() {
        swimState.swimState = SwimState.SUBMERGED;
        swimState.breath = 30f;
        transform.position.set(0, -3.5f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);

        engine.update(1f / 60f);

        assertEquals(SwimState.DIVING, swimState.swimState);
    }

    @Test
    void transitionsDivingToSurfaceWhenNoDiveInput() {
        swimState.swimState = SwimState.DIVING;
        swimState.breath = 30f;
        swimState.currentDepth = 1.0f;
        transform.position.set(0, -0.3f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);
        input.crouch = false;

        engine.update(1f / 60f);

        assertEquals(SwimState.SURFACE, swimState.swimState);
        assertEquals(1, surfacedEvents.size());
    }

    @Test
    void breathDrainsWhileDiving() {
        swimState.swimState = SwimState.DIVING;
        swimState.breath = 10f;
        transform.position.set(0, -2.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);
        input.crouch = true;

        engine.update(1.0f);

        assertTrue(swimState.breath < 10f);
    }

    @Test
    void transitionsToDrowningWhenBreathDepleted() {
        swimState.swimState = SwimState.DIVING;
        swimState.breath = 0.5f;
        transform.position.set(0, -2.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);
        input.crouch = true;

        engine.update(1.0f);

        assertEquals(SwimState.DROWNING, swimState.swimState);
        assertEquals(1, breathEvents.size());
    }

    @Test
    void breathRefillsAtSurface() {
        swimState.swimState = SwimState.SURFACE;
        swimState.breath = 10f;
        swimState.maxBreath = 30f;
        transform.position.set(0, 0.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.3f);

        engine.update(1.0f);

        assertTrue(swimState.breath > 10f);
    }

    @Test
    void staysInDryStateWhenAboveWater() {
        transform.position.set(0, 5.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);
        movement.isGrounded = true;

        engine.update(1f / 60f);

        assertEquals(SwimState.DRY, swimState.swimState);
        assertTrue(enteredEvents.isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.SwimmingSystemTest" -i`
Expected: FAIL — SwimmingSystem class does not exist yet

- [ ] **Step 3: Implement SwimmingSystem**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.water.SwimState;
import com.galacticodyssey.water.SwimmingStateComponent;
import com.galacticodyssey.water.WaterBodyComponent;
import com.galacticodyssey.water.data.SwimConfigData;
import com.galacticodyssey.water.data.WaterDataRegistry;
import com.galacticodyssey.water.events.*;

public class SwimmingSystem extends IteratingSystem {

    private static final float CAPSULE_HALF_HEIGHT = 0.9f;

    private final ComponentMapper<SwimmingStateComponent> swimMapper =
        ComponentMapper.getFor(SwimmingStateComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<MovementStateComponent> moveMapper =
        ComponentMapper.getFor(MovementStateComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private final SwimConfigData config;

    private WaveSystem waveSystem;
    private float testWaterSurfaceHeight = Float.NaN;

    public SwimmingSystem(int priority, EventBus eventBus, WaterDataRegistry registry) {
        super(Family.all(
            SwimmingStateComponent.class,
            PlayerInputComponent.class,
            MovementStateComponent.class,
            TransformComponent.class,
            PhysicsBodyComponent.class
        ).get(), priority);
        this.eventBus = eventBus;
        this.config = registry.getSwimConfig();
    }

    public void setWaveSystem(WaveSystem waveSystem) {
        this.waveSystem = waveSystem;
    }

    public void setTestWaterSurfaceHeight(float height) {
        this.testWaterSurfaceHeight = height;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        SwimmingStateComponent swim = swimMapper.get(entity);
        PlayerInputComponent input = inputMapper.get(entity);
        MovementStateComponent movement = moveMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);

        float waterSurface = getWaterSurfaceHeight(transform.position, swim);
        swim.waterSurfaceHeight = waterSurface;

        float footY = transform.position.y - CAPSULE_HALF_HEIGHT;
        float waistY = transform.position.y;
        float chestY = transform.position.y + CAPSULE_HALF_HEIGHT * 0.3f;
        float headY = transform.position.y + CAPSULE_HALF_HEIGHT;

        float immersion = 0f;
        if (waterSurface > footY) {
            immersion = MathUtils.clamp(
                (waterSurface - footY) / (headY - footY), 0f, 1f);
        }
        swim.immersionFraction = immersion;

        swim.previousDepth = swim.currentDepth;
        swim.currentDepth = Math.max(0f, waterSurface - transform.position.y);
        swim.verticalSpeed = (swim.previousDepth - swim.currentDepth) / Math.max(dt, 0.001f);

        swim.previousState = swim.swimState;

        updateStateMachine(entity, swim, input, movement, transform, waterSurface,
            footY, chestY, headY, dt);

        updateBreath(swim, input, dt);
        updateStamina(swim, movement, input, dt);
        applySwimPhysics(entity, swim, input, transform, physics, waterSurface, dt);
    }

    private void updateStateMachine(Entity entity, SwimmingStateComponent swim,
            PlayerInputComponent input, MovementStateComponent movement,
            TransformComponent transform, float waterSurface,
            float footY, float chestY, float headY, float dt) {

        switch (swim.swimState) {
            case DRY:
                if (waterSurface > footY + config.wadeDepthFoot && movement.isGrounded) {
                    swim.swimState = SwimState.WADING;
                    eventBus.publish(new PlayerEnteredWaterEvent(entity, null));
                }
                break;

            case WADING:
                if (waterSurface < footY + config.wadeDepthFoot) {
                    swim.swimState = SwimState.DRY;
                    eventBus.publish(new PlayerExitedWaterEvent(entity));
                } else if (waterSurface > chestY || !movement.isGrounded) {
                    swim.swimState = SwimState.SURFACE;
                    swim.breath = swim.maxBreath;
                }
                break;

            case SURFACE:
                if (movement.isGrounded && waterSurface < chestY) {
                    swim.swimState = SwimState.WADING;
                } else if (input.crouch) {
                    swim.swimState = SwimState.DIVING;
                    eventBus.publish(new PlayerStartedDivingEvent(entity, swim.currentDepth));
                }
                if (swim.swimState == SwimState.SURFACE) {
                    swim.breath = Math.min(swim.maxBreath,
                        swim.breath + config.breathRefillRate * dt);
                }
                break;

            case DIVING:
                if (swim.currentDepth >= config.diveToSubmergedDepth) {
                    swim.swimState = SwimState.SUBMERGED;
                } else if (!input.crouch && swim.currentDepth < 0.5f) {
                    swim.swimState = SwimState.SURFACE;
                    eventBus.publish(new PlayerSurfacedEvent(entity));
                }
                if (swim.breath <= 0f) {
                    swim.breath = 0f;
                    swim.swimState = SwimState.DROWNING;
                    eventBus.publish(new BreathDepletedEvent(entity));
                    eventBus.publish(new PlayerDrowningEvent(entity));
                }
                break;

            case SUBMERGED:
                if (swim.currentDepth < config.submergedToDiveDepth) {
                    swim.swimState = SwimState.DIVING;
                }
                if (swim.currentDepth < 0.5f) {
                    swim.swimState = SwimState.SURFACE;
                    eventBus.publish(new PlayerSurfacedEvent(entity));
                }
                if (swim.breath <= 0f) {
                    swim.breath = 0f;
                    swim.swimState = SwimState.DROWNING;
                    eventBus.publish(new BreathDepletedEvent(entity));
                    eventBus.publish(new PlayerDrowningEvent(entity));
                }
                break;

            case DROWNING:
                if (swim.currentDepth < 0.5f) {
                    swim.swimState = SwimState.SURFACE;
                    eventBus.publish(new PlayerSurfacedEvent(entity));
                }
                break;
        }
    }

    private void updateBreath(SwimmingStateComponent swim, PlayerInputComponent input, float dt) {
        if (swim.swimState == SwimState.DIVING || swim.swimState == SwimState.SUBMERGED) {
            float drain = input.sprint ? config.sprintBreathDrainRate : config.breathDrainRate;
            swim.breath -= drain * dt;
        }
    }

    private void updateStamina(SwimmingStateComponent swim, MovementStateComponent movement,
                                PlayerInputComponent input, float dt) {
        switch (swim.swimState) {
            case WADING:
                movement.currentStamina -= config.wadingStaminaDrain * dt;
                break;
            case SURFACE:
                float surfaceDrain = input.sprint ?
                    config.sprintSwimStaminaDrain : config.surfaceStaminaDrain;
                movement.currentStamina -= surfaceDrain * dt;
                break;
            case DIVING:
            case SUBMERGED:
                float diveDrain = input.sprint ?
                    config.sprintSwimStaminaDrain : config.surfaceStaminaDrain;
                movement.currentStamina -= diveDrain * dt;
                break;
            default:
                break;
        }
        movement.currentStamina = MathUtils.clamp(
            movement.currentStamina, 0f, movement.maxStamina);
        movement.isExhausted = movement.currentStamina <= 0f;
    }

    private void applySwimPhysics(Entity entity, SwimmingStateComponent swim,
            PlayerInputComponent input, TransformComponent transform,
            PhysicsBodyComponent physics, float waterSurface, float dt) {
        if (physics.body == null) return;
        if (swim.swimState == SwimState.DRY) return;

        Vector3 vel = Pools.obtain(Vector3.class);
        physics.body.getLinearVelocity(vel);

        if (swim.swimState == SwimState.SURFACE) {
            float targetY = waterSurface + config.surfaceEyeOffset - CAPSULE_HALF_HEIGHT;
            float errorY = targetY - transform.position.y;
            float springForce = config.buoyancySpringK * errorY - config.buoyancyDamping * vel.y;

            Vector3 force = Pools.obtain(Vector3.class);
            force.set(0f, springForce * physics.mass, 0f);
            physics.body.applyCentralForce(force);
            Pools.free(force);
        }

        if (swim.swimState == SwimState.DROWNING) {
            Vector3 floatForce = Pools.obtain(Vector3.class);
            floatForce.set(0f, config.drowningFloatSpeed * physics.mass, 0f);
            physics.body.applyCentralForce(floatForce);
            Pools.free(floatForce);
        }

        if (swim.swimState != SwimState.DRY && swim.swimState != SwimState.DROWNING) {
            float speed = vel.len();
            if (speed > 0.01f) {
                float dragMag = 0.5f * 1025f * config.playerDragCoefficient
                    * config.playerCrossSectionArea * speed * speed;
                Vector3 drag = Pools.obtain(Vector3.class);
                drag.set(vel).nor().scl(-dragMag);
                physics.body.applyCentralForce(drag);
                Pools.free(drag);
            }
        }

        Pools.free(vel);
    }

    private float getWaterSurfaceHeight(Vector3 localPos, SwimmingStateComponent swim) {
        if (!Float.isNaN(testWaterSurfaceHeight)) {
            return testWaterSurfaceHeight;
        }
        if (swim.isInInteriorWater) {
            return swim.interiorWaterLevel;
        }
        if (waveSystem != null) {
            return waveSystem.getHeight(localPos.x, localPos.z);
        }
        return 0f;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.SwimmingSystemTest" -i`
Expected: All 11 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/systems/SwimmingSystem.java \
       core/src/test/java/com/galacticodyssey/water/systems/SwimmingSystemTest.java
git commit -m "feat(water): add SwimmingSystem with state machine, buoyancy, and drag"
```

---

## Task 7: PlayerMovementSystem Swim Guard

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java`

- [ ] **Step 1: Add swim state guard to processEntity**

Add to the existing guard block at the top of `processEntity()`, right after the piloting guard:

```java
// Add this import at the top of the file:
import com.galacticodyssey.water.SwimState;
import com.galacticodyssey.water.SwimmingStateComponent;

// Add this guard in processEntity, after the piloting check:
SwimmingStateComponent swimState = entity.getComponent(SwimmingStateComponent.class);
if (swimState != null && swimState.swimState != SwimState.DRY) {
    return;
}
```

This goes after the existing `PlayerStateComponent` piloting check and before the physics body null-check.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run existing player movement tests to verify no regression**

Run: `./gradlew core:test --tests "com.galacticodyssey.player.*" -i`
Expected: All existing tests PASS (no SwimmingStateComponent on test entities means guard is skipped)

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java
git commit -m "feat(player): add swim state guard to PlayerMovementSystem"
```

---

## Task 8: DepthZoneComponent and UnderwaterSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/DepthZoneComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/DiveGearComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/systems/UnderwaterSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/water/systems/UnderwaterSystemTest.java`

- [ ] **Step 1: Create DepthZoneComponent**

```java
package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;

public class DepthZoneComponent implements Component {
    public DepthZone currentZone = DepthZone.SUNLIT;
    public float currentDepth;
    public float ambientPressure = 1.0f;
    public float visibilityFraction = 1.0f;
    public final Color fogColor = new Color(0.1f, 0.4f, 0.7f, 1f);
    public boolean requiresLight;
}
```

- [ ] **Step 2: Create DiveGearComponent**

```java
package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;

public class DiveGearComponent implements Component {
    public String gearId;
    public float oxygenCapacity;
    public float oxygenRemaining;
    public float maxPressure;
    public boolean providesLight;
    public float lightRadius;
    public float swimSpeedModifier = 1.0f;
}
```

- [ ] **Step 3: Write failing tests for UnderwaterSystem**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.data.*;
import com.galacticodyssey.water.events.DepthZoneChangedEvent;
import com.galacticodyssey.water.events.PressureDamageEvent;
import com.galacticodyssey.water.events.AscentSicknessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnderwaterSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private SwimmingStateComponent swimState;
    private DepthZoneComponent depthZone;

    private final List<DepthZoneChangedEvent> zoneEvents = new ArrayList<>();
    private final List<PressureDamageEvent> pressureEvents = new ArrayList<>();
    private final List<AscentSicknessEvent> sicknessEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        WaterDataRegistry registry = new WaterDataRegistry();
        registry.setSwimConfig(new SwimConfigData());

        DepthZonesConfig dzConfig = new DepthZonesConfig();
        dzConfig.noGearMaxPressure = 2.0f;
        dzConfig.zones = new DepthZoneData[5];

        dzConfig.zones[0] = makeZone("SUNLIT", 0, 10, 0);
        dzConfig.zones[1] = makeZone("TWILIGHT", 10, 50, 0.5f);
        dzConfig.zones[2] = makeZone("MIDNIGHT", 50, 200, 2.0f);
        dzConfig.zones[3] = makeZone("ABYSSAL", 200, 500, 5.0f);
        dzConfig.zones[4] = makeZone("HADAL", 500, 99999, 10.0f);
        registry.setDepthZonesConfig(dzConfig);

        UnderwaterSystem system = new UnderwaterSystem(16, eventBus, registry);
        engine = new Engine();
        engine.addSystem(system);

        player = new Entity();
        swimState = new SwimmingStateComponent();
        depthZone = new DepthZoneComponent();
        player.add(swimState);
        player.add(depthZone);
        engine.addEntity(player);

        eventBus.subscribe(DepthZoneChangedEvent.class, zoneEvents::add);
        eventBus.subscribe(PressureDamageEvent.class, pressureEvents::add);
        eventBus.subscribe(AscentSicknessEvent.class, sicknessEvents::add);
    }

    private DepthZoneData makeZone(String id, float min, float max, float pressureDmg) {
        DepthZoneData z = new DepthZoneData();
        z.id = id;
        z.minDepth = min;
        z.maxDepth = max;
        z.pressureDamageRate = pressureDmg;
        z.visibilityStart = 1.0f;
        z.visibilityEnd = 0.5f;
        z.fogColorR = 0.1f;
        z.fogColorG = 0.3f;
        z.fogColorB = 0.6f;
        z.requiresLight = min >= 50;
        return z;
    }

    @Test
    void pressureCalculationAtKnownDepths() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 100f;

        engine.update(1f / 60f);

        assertEquals(11.0f, depthZone.ambientPressure, 0.01f);
    }

    @Test
    void sunlitZoneAtShallowDepth() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 5f;

        engine.update(1f / 60f);

        assertEquals(DepthZone.SUNLIT, depthZone.currentZone);
    }

    @Test
    void twilightZoneAtMidDepth() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 30f;
        depthZone.currentZone = DepthZone.SUNLIT;

        engine.update(1f / 60f);

        assertEquals(DepthZone.TWILIGHT, depthZone.currentZone);
        assertEquals(1, zoneEvents.size());
        assertEquals(DepthZone.SUNLIT, zoneEvents.get(0).previousZone);
        assertEquals(DepthZone.TWILIGHT, zoneEvents.get(0).newZone);
    }

    @Test
    void abyssalZoneAtGreatDepth() {
        swimState.swimState = SwimState.SUBMERGED;
        swimState.currentDepth = 300f;

        engine.update(1f / 60f);

        assertEquals(DepthZone.ABYSSAL, depthZone.currentZone);
    }

    @Test
    void noPressureDamageInSunlitWithNoGear() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 5f;

        engine.update(1.0f);

        assertTrue(pressureEvents.isEmpty());
    }

    @Test
    void pressureDamageWithoutGearBelowGraceDepth() {
        swimState.swimState = SwimState.SUBMERGED;
        swimState.currentDepth = 50f;

        engine.update(1.0f);

        assertFalse(pressureEvents.isEmpty());
        assertTrue(pressureEvents.get(0).damage > 0f);
    }

    @Test
    void noPressureDamageWithAdequateGear() {
        DiveGearComponent gear = new DiveGearComponent();
        gear.maxPressure = 20f;
        player.add(gear);

        swimState.swimState = SwimState.SUBMERGED;
        swimState.currentDepth = 50f;

        engine.update(1.0f);

        assertTrue(pressureEvents.isEmpty());
    }

    @Test
    void noZoneUpdateWhenDry() {
        swimState.swimState = SwimState.DRY;
        swimState.currentDepth = 0f;

        engine.update(1f / 60f);

        assertTrue(zoneEvents.isEmpty());
    }

    @Test
    void ascentSicknessOnFastRise() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 35f;
        swimState.verticalSpeed = 15f;

        engine.update(1f / 60f);

        assertEquals(1, sicknessEvents.size());
        assertTrue(sicknessEvents.get(0).duration >= 5.0f);
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.UnderwaterSystemTest" -i`
Expected: FAIL — UnderwaterSystem class does not exist

- [ ] **Step 5: Implement UnderwaterSystem**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.data.*;
import com.galacticodyssey.water.events.*;

public class UnderwaterSystem extends IteratingSystem {

    private final ComponentMapper<SwimmingStateComponent> swimMapper =
        ComponentMapper.getFor(SwimmingStateComponent.class);
    private final ComponentMapper<DepthZoneComponent> zoneMapper =
        ComponentMapper.getFor(DepthZoneComponent.class);
    private final ComponentMapper<DiveGearComponent> gearMapper =
        ComponentMapper.getFor(DiveGearComponent.class);

    private final EventBus eventBus;
    private final SwimConfigData swimConfig;
    private final DepthZonesConfig zonesConfig;

    public UnderwaterSystem(int priority, EventBus eventBus, WaterDataRegistry registry) {
        super(Family.all(
            SwimmingStateComponent.class,
            DepthZoneComponent.class
        ).get(), priority);
        this.eventBus = eventBus;
        this.swimConfig = registry.getSwimConfig();
        this.zonesConfig = registry.getDepthZonesConfig();
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        SwimmingStateComponent swim = swimMapper.get(entity);
        DepthZoneComponent zone = zoneMapper.get(entity);

        if (swim.swimState == SwimState.DRY) return;

        float depth = swim.currentDepth;
        zone.currentDepth = depth;
        zone.ambientPressure = 1.0f + depth / 10.0f;

        DepthZone previousZone = zone.currentZone;
        zone.currentZone = resolveZone(depth);

        if (zone.currentZone != previousZone) {
            eventBus.publish(new DepthZoneChangedEvent(
                entity, previousZone, zone.currentZone, depth));
        }

        updateVisibility(zone, depth);
        checkPressureDamage(entity, zone, dt);
        checkAscentSickness(entity, swim);
        updateDiveGearOxygen(entity, swim, dt);
    }

    private DepthZone resolveZone(float depth) {
        if (zonesConfig.zones == null) return DepthZone.SUNLIT;
        for (DepthZoneData zd : zonesConfig.zones) {
            if (depth >= zd.minDepth && depth < zd.maxDepth) {
                return DepthZone.valueOf(zd.id);
            }
        }
        return DepthZone.HADAL;
    }

    private void updateVisibility(DepthZoneComponent zone, float depth) {
        if (zonesConfig.zones == null) return;
        for (DepthZoneData zd : zonesConfig.zones) {
            if (depth >= zd.minDepth && depth < zd.maxDepth) {
                float t = (depth - zd.minDepth) / Math.max(zd.maxDepth - zd.minDepth, 1f);
                zone.visibilityFraction = MathUtils.lerp(zd.visibilityStart, zd.visibilityEnd, t);
                zone.fogColor.set(zd.fogColorR, zd.fogColorG, zd.fogColorB, 1f);
                zone.requiresLight = zd.requiresLight;
                return;
            }
        }
    }

    private void checkPressureDamage(Entity entity, DepthZoneComponent zone, float dt) {
        DiveGearComponent gear = gearMapper.has(entity) ? gearMapper.get(entity) : null;
        float gearMaxPressure = gear != null ? gear.maxPressure : zonesConfig.noGearMaxPressure;

        if (zone.ambientPressure > gearMaxPressure) {
            float excess = zone.ambientPressure - gearMaxPressure;
            DepthZoneData zoneData = findZoneData(zone.currentZone);
            float rate = zoneData != null ? zoneData.pressureDamageRate : 1.0f;
            float damage = rate * excess * dt;
            if (damage > 0f) {
                eventBus.publish(new PressureDamageEvent(
                    entity, damage, zone.ambientPressure, gearMaxPressure));
            }
        }
    }

    private void checkAscentSickness(Entity entity, SwimmingStateComponent swim) {
        if (swim.hasAscentSickness) return;
        if (swim.currentDepth < swimConfig.ascentSicknessMinDepth) return;
        if (swim.verticalSpeed < swimConfig.ascentSicknessSpeedThreshold) return;

        float speedRatio = swim.verticalSpeed / swimConfig.ascentSicknessSpeedThreshold;
        float duration = MathUtils.lerp(swimConfig.ascentSicknessMinDuration,
            swimConfig.ascentSicknessMaxDuration,
            MathUtils.clamp(speedRatio - 1f, 0f, 1f));

        swim.hasAscentSickness = true;
        swim.ascentSicknessTimer = duration;
        eventBus.publish(new AscentSicknessEvent(entity, swim.verticalSpeed, duration));
    }

    private void updateDiveGearOxygen(Entity entity, SwimmingStateComponent swim, float dt) {
        if (!gearMapper.has(entity)) return;
        DiveGearComponent gear = gearMapper.get(entity);

        if (swim.swimState == SwimState.DIVING || swim.swimState == SwimState.SUBMERGED) {
            gear.oxygenRemaining -= dt;
            if (gear.oxygenRemaining <= 0f) {
                gear.oxygenRemaining = 0f;
            }
        }
    }

    private DepthZoneData findZoneData(DepthZone zone) {
        if (zonesConfig.zones == null) return null;
        String name = zone.name();
        for (DepthZoneData zd : zonesConfig.zones) {
            if (name.equals(zd.id)) return zd;
        }
        return null;
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.UnderwaterSystemTest" -i`
Expected: All 9 tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/DepthZoneComponent.java \
       core/src/main/java/com/galacticodyssey/water/DiveGearComponent.java \
       core/src/main/java/com/galacticodyssey/water/systems/UnderwaterSystem.java \
       core/src/test/java/com/galacticodyssey/water/systems/UnderwaterSystemTest.java
git commit -m "feat(water): add UnderwaterSystem with depth zones, pressure, and ascent sickness"
```

---

## Task 9: WeatherStateComponent, StormCellComponent, and WeatherSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/WeatherStateComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/StormCellComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/systems/WeatherSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/water/systems/WeatherSystemTest.java`

- [ ] **Step 1: Create WeatherStateComponent**

```java
package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;

public class WeatherStateComponent implements Component {
    public WeatherPhase ambientPhase = WeatherPhase.CALM;
}
```

- [ ] **Step 2: Create StormCellComponent**

```java
package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;

public class StormCellComponent implements Component {
    public double centerGalaxyX;
    public double centerGalaxyZ;
    public float radius = 3000f;
    public WeatherPhase currentPhase = WeatherPhase.CALM;
    public float phaseTimer;
    public float phaseDuration;
    public float windDirection;
    public float windSpeed;
    public float driftVelocityX;
    public float driftVelocityZ;
    public float intensity = 1.0f;
    public boolean playerInside;
    public boolean playerApproaching;
}
```

- [ ] **Step 3: Write failing tests for WeatherSystem**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.data.*;
import com.galacticodyssey.water.events.StormApproachingEvent;
import com.galacticodyssey.water.events.StormEnteredEvent;
import com.galacticodyssey.water.events.StormExitedEvent;
import com.galacticodyssey.water.events.StormPhaseChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeatherSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private WeatherSystem weatherSystem;
    private Entity stormEntity;
    private StormCellComponent storm;
    private WaterBodyComponent waterBody;
    private Entity waterEntity;

    private final List<StormPhaseChangedEvent> phaseEvents = new ArrayList<>();
    private final List<StormApproachingEvent> approachEvents = new ArrayList<>();
    private final List<StormEnteredEvent> enteredEvents = new ArrayList<>();
    private final List<StormExitedEvent> exitedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        WaterDataRegistry registry = new WaterDataRegistry();

        StormConfigData stormConfig = new StormConfigData();
        stormConfig.edgeBandFraction = 0.3f;
        stormConfig.approachWarningDistance = 500f;
        registry.setStormConfig(stormConfig);

        for (WeatherPhase phase : WeatherPhase.values()) {
            WeatherProfileData profile = new WeatherProfileData();
            profile.phase = phase.name();
            profile.minDuration = 10f;
            profile.maxDuration = 20f;
            profile.waveCount = phase == WeatherPhase.STORM ? 6 : 2;
            profile.minAmplitude = phase == WeatherPhase.STORM ? 3f : 0.3f;
            profile.maxAmplitude = phase == WeatherPhase.STORM ? 6f : 0.8f;
            profile.minSteepness = 0.1f;
            profile.maxSteepness = phase == WeatherPhase.STORM ? 0.8f : 0.15f;
            profile.directionSpread = 15f;
            profile.minWindSpeed = 0f;
            profile.maxWindSpeed = phase == WeatherPhase.STORM ? 30f : 5f;
            profile.lerpRate = 0.5f;
            registry.registerWeatherProfile(profile);
        }

        weatherSystem = new WeatherSystem(5, eventBus, registry);
        engine = new Engine();
        engine.addSystem(weatherSystem);

        stormEntity = new Entity();
        storm = new StormCellComponent();
        storm.currentPhase = WeatherPhase.CALM;
        storm.phaseDuration = 10f;
        storm.phaseTimer = 0f;
        storm.radius = 3000f;
        storm.centerGalaxyX = 0;
        storm.centerGalaxyZ = 0;
        stormEntity.add(storm);
        engine.addEntity(stormEntity);

        waterEntity = new Entity();
        waterBody = new WaterBodyComponent();
        waterEntity.add(waterBody);
        engine.addEntity(waterEntity);

        weatherSystem.setActiveWaterBody(waterBody);

        eventBus.subscribe(StormPhaseChangedEvent.class, phaseEvents::add);
        eventBus.subscribe(StormApproachingEvent.class, approachEvents::add);
        eventBus.subscribe(StormEnteredEvent.class, enteredEvents::add);
        eventBus.subscribe(StormExitedEvent.class, exitedEvents::add);
    }

    @Test
    void stormStartsInCalmPhase() {
        assertEquals(WeatherPhase.CALM, storm.currentPhase);
    }

    @Test
    void phaseTransitionsAfterDurationExpires() {
        storm.phaseDuration = 1.0f;
        storm.phaseTimer = 0f;

        for (int i = 0; i < 70; i++) {
            engine.update(1f / 60f);
        }

        assertEquals(WeatherPhase.BUILDING, storm.currentPhase);
        assertEquals(1, phaseEvents.size());
        assertEquals(WeatherPhase.CALM, phaseEvents.get(0).oldPhase);
        assertEquals(WeatherPhase.BUILDING, phaseEvents.get(0).newPhase);
    }

    @Test
    void fullStormCycleReturnsToCalm() {
        storm.phaseDuration = 0.1f;

        for (int i = 0; i < 4; i++) {
            storm.phaseTimer = storm.phaseDuration + 0.01f;
            engine.update(1f / 60f);
        }

        assertEquals(WeatherPhase.CALM, storm.currentPhase);
        assertEquals(4, phaseEvents.size());
    }

    @Test
    void phaseTimerIncrementsEachTick() {
        float before = storm.phaseTimer;
        engine.update(0.5f);
        assertTrue(storm.phaseTimer > before);
    }

    @Test
    void stormCellDriftsWithWind() {
        storm.driftVelocityX = 10f;
        storm.driftVelocityZ = 5f;

        double startX = storm.centerGalaxyX;
        double startZ = storm.centerGalaxyZ;

        engine.update(1.0f);

        assertEquals(startX + 10.0, storm.centerGalaxyX, 0.1);
        assertEquals(startZ + 5.0, storm.centerGalaxyZ, 0.1);
    }

    @Test
    void publishesStormEnteredWhenPlayerMovesInside() {
        weatherSystem.setPlayerGalaxyPosition(0, 0);
        storm.centerGalaxyX = 0;
        storm.centerGalaxyZ = 0;
        storm.radius = 3000f;
        storm.playerInside = false;

        engine.update(1f / 60f);

        assertTrue(storm.playerInside);
        assertEquals(1, enteredEvents.size());
    }

    @Test
    void publishesStormExitedWhenPlayerLeavesRadius() {
        storm.playerInside = true;
        weatherSystem.setPlayerGalaxyPosition(10000, 10000);

        engine.update(1f / 60f);

        assertFalse(storm.playerInside);
        assertEquals(1, exitedEvents.size());
    }

    @Test
    void publishesStormApproachingAtWarningDistance() {
        storm.centerGalaxyX = 3400;
        storm.centerGalaxyZ = 0;
        storm.radius = 3000f;
        storm.playerInside = false;
        storm.playerApproaching = false;
        weatherSystem.setPlayerGalaxyPosition(0, 0);

        engine.update(1f / 60f);

        assertTrue(storm.playerApproaching);
        assertEquals(1, approachEvents.size());
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.WeatherSystemTest" -i`
Expected: FAIL — WeatherSystem class does not exist

- [ ] **Step 5: Implement WeatherSystem**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.data.*;
import com.galacticodyssey.water.events.StormApproachingEvent;
import com.galacticodyssey.water.events.StormEnteredEvent;
import com.galacticodyssey.water.events.StormExitedEvent;
import com.galacticodyssey.water.events.StormPhaseChangedEvent;

public class WeatherSystem extends IteratingSystem {

    private final ComponentMapper<StormCellComponent> stormMapper =
        ComponentMapper.getFor(StormCellComponent.class);

    private final EventBus eventBus;
    private final WaterDataRegistry registry;
    private final StormConfigData stormConfig;
    private WaterBodyComponent activeWaterBody;

    private double playerGalaxyX;
    private double playerGalaxyZ;

    public WeatherSystem(int priority, EventBus eventBus, WaterDataRegistry registry) {
        super(Family.all(StormCellComponent.class).get(), priority);
        this.eventBus = eventBus;
        this.registry = registry;
        this.stormConfig = registry.getStormConfig();
    }

    public void setActiveWaterBody(WaterBodyComponent waterBody) {
        this.activeWaterBody = waterBody;
    }

    public void setPlayerGalaxyPosition(double gx, double gz) {
        this.playerGalaxyX = gx;
        this.playerGalaxyZ = gz;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        StormCellComponent storm = stormMapper.get(entity);

        storm.phaseTimer += dt;

        storm.centerGalaxyX += storm.driftVelocityX * dt;
        storm.centerGalaxyZ += storm.driftVelocityZ * dt;

        if (storm.phaseTimer >= storm.phaseDuration) {
            advancePhase(entity, storm);
        }

        checkPlayerProximity(entity, storm);

        if (activeWaterBody != null) {
            lerpWaveParams(storm, dt);
        }
    }

    private void checkPlayerProximity(Entity entity, StormCellComponent storm) {
        double dx = playerGalaxyX - storm.centerGalaxyX;
        double dz = playerGalaxyZ - storm.centerGalaxyZ;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);

        boolean wasInside = storm.playerInside;
        boolean wasApproaching = storm.playerApproaching;

        storm.playerInside = distance < storm.radius;

        if (storm.playerInside && !wasInside) {
            eventBus.publish(new StormEnteredEvent(entity, storm.intensity));
        } else if (!storm.playerInside && wasInside) {
            eventBus.publish(new StormExitedEvent(entity));
        }

        float approachDist = storm.radius + stormConfig.approachWarningDistance;
        storm.playerApproaching = !storm.playerInside && distance < approachDist;

        if (storm.playerApproaching && !wasApproaching) {
            float bearing = (float) Math.toDegrees(Math.atan2(dz, dx));
            eventBus.publish(new StormApproachingEvent(entity, distance, bearing));
        }
    }

    private void advancePhase(Entity entity, StormCellComponent storm) {
        WeatherPhase oldPhase = storm.currentPhase;
        WeatherPhase newPhase;

        switch (storm.currentPhase) {
            case CALM:     newPhase = WeatherPhase.BUILDING; break;
            case BUILDING: newPhase = WeatherPhase.STORM;    break;
            case STORM:    newPhase = WeatherPhase.SUBSIDING; break;
            default:       newPhase = WeatherPhase.CALM;      break;
        }

        storm.currentPhase = newPhase;
        storm.phaseTimer = 0f;

        WeatherProfileData profile = registry.getWeatherProfile(newPhase.name());
        if (profile != null) {
            storm.phaseDuration = MathUtils.random(profile.minDuration, profile.maxDuration);
            storm.windSpeed = MathUtils.random(profile.minWindSpeed, profile.maxWindSpeed);
        }

        eventBus.publish(new StormPhaseChangedEvent(entity, oldPhase, newPhase));
    }

    private void lerpWaveParams(StormCellComponent storm, float dt) {
        WeatherProfileData profile = registry.getWeatherProfile(storm.currentPhase.name());
        if (profile == null) return;

        float rate = profile.lerpRate * dt;

        int currentCount = activeWaterBody.waves.size;
        int targetCount = profile.waveCount;

        while (currentCount < targetCount) {
            WaveParams wp = new WaveParams();
            wp.amplitude = 0f;
            wp.wavelength = MathUtils.random(20f, 80f);
            wp.speed = (float) Math.sqrt(9.81f * wp.wavelength / (2f * MathUtils.PI));
            wp.steepness = 0f;
            wp.directionDeg = storm.windDirection + MathUtils.random(
                -profile.directionSpread, profile.directionSpread);
            activeWaterBody.waves.add(wp);
            currentCount++;
        }

        float targetAmp = MathUtils.random(profile.minAmplitude, profile.maxAmplitude)
            * storm.intensity;
        float targetSteepness = MathUtils.random(profile.minSteepness, profile.maxSteepness);

        for (int i = 0; i < activeWaterBody.waves.size; i++) {
            WaveParams wp = activeWaterBody.waves.get(i);
            if (i < targetCount) {
                wp.amplitude = MathUtils.lerp(wp.amplitude, targetAmp, rate);
                wp.steepness = MathUtils.lerp(wp.steepness, targetSteepness, rate);
            } else {
                wp.amplitude = MathUtils.lerp(wp.amplitude, 0f, rate);
                if (wp.amplitude < 0.01f) {
                    activeWaterBody.waves.removeIndex(i);
                    i--;
                }
            }
        }

        float windDirRad = storm.windDirection * MathUtils.degreesToRadians;
        float currentFactor = registry.getSwimConfig() != null ?
            registry.getSwimConfig().windCurrentFactor : 0.03f;
        activeWaterBody.currentVelocity.set(
            MathUtils.cos(windDirRad) * storm.windSpeed * currentFactor,
            0f,
            MathUtils.sin(windDirRad) * storm.windSpeed * currentFactor
        );
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.WeatherSystemTest" -i`
Expected: All 8 tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/WeatherStateComponent.java \
       core/src/main/java/com/galacticodyssey/water/StormCellComponent.java \
       core/src/main/java/com/galacticodyssey/water/systems/WeatherSystem.java \
       core/src/test/java/com/galacticodyssey/water/systems/WeatherSystemTest.java
git commit -m "feat(water): add WeatherSystem with storm state machine, proximity events, and wave param lerping"
```

---

## Task 10: DeckWashSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/DeckWashComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/systems/DeckWashSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/water/systems/DeckWashSystemTest.java`

- [ ] **Step 1: Create DeckWashComponent**

```java
package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.IntArray;

public class DeckWashComponent implements Component {
    public final IntArray gunwaleSampleIndices = new IntArray();
    public float deckHeight;
    public float gunwaleSegmentLength = 2.0f;
    public float dischargeCd = 0.6f;
    public String topCompartmentId;
    public boolean deckAwash;
    public float deckAwashTimer;
    public static final float DECK_AWASH_THRESHOLD = 3.0f;
}
```

- [ ] **Step 2: Write failing tests**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.events.DeckWashEvent;
import com.galacticodyssey.water.events.DeckAwashEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckWashSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private DeckWashSystem deckWashSystem;
    private Entity shipEntity;
    private HullComponent hull;
    private FloodingComponent flooding;
    private DeckWashComponent deckWash;

    private final List<DeckWashEvent> washEvents = new ArrayList<>();
    private final List<DeckAwashEvent> awashEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        deckWashSystem = new DeckWashSystem(12, eventBus);
        engine = new Engine();
        engine.addSystem(deckWashSystem);

        shipEntity = new Entity();
        hull = new HullComponent();

        BuoyancySamplePoint gunwalePoint = new BuoyancySamplePoint();
        gunwalePoint.localOffset.set(2f, 3f, 0f);
        gunwalePoint.normal.set(0f, 1f, 0f);
        gunwalePoint.area = 1.0f;
        hull.samplePoints.add(gunwalePoint);

        flooding = new FloodingComponent();
        Compartment topComp = new Compartment("upper_deck", 50f);
        flooding.compartments.add(topComp);

        deckWash = new DeckWashComponent();
        deckWash.gunwaleSampleIndices.add(0);
        deckWash.deckHeight = 3f;
        deckWash.topCompartmentId = "upper_deck";
        deckWash.gunwaleSegmentLength = 2.0f;

        PhysicsBodyComponent physics = new PhysicsBodyComponent();

        shipEntity.add(hull);
        shipEntity.add(flooding);
        shipEntity.add(deckWash);
        shipEntity.add(physics);
        engine.addEntity(shipEntity);

        deckWashSystem.setTestWaterSurfaceHeight(Float.NaN);

        eventBus.subscribe(DeckWashEvent.class, washEvents::add);
        eventBus.subscribe(DeckAwashEvent.class, awashEvents::add);
    }

    @Test
    void noWashWhenWavesBelowGunwale() {
        deckWashSystem.setTestWaterSurfaceHeight(2.5f);

        engine.update(1f / 60f);

        assertTrue(washEvents.isEmpty());
        Compartment comp = flooding.compartments.get(0);
        assertEquals(0f, comp.waterVolume, 0.001f);
    }

    @Test
    void waterEntersWhenWavesExceedGunwale() {
        deckWashSystem.setTestWaterSurfaceHeight(4.0f);

        engine.update(1.0f);

        assertFalse(washEvents.isEmpty());
        Compartment comp = flooding.compartments.get(0);
        assertTrue(comp.waterVolume > 0f);
    }

    @Test
    void flowRateIncreasesWithOvertoppingDepth() {
        deckWashSystem.setTestWaterSurfaceHeight(3.5f);
        engine.update(1.0f);
        float lowOvertopping = flooding.compartments.get(0).waterVolume;

        flooding.compartments.get(0).waterVolume = 0f;
        washEvents.clear();

        deckWashSystem.setTestWaterSurfaceHeight(5.0f);
        engine.update(1.0f);
        float highOvertopping = flooding.compartments.get(0).waterVolume;

        assertTrue(highOvertopping > lowOvertopping);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.DeckWashSystemTest" -i`
Expected: FAIL — DeckWashSystem class does not exist

- [ ] **Step 4: Implement DeckWashSystem**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.events.BilgeAlarmEvent;
import com.galacticodyssey.water.events.DeckAwashEvent;
import com.galacticodyssey.water.events.DeckWashEvent;
import com.galacticodyssey.water.events.ShipSinkingEvent;

public class DeckWashSystem extends IteratingSystem {

    private final ComponentMapper<HullComponent> hullMapper =
        ComponentMapper.getFor(HullComponent.class);
    private final ComponentMapper<FloodingComponent> floodMapper =
        ComponentMapper.getFor(FloodingComponent.class);
    private final ComponentMapper<DeckWashComponent> washMapper =
        ComponentMapper.getFor(DeckWashComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private WaveSystem waveSystem;
    private float testWaterSurfaceHeight = Float.NaN;

    private static final float GRAVITY = 9.81f;

    public DeckWashSystem(int priority, EventBus eventBus) {
        super(Family.all(
            DeckWashComponent.class,
            HullComponent.class,
            FloodingComponent.class,
            PhysicsBodyComponent.class
        ).get(), priority);
        this.eventBus = eventBus;
    }

    public void setWaveSystem(WaveSystem waveSystem) { this.waveSystem = waveSystem; }
    public void setTestWaterSurfaceHeight(float h) { this.testWaterSurfaceHeight = h; }

    @Override
    protected void processEntity(Entity entity, float dt) {
        HullComponent hull = hullMapper.get(entity);
        FloodingComponent flooding = floodMapper.get(entity);
        DeckWashComponent wash = washMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);

        Matrix4 worldTx = new Matrix4().idt();
        if (physics.body != null) {
            physics.body.getWorldTransform(worldTx);
        }

        float totalFlow = 0f;
        Vector3 worldPt = Pools.obtain(Vector3.class);

        for (int i = 0; i < wash.gunwaleSampleIndices.size; i++) {
            int idx = wash.gunwaleSampleIndices.get(i);
            if (idx >= hull.samplePoints.size) continue;

            BuoyancySamplePoint sp = hull.samplePoints.get(idx);
            worldPt.set(sp.localOffset).mul(worldTx);

            float waterHeight = getWaterHeight(worldPt);
            float overtoppingDepth = waterHeight - worldPt.y;

            if (overtoppingDepth > 0f) {
                float flow = wash.dischargeCd * wash.gunwaleSegmentLength
                    * (float) Math.sqrt(2f * GRAVITY * overtoppingDepth);
                totalFlow += flow * dt;
            }
        }

        Pools.free(worldPt);

        if (totalFlow > 0f) {
            Compartment target = findCompartment(flooding, wash.topCompartmentId);
            if (target != null) {
                target.waterVolume = Math.min(target.volume, target.waterVolume + totalFlow);
            }
            eventBus.publish(new DeckWashEvent(entity, totalFlow / dt));

            wash.deckAwashTimer += dt;
            if (!wash.deckAwash && wash.deckAwashTimer > DeckWashComponent.DECK_AWASH_THRESHOLD) {
                wash.deckAwash = true;
                eventBus.publish(new DeckAwashEvent(entity));
            }
        } else {
            wash.deckAwashTimer = Math.max(0f, wash.deckAwashTimer - dt);
            if (wash.deckAwashTimer <= 0f) {
                wash.deckAwash = false;
            }
        }

        checkFloodingAlarms(entity, flooding);
    }

    private void checkFloodingAlarms(Entity entity, FloodingComponent flooding) {
        float totalFloodedMass = 0f;
        boolean allSubmerged = true;
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment comp = flooding.compartments.get(i);
            totalFloodedMass += comp.waterVolume * 1025f;
            if (comp.fillFraction() > 0.3f) {
                eventBus.publish(new BilgeAlarmEvent(entity, comp.id, comp.fillFraction()));
            }
            if (comp.fillFraction() < 1.0f) {
                allSubmerged = false;
            }
        }
        if (allSubmerged && flooding.compartments.size > 0) {
            eventBus.publish(new ShipSinkingEvent(entity, totalFloodedMass));
        }
    }

    private float getWaterHeight(Vector3 worldPos) {
        if (!Float.isNaN(testWaterSurfaceHeight)) {
            return testWaterSurfaceHeight;
        }
        if (waveSystem != null) {
            return waveSystem.getHeight(worldPos.x, worldPos.z);
        }
        return 0f;
    }

    private Compartment findCompartment(FloodingComponent flooding, String id) {
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment c = flooding.compartments.get(i);
            if (id.equals(c.id)) return c;
        }
        return null;
    }
}
```

Note: This system references `FloodingComponent` and `Compartment` which already exist in the codebase at `com.galacticodyssey.water.FloodingComponent` and `com.galacticodyssey.water.Compartment`. It also publishes `BilgeAlarmEvent` (when any compartment exceeds 30% flooded) and `ShipSinkingEvent` (when all compartments are fully flooded) as specified in the design spec's sinking progression milestones.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.DeckWashSystemTest" -i`
Expected: All 3 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/DeckWashComponent.java \
       core/src/main/java/com/galacticodyssey/water/systems/DeckWashSystem.java \
       core/src/test/java/com/galacticodyssey/water/systems/DeckWashSystemTest.java
git commit -m "feat(water): add DeckWashSystem for wave overtopping at ship gunwales"
```

---

## Task 11: HatchComponent and HatchFloodingSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/HatchComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/water/Hatch.java`
- Create: `core/src/main/java/com/galacticodyssey/water/systems/HatchFloodingSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/water/systems/HatchFloodingSystemTest.java`

- [ ] **Step 1: Create Hatch and HatchComponent**

```java
// Hatch.java
package com.galacticodyssey.water;

import com.badlogic.gdx.math.Vector3;

public class Hatch {
    public String id;
    public boolean isOpen;
    public float area;
    public final Vector3 localPosition = new Vector3();
    public String compartmentA;
    public String compartmentB;

    public Hatch(String id, float area, String compartmentA, String compartmentB) {
        this.id = id;
        this.area = area;
        this.compartmentA = compartmentA;
        this.compartmentB = compartmentB;
        this.isOpen = true;
    }
}
```

```java
// HatchComponent.java
package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

public class HatchComponent implements Component {
    public final Array<Hatch> hatches = new Array<>();
}
```

- [ ] **Step 2: Write failing tests**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HatchFloodingSystemTest {

    private Engine engine;
    private Entity shipEntity;
    private FloodingComponent flooding;
    private HatchComponent hatches;

    @BeforeEach
    void setUp() {
        EventBus eventBus = new EventBus();
        HatchFloodingSystem system = new HatchFloodingSystem(13, eventBus);
        engine = new Engine();
        engine.addSystem(system);

        shipEntity = new Entity();
        flooding = new FloodingComponent();

        Compartment compA = new Compartment("hold_fore", 100f);
        compA.waterVolume = 50f;
        Compartment compB = new Compartment("hold_aft", 100f);
        compB.waterVolume = 0f;
        flooding.compartments.add(compA);
        flooding.compartments.add(compB);

        hatches = new HatchComponent();
        Hatch hatch = new Hatch("hatch_fore_aft", 0.5f, "hold_fore", "hold_aft");
        hatch.isOpen = true;
        hatch.localPosition.set(0f, 0f, 5f);
        hatches.hatches.add(hatch);

        shipEntity.add(flooding);
        shipEntity.add(hatches);
        engine.addEntity(shipEntity);
    }

    @Test
    void waterFlowsThroughOpenHatch() {
        engine.update(1.0f);

        Compartment fore = findComp("hold_fore");
        Compartment aft = findComp("hold_aft");
        assertTrue(fore.waterVolume < 50f);
        assertTrue(aft.waterVolume > 0f);
    }

    @Test
    void noFlowThroughClosedHatch() {
        hatches.hatches.get(0).isOpen = false;

        engine.update(1.0f);

        Compartment fore = findComp("hold_fore");
        Compartment aft = findComp("hold_aft");
        assertEquals(50f, fore.waterVolume, 0.001f);
        assertEquals(0f, aft.waterVolume, 0.001f);
    }

    @Test
    void flowDirectionFollowsHeadDifference() {
        findComp("hold_fore").waterVolume = 10f;
        findComp("hold_aft").waterVolume = 80f;

        engine.update(1.0f);

        assertTrue(findComp("hold_fore").waterVolume > 10f);
        assertTrue(findComp("hold_aft").waterVolume < 80f);
    }

    @Test
    void waterVolumeNeverExceedsCapacity() {
        findComp("hold_fore").waterVolume = 95f;
        findComp("hold_aft").waterVolume = 95f;
        findComp("hold_aft").volume = 100f;

        engine.update(10.0f);

        assertTrue(findComp("hold_aft").waterVolume <= 100f);
        assertTrue(findComp("hold_fore").waterVolume >= 0f);
    }

    private Compartment findComp(String id) {
        for (int i = 0; i < flooding.compartments.size; i++) {
            if (id.equals(flooding.compartments.get(i).id)) {
                return flooding.compartments.get(i);
            }
        }
        return null;
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.HatchFloodingSystemTest" -i`
Expected: FAIL — HatchFloodingSystem class does not exist

- [ ] **Step 4: Implement HatchFloodingSystem**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;

public class HatchFloodingSystem extends IteratingSystem {

    private static final float GRAVITY = 9.81f;
    private static final float HATCH_CD = 0.6f;

    private final ComponentMapper<FloodingComponent> floodMapper =
        ComponentMapper.getFor(FloodingComponent.class);
    private final ComponentMapper<HatchComponent> hatchMapper =
        ComponentMapper.getFor(HatchComponent.class);

    private final EventBus eventBus;

    public HatchFloodingSystem(int priority, EventBus eventBus) {
        super(Family.all(
            FloodingComponent.class,
            HatchComponent.class
        ).get(), priority);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        FloodingComponent flooding = floodMapper.get(entity);
        HatchComponent hatchComp = hatchMapper.get(entity);

        for (int i = 0; i < hatchComp.hatches.size; i++) {
            Hatch hatch = hatchComp.hatches.get(i);
            if (!hatch.isOpen) continue;

            Compartment compA = findCompartment(flooding, hatch.compartmentA);
            Compartment compB = findCompartment(flooding, hatch.compartmentB);
            if (compA == null || compB == null) continue;

            float headA = compA.fillFraction();
            float headB = compB.fillFraction();
            float headDiff = headA - headB;

            if (Math.abs(headDiff) < 0.001f) continue;

            float flow = HATCH_CD * hatch.area
                * (float) Math.sqrt(2f * GRAVITY * Math.abs(headDiff)) * dt;

            if (headDiff > 0) {
                float transfer = Math.min(flow, compA.waterVolume);
                transfer = Math.min(transfer, compB.volume - compB.waterVolume);
                compA.waterVolume -= transfer;
                compB.waterVolume += transfer;
            } else {
                float transfer = Math.min(flow, compB.waterVolume);
                transfer = Math.min(transfer, compA.volume - compA.waterVolume);
                compB.waterVolume -= transfer;
                compA.waterVolume += transfer;
            }

            compA.waterVolume = MathUtils.clamp(compA.waterVolume, 0f, compA.volume);
            compB.waterVolume = MathUtils.clamp(compB.waterVolume, 0f, compB.volume);
        }
    }

    private Compartment findCompartment(FloodingComponent flooding, String id) {
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment c = flooding.compartments.get(i);
            if (id.equals(c.id)) return c;
        }
        return null;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew core:test --tests "com.galacticodyssey.water.systems.HatchFloodingSystemTest" -i`
Expected: All 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/Hatch.java \
       core/src/main/java/com/galacticodyssey/water/HatchComponent.java \
       core/src/main/java/com/galacticodyssey/water/systems/HatchFloodingSystem.java \
       core/src/test/java/com/galacticodyssey/water/systems/HatchFloodingSystemTest.java
git commit -m "feat(water): add HatchFloodingSystem for inter-compartment water flow"
```

---

## Task 12: SwimCameraSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/water/systems/SwimCameraSystem.java`

- [ ] **Step 1: Implement SwimCameraSystem**

```java
package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.water.SwimState;
import com.galacticodyssey.water.SwimmingStateComponent;
import com.galacticodyssey.water.data.SwimConfigData;
import com.galacticodyssey.water.data.WaterDataRegistry;

public class SwimCameraSystem extends IteratingSystem {

    private static final float SURFACE_Y_LERP_SPEED = 5.0f;
    private static final float ROLL_FACTOR = 0.3f;
    private static final float UNDERWATER_SWAY_AMPLITUDE = 0.5f;
    private static final float UNDERWATER_SWAY_FREQ = 0.3f;

    private final ComponentMapper<SwimmingStateComponent> swimMapper =
        ComponentMapper.getFor(SwimmingStateComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final SwimConfigData config;
    private WaveSystem waveSystem;
    private float time;

    public SwimCameraSystem(int priority, WaterDataRegistry registry) {
        super(Family.all(
            SwimmingStateComponent.class,
            FPSCameraComponent.class,
            TransformComponent.class
        ).get(), priority);
        this.config = registry.getSwimConfig();
    }

    public void setWaveSystem(WaveSystem waveSystem) {
        this.waveSystem = waveSystem;
    }

    @Override
    public void update(float dt) {
        time += dt;
        super.update(dt);
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        SwimmingStateComponent swim = swimMapper.get(entity);
        FPSCameraComponent camera = cameraMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);

        if (swim.swimState == SwimState.DRY) return;

        switch (swim.swimState) {
            case WADING:
                camera.headBobAmplitude = 0.02f;
                camera.headBobFrequency = 6.0f;
                break;

            case SURFACE:
                float targetY = swim.waterSurfaceHeight + config.surfaceEyeOffset;
                camera.currentEyeHeight = MathUtils.lerp(
                    camera.currentEyeHeight, targetY - transform.position.y,
                    SURFACE_Y_LERP_SPEED * dt);

                if (waveSystem != null) {
                    Vector3 normal = Pools.obtain(Vector3.class);
                    waveSystem.getNormal(transform.position.x, transform.position.z, normal);
                    float rollTarget = (float) Math.atan2(normal.x, normal.y) * ROLL_FACTOR;
                    Pools.free(normal);
                }

                camera.headBobAmplitude = 0f;
                break;

            case DIVING:
            case SUBMERGED:
                float sway = MathUtils.sin(time * MathUtils.PI2 * UNDERWATER_SWAY_FREQ)
                    * UNDERWATER_SWAY_AMPLITUDE;
                camera.headBobAmplitude = 0f;
                break;

            case DROWNING:
                camera.headBobAmplitude = 0f;
                break;
        }

        if (swim.hasAscentSickness && swim.ascentSicknessTimer > 0f) {
            swim.ascentSicknessTimer -= dt;
            if (swim.ascentSicknessTimer <= 0f) {
                swim.hasAscentSickness = false;
                swim.ascentSicknessTimer = 0f;
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/water/systems/SwimCameraSystem.java
git commit -m "feat(water): add SwimCameraSystem for wave bobbing and underwater effects"
```

---

## Task 13: GameWorld Registration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add system registration to GameWorld**

Add the following after the existing water system registrations (after the `FloodingHudSystem` line, around line 344). Add the necessary imports at the top of the file.

Imports to add:
```java
import com.galacticodyssey.water.data.WaterDataRegistry;
import com.galacticodyssey.water.systems.SwimmingSystem;
import com.galacticodyssey.water.systems.UnderwaterSystem;
import com.galacticodyssey.water.systems.WeatherSystem;
import com.galacticodyssey.water.systems.DeckWashSystem;
import com.galacticodyssey.water.systems.HatchFloodingSystem;
import com.galacticodyssey.water.systems.SwimCameraSystem;
```

Registration code (insert after existing water systems, before the closing of the water systems block):
```java
// Swimming & water mechanics
WaterDataRegistry waterDataRegistry = new WaterDataRegistry();
this.waterDataRegistry = waterDataRegistry;

Entity weatherEntity = new Entity();
weatherEntity.add(new com.galacticodyssey.water.WeatherStateComponent());
engine.addEntity(weatherEntity);

WeatherSystem weatherSystem = new WeatherSystem(5, eventBus, waterDataRegistry);
weatherSystem.setActiveWaterBody(/* pass the active ocean WaterBodyComponent */);
engine.addSystem(weatherSystem);

DeckWashSystem deckWashSystem = new DeckWashSystem(12, eventBus);
deckWashSystem.setWaveSystem(waveSystem);
engine.addSystem(deckWashSystem);

HatchFloodingSystem hatchFloodingSystem = new HatchFloodingSystem(13, eventBus);
engine.addSystem(hatchFloodingSystem);

SwimmingSystem swimmingSystem = new SwimmingSystem(15, eventBus, waterDataRegistry);
swimmingSystem.setWaveSystem(waveSystem);
engine.addSystem(swimmingSystem);

UnderwaterSystem underwaterSystem = new UnderwaterSystem(16, eventBus, waterDataRegistry);
engine.addSystem(underwaterSystem);

SwimCameraSystem swimCameraSystem = new SwimCameraSystem(90, waterDataRegistry);
swimCameraSystem.setWaveSystem(waveSystem);
engine.addSystem(swimCameraSystem);
```

Also add the `waterDataRegistry` field declaration near the other registry fields:
```java
private final WaterDataRegistry waterDataRegistry;
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(core): register swimming, weather, and ship-water systems in GameWorld"
```

---

## Task 14: Run All Tests

- [ ] **Step 1: Run full test suite**

Run: `./gradlew core:test -i`
Expected: All tests PASS including new and existing

- [ ] **Step 2: Fix any failures**

If any existing tests break, investigate and fix. The most likely issue is the `PlayerMovementSystem` guard change — existing player tests should still pass because test entities won't have `SwimmingStateComponent`.

- [ ] **Step 3: Final commit if fixes were needed**

```bash
git add -A
git commit -m "fix: resolve test failures from swimming system integration"
```
