---
name: libgdx-ship-cockpit-hud
description: How to build and extend the ship piloting HUD in Galactic Odyssey using Scene2D.UI. Covers the cockpit instrument overlay (velocity, altitude, attitude, throttle, heading), targeting reticle, radar/proximity display, shield and hull status bars, weapon status indicators, flight mode display, and warning alerts. Use this skill whenever the user wants to add ship HUD elements, create cockpit instruments, build a targeting system display, add a radar or minimap for ship mode, show ship status (shields, hull, power), display flight telemetry (speed, altitude, heading), create warning indicators (collision, low fuel, damage), add weapon lock-on UI, build an attitude indicator (artificial horizon), or style the piloting UI. Also trigger for "ship HUD", "cockpit UI", "flight instruments", "targeting reticle", "ship radar", "shield bar", "hull integrity display", "throttle indicator", "attitude indicator", "velocity vector", "heading compass", "weapon status HUD", "flight telemetry", or "pilot display".
---

# Ship Cockpit HUD

This skill covers how to build the ship piloting HUD using Scene2D.UI, following the project's existing UI patterns from `UiFactory` and `GameScreen`.

## Architecture

The ship HUD is a Scene2D `Stage` that overlays the 3D viewport when the player is in `PILOTING` mode. It subscribes to `PlayerStartPilotingEvent` / `PlayerStopPilotingEvent` via `EventBus` to show/hide itself.

```
GameScreen.render()
  ├── renderPlanetTerrain()     ← 3D world
  ├── renderShips()             ← 3D ships
  └── shipHudStage.draw()      ← 2D HUD overlay (only when PILOTING)
```

### Integration Point

In `GameScreen`, create and manage the HUD stage:

```java
private ShipHudStage shipHud;

private void initializeWorld() {
    // ... existing init ...
    shipHud = new ShipHudStage(game.getSkin(), eventBus);
}

public void render(float delta) {
    // ... existing render ...
    shipHud.update(delta, camera, pilotedShip);
    shipHud.draw();
}

public void resize(int width, int height) {
    // ... existing resize ...
    shipHud.resize(width, height);
}

public void dispose() {
    // ... existing dispose ...
    shipHud.dispose();
}
```

## HUD Layout

The HUD is organized into screen-edge-anchored panels. Use Scene2D's `Table` layout with `FitViewport` at a fixed logical resolution (1920x1080 recommended for HUD design, scaled by viewport).

```
┌─────────────────────────────────────────────────────┐
│ [Flight Mode]              [Warnings]    [Heading]  │
│                                                      │
│                                                      │
│ [Velocity]                              [Shields]    │
│ [Altitude]            [Reticle]         [Hull]       │
│ [Throttle Bar]                          [Power]      │
│                                                      │
│                                                      │
│ [Weapons L]     [Attitude Indicator]   [Weapons R]   │
│                    [Radar]                            │
└─────────────────────────────────────────────────────┘
```

## ShipHudStage Class

```java
public class ShipHudStage implements Disposable {

    private final Stage stage;
    private final Skin skin;
    private boolean visible;

    // Instrument panels
    private Label velocityLabel;
    private Label altitudeLabel;
    private Label headingLabel;
    private Label flightModeLabel;
    private ThrottleBar throttleBar;
    private ShieldHullBar shieldBar;
    private ShieldHullBar hullBar;
    private AttitudeIndicator attitudeIndicator;
    private RadarDisplay radarDisplay;
    private TargetingReticle reticle;
    private WeaponStatusPanel weaponsPanel;
    private WarningIndicator warningIndicator;

    public ShipHudStage(Skin skin, EventBus eventBus) {
        this.skin = skin;
        this.stage = new Stage(new FitViewport(1920, 1080));

        buildLayout();

        eventBus.subscribe(PlayerStartPilotingEvent.class, e -> show(e.ship));
        eventBus.subscribe(PlayerStopPilotingEvent.class, e -> hide());
    }

    public void update(float delta, PerspectiveCamera camera, Entity ship) {
        if (!visible || ship == null) return;
        updateTelemetry(ship);
        updateAttitude(ship);
        updateRadar(ship, camera);
        updateWarnings(ship);
        stage.act(delta);
    }

    public void draw() {
        if (!visible) return;
        stage.draw();
    }
}
```

## Instrument Panels

### Velocity / Altitude / Heading — Text Labels

Simple `Label` widgets updated each frame from ship state:

```java
private void updateTelemetry(Entity ship) {
    ShipFlightComponent flight = ship.getComponent(ShipFlightComponent.class);
    TransformComponent transform = ship.getComponent(TransformComponent.class);
    PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);

    float speed = physics.linearVelocity.len();
    velocityLabel.setText(String.format("%.0f m/s", speed));

    float altitude = transform.position.len() - planetRadius;
    altitudeLabel.setText(String.format("ALT %.0f m", altitude));

    // Heading from ship forward projected onto local tangent plane
    Vector3 forward = new Vector3(0, 0, -1).mul(transform.rotation);
    Vector3 up = new Vector3(transform.position).nor();
    Vector3 north = new Vector3(0, 1, 0); // or planet north pole
    float heading = computeHeading(forward, up, north);
    headingLabel.setText(String.format("%03.0f°", heading));
}
```

Style these with monospace font and a semi-transparent dark background for readability against any sky/space background.

### Flight Mode Display

Shows current flight state — toggle between FLIGHT_ASSIST and NEWTONIAN:

```java
flightModeLabel.setText(flight.flightAssistEnabled ? "FA ON" : "FA OFF");
flightModeLabel.setColor(flight.flightAssistEnabled ? Color.GREEN : Color.ORANGE);
```

### Throttle Bar — Vertical Bar Widget

A custom `Widget` or `ProgressBar` on the left edge showing current throttle (-1 to +1):

```java
public class ThrottleBar extends Widget {
    private float throttle; // -1 (full reverse) to +1 (full forward)

    @Override
    public void draw(Batch batch, float parentAlpha) {
        // Draw background bar
        // Draw filled portion from center (0) up (positive) or down (negative)
        float centerY = getY() + getHeight() * 0.5f;
        float fillHeight = throttle * getHeight() * 0.5f;

        batch.setColor(throttle >= 0 ? Color.CYAN : Color.RED);
        // Draw filled rect from centerY to centerY + fillHeight
    }

    public void setThrottle(float t) {
        this.throttle = MathUtils.clamp(t, -1f, 1f);
    }
}
```

### Shield / Hull Bars — Horizontal Status Bars

Positioned on the right edge. Use `ProgressBar` or custom widget:

```java
public class ShieldHullBar extends ProgressBar {
    private final Color fullColor;
    private final Color lowColor;
    private static final float LOW_THRESHOLD = 0.25f;

    public ShieldHullBar(Color full, Color low, Skin skin) {
        super(0, 1, 0.01f, false, skin);
        this.fullColor = full;
        this.lowColor = low;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        float pct = getPercent();
        // Lerp color from full to low as value drops
        Color c = pct > LOW_THRESHOLD ? fullColor : lowColor;
        getStyle().knobBefore.setColor(c);
        // Flash when critical
        if (pct < LOW_THRESHOLD) {
            float flash = (MathUtils.sin(delta * 8f) + 1f) * 0.5f;
            setColor(1, 1, 1, 0.7f + flash * 0.3f);
        }
    }
}
```

Shield bar: cyan full, blue low. Hull bar: green full, red low.

### Attitude Indicator (Artificial Horizon)

A custom `Widget` that shows ship pitch and roll relative to the local horizon. This is the most complex HUD element.

```java
public class AttitudeIndicator extends Widget {
    private float pitch;  // degrees, positive = nose up
    private float roll;   // degrees, positive = clockwise

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float cx = getX() + getWidth() / 2f;
        float cy = getY() + getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f;

        // Use ShapeRenderer (end batch, draw shapes, restart batch)
        // 1. Draw sky (upper half) and ground (lower half), rotated by roll
        //    and offset vertically by pitch
        // 2. Draw horizon line
        // 3. Draw pitch ladder lines at 10° intervals
        // 4. Draw fixed aircraft symbol (center crosshair)
        // 5. Draw roll arc with tick marks at top
    }

    public void update(Entity ship) {
        TransformComponent t = ship.getComponent(TransformComponent.class);
        Vector3 up = new Vector3(t.position).nor();       // local up (radial)
        Vector3 forward = new Vector3(0, 0, -1).mul(t.rotation);
        Vector3 right = new Vector3(1, 0, 0).mul(t.rotation);
        Vector3 shipUp = new Vector3(0, 1, 0).mul(t.rotation);

        // Pitch = angle between forward and local tangent plane
        pitch = MathUtils.asin(forward.dot(up)) * MathUtils.radiansToDegrees;

        // Roll = angle of ship's right vector around the forward axis
        // relative to the tangent plane
        Vector3 horizonRight = new Vector3(right).sub(up.cpy().scl(right.dot(up))).nor();
        roll = MathUtils.atan2(shipUp.dot(up.cpy().crs(forward).nor()),
                               shipUp.dot(up)) * MathUtils.radiansToDegrees;
    }
}
```

Because the game uses spherical planet gravity, "up" is the radial direction from planet center, not world Y. The attitude indicator must compute pitch/roll relative to the local tangent plane at the ship's position.

### Radar Display — Top-Down Proximity Map

A circular minimap showing nearby entities relative to the ship:

```java
public class RadarDisplay extends Widget {
    private static final float RADAR_RANGE = 2000f; // meters
    private final Array<RadarBlip> blips = new Array<>();

    public void updateBlips(Entity playerShip, Engine engine) {
        blips.clear();
        TransformComponent shipT = playerShip.getComponent(TransformComponent.class);
        Vector3 shipPos = shipT.position;
        Quaternion shipRot = shipT.rotation;

        // Query nearby entities
        for (Entity e : engine.getEntitiesFor(Family.all(TransformComponent.class).get())) {
            if (e == playerShip) continue;
            TransformComponent t = e.getComponent(TransformComponent.class);
            float dist = t.position.dst(shipPos);
            if (dist > RADAR_RANGE) continue;

            // Transform to ship-local coordinates
            Vector3 relative = t.position.cpy().sub(shipPos);
            relative.mul(shipRot.cpy().conjugate()); // world → ship local

            // Project onto ship's XZ plane for 2D radar
            float radarX = relative.x / RADAR_RANGE;
            float radarZ = relative.z / RADAR_RANGE;

            BlipType type = classifyEntity(e);
            blips.add(new RadarBlip(radarX, radarZ, type));
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float cx = getX() + getWidth() / 2f;
        float cy = getY() + getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;

        // Draw circle background (dark, semi-transparent)
        // Draw range rings at 25%, 50%, 75%
        // Draw cardinal direction ticks
        // Draw blips as colored dots:
        //   Green = friendly, Red = hostile, Yellow = neutral, White = station
        // Draw player triangle at center
    }
}

enum BlipType { FRIENDLY, HOSTILE, NEUTRAL, STATION, PROJECTILE }
```

### Targeting Reticle

A screen-center crosshair that changes state based on what the ship is pointing at:

```java
public class TargetingReticle extends Widget {
    private TargetState state = TargetState.IDLE;
    private float lockProgress; // 0 to 1 for lock-on weapons

    enum TargetState {
        IDLE,       // no target — simple crosshair
        TRACKING,   // pointing at a valid target — crosshair highlights
        LOCKING,    // lock-on weapon acquiring — animated ring
        LOCKED      // lock complete — solid ring, fire indicator
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float cx = getX() + getWidth() / 2f;
        float cy = getY() + getHeight() / 2f;

        switch (state) {
            case IDLE:
                // Simple crosshair lines (thin, white, 50% alpha)
                break;
            case TRACKING:
                // Crosshair + target info text (name, distance, hull %)
                break;
            case LOCKING:
                // Rotating dashed circle, filling as lockProgress increases
                break;
            case LOCKED:
                // Solid circle, "LOCKED" text, diamond corners
                break;
        }
    }
}
```

### Warning Indicators

Flash warnings for critical conditions:

```java
public class WarningIndicator extends Table {
    private final Array<Warning> activeWarnings = new Array<>();

    enum WarningType {
        COLLISION("PULL UP", Color.RED),
        LOW_FUEL("LOW FUEL", Color.ORANGE),
        HULL_CRITICAL("HULL CRITICAL", Color.RED),
        SHIELDS_DOWN("SHIELDS DOWN", Color.YELLOW),
        MISSILE_INCOMING("MISSILE", Color.RED),
        OVERHEAT("OVERHEAT", Color.ORANGE);

        final String text;
        final Color color;
        WarningType(String text, Color color) {
            this.text = text;
            this.color = color;
        }
    }

    public void setWarning(WarningType type, boolean active) {
        // Add or remove from activeWarnings
        // Warnings stack vertically, newest at top
        // Each flashes at 2Hz when active
    }
}
```

## Rendering Approach

### ShapeRenderer for Geometric Elements

The attitude indicator, radar, and throttle bar use geometric drawing. Since Scene2D uses a `SpriteBatch`, you need to flush the batch, draw shapes, then restart:

```java
@Override
public void draw(Batch batch, float parentAlpha) {
    batch.end();  // flush SpriteBatch

    shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
    // ... draw shapes ...
    shapeRenderer.end();

    shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
    // ... draw outlines ...
    shapeRenderer.end();

    batch.begin();  // restart SpriteBatch for subsequent actors
}
```

Alternatively, pre-render instruments to a `FrameBuffer` texture and display as an `Image` actor — better for performance if the instruments don't change every frame.

### Font Choices

Use two font sizes from UiFactory:
- **Large mono** (24pt) for primary readings (velocity, altitude)
- **Small mono** (14pt) for secondary info (heading digits, weapon ammo)

Set up in UiFactory with `FreeTypeFontGenerator` using a monospace face (e.g., "Share Tech Mono", "Inconsolata", or the system monospace fallback).

## Color Palette

Consistent color language across all HUD elements:

| Color | Meaning | Hex |
|---|---|---|
| Cyan | Velocity, throttle forward, active systems | `#00FFFF` |
| Green | Friendly, hull healthy, shields charged | `#00FF88` |
| Red | Hostile, damage, critical, reverse thrust | `#FF3333` |
| Orange | Warning, caution, flight assist off | `#FF8800` |
| Yellow | Neutral entities, shield low | `#FFFF00` |
| White | Text, reticle idle, structural lines | `#CCCCCC` |
| Dark background | Panel backing | `#000000` at 40-60% alpha |

## Spherical Planet Considerations

On a spherical planet, "up" is radial (position normalized), not world Y. This affects:

- **Altitude** — `transform.position.len() - planetRadius`, not `position.y`
- **Heading** — project ship forward onto the local tangent plane (perpendicular to radial up), then measure angle from a reference north
- **Attitude indicator** — pitch and roll are relative to the tangent plane, not the XZ plane
- **Radar** — "horizontal" plane for the 2D projection is the tangent plane at the ship's position

Compute the local tangent frame each tick:

```java
Vector3 radialUp = new Vector3(shipPos).nor();
Vector3 east = new Vector3(0, 1, 0).crs(radialUp).nor(); // or planet north × up
Vector3 north = new Vector3(radialUp).crs(east).nor();
// tangent plane basis: (east, north) with normal = radialUp
```

## Common Mistakes

| Mistake | Fix |
|---|---|
| Using world Y for altitude on a spherical planet | Use `position.len() - planetRadius` |
| HUD elements not scaling with window resize | Use `FitViewport` with fixed logical resolution (1920x1080); call `viewport.update()` in `resize()` |
| ShapeRenderer projection doesn't match Stage | Set `shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix())` before drawing |
| HUD flickers or draws behind 3D | Draw the HUD Stage **after** all 3D rendering; disable depth test during HUD draw |
| Radar blips jitter when ship rotates | Transform blips to ship-local space using the inverse of the ship's rotation quaternion |
| Attitude indicator gimbal-locks at poles | Use quaternion-derived vectors, not euler angles, for pitch/roll computation |
| Font appears blurry at different resolutions | Generate fonts at the viewport's logical resolution, not the screen resolution; `FitViewport` handles scaling |
