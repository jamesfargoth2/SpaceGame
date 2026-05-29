---
name: procgen-scale-reference
description: >
  Canonical physical constants and real-world size ranges for every
  procedurally generated entity in Galactic Odyssey — covering all celestial
  body types from cometary nuclei to galaxy superclusters, all stellar classes
  from Y-type brown dwarfs to hypergiant stars, all stellar remnants, Solar
  System body data, exoplanet categories, moon archetypes, asteroid families,
  nebula types, star clusters, galaxy morphologies, and exotic objects
  (magnetars, pulsars, quasars, GRBs). Also includes ship, station, city,
  building, creature, and vegetation size ranges plus LOD calibration formulas.
  Use this skill BEFORE writing any procgen system that involves a size,
  distance, mass, gravity, temperature, density, or LOD threshold. Also use
  when debugging why something looks too big or too small, when two systems
  use inconsistent units, or when deciding LOD switch distances for any new
  object type. All values are the authoritative game constants — never invent
  size ranges; always reference this skill instead.
---

# Scale Reference — Authoritative Physical Constants & Celestial Body Data

## Fundamental Constants

```java
public final class PhysicsConstants {
    public static final double G                = 6.674e-11;   // m³ kg⁻¹ s⁻²
    public static final double C                = 2.998e8;     // m/s
    public static final double STEFAN_BOLTZMANN = 5.670e-8;   // W m⁻² K⁻⁴
    public static final double PLANCK           = 6.626e-34;   // J·s
    public static final double BOLTZMANN        = 1.381e-23;   // J/K

    // Solar reference
    public static final double SOLAR_MASS_KG       = 1.989e30;
    public static final double SOLAR_RADIUS_KM     = 695_700.0;
    public static final double SOLAR_RADIUS_M      = 6.957e8;
    public static final double SOLAR_LUMINOSITY_W  = 3.828e26;
    public static final double SOLAR_TEMP_K        = 5_778.0;

    // Earth reference
    public static final double EARTH_RADIUS_KM     = 6_371.0;
    public static final double EARTH_MASS_KG       = 5.972e24;
    public static final double EARTH_SURFACE_G     = 9.807;    // m/s²
    public static final double EARTH_ATM_PA        = 101_325.0;
    public static final double EARTH_SCALE_HEIGHT_KM = 8.5;

    // Distance units
    public static final double AU_KM   = 1.496e8;
    public static final double AU_M    = 1.496e11;
    public static final double LY_KM   = 9.461e12;
    public static final double LY_M    = 9.461e15;
    public static final double LY_AU   = 63_241.0;
    public static final double PC_LY   = 3.2616;   // parsec in light-years
    public static final double KPC_LY  = 3_261.6;  // kiloparsec
    public static final double MPC_LY  = 3_261_600.0;
}
```

---

## Substellar Objects (Below Stellar Ignition)

```
// Hydrogen fusion threshold: ~0.08 M☉ = 80 Jupiter masses
// Deuterium fusion threshold: ~13 Jupiter masses (brown dwarf / planet boundary)
// Jupiter mass = 1.898e27 kg = 317.8 M_Earth = 0.000955 M☉

// Type  | Subtype   | T (K)     | Radius (R_Jup) | Mass (M_Jup) | Example
// ────────────────────────────────────────────────────────────────────────────
// L      0-8        2200–1300   0.90–1.10        75–25          2MASS J1047
// T      0-8        1300–700    0.85–1.00        60–13          Gliese 229B
// Y      0-4        <700        0.80–0.95        <13            WISE 1828
// Rogue planet      <300        0.90–1.10        1–13           PSO J318.5
// Super-Jupiter     ~900        1.05–1.30        13–80          (substellar gap)

// Jupiter class (gas giant planet, not star):
//   M_J = 1.898e27 kg = 317.8 M_Earth
//   R_J = 71,492 km = 11.21 R_Earth = 0.103 R☉
```

---

## Stellar Remnants

```
// WHITE DWARFS
// ─────────────────────────────────────────────────────────────────────────
// Mass range:    0.17–1.35 M☉  (Chandrasekhar limit = 1.44 M☉)
// Radius:        3,000–20,000 km  (roughly Earth-sized)
// Typical radius ~7,000 km = 0.0101 R☉ = 1.10 R_Earth
// Density:       10^5–10^9 kg/m³  (average ~10^6)
// Temperature:   4,000 K (cool/old) to 200,000 K (young/hot)
// Cooling time:  >10^10 yr to reach black dwarf (none exist yet; universe too young)
//
// Subtype | T (K)    | Spectrum  | Note
// ──────────────────────────────────────────────────────────────────────────
// DA       6000-80000  H lines     Most common (~75%)
// DB       12000-45000 He I lines  ~8%
// DC       <12000      Featureless no spectral lines
// DO       45000-200000 He II      Very hot
// DZ       any         Metal lines accreting from debris disk
// DQ       any         Carbon      ~2%

// NEUTRON STARS
// ─────────────────────────────────────────────────────────────────────────
// Mass:      1.17–2.35 M☉ (theoretical max ~3 M☉ before black hole)
// Radius:    10–13 km
// Density:   4×10^17 kg/m³  (nuclear density; 1 teaspoon = ~1 billion tonnes)
// Formed by: core collapse of 8–20 M☉ star → Type II supernova
//
// Subtype    | Period       | B field (T) | Note
// ──────────────────────────────────────────────────────────────────────────
// Pulsar     | 1.4 ms–8.5 s | 10^8        | Fastest: PSR J1748-2446ad 716 Hz
// Millisecond| <30 ms       | 10^5        | Recycled; very stable
// Magnetar   | 2–12 s       | 10^10–10^11 | Strongest known B field in universe
// Soft gamma repeater | irregular | 10^11 | Magnetar subtype; emits bursts

public static final float NEUTRON_STAR_RADIUS_KM     = 11.5f;
public static final float NEUTRON_STAR_MIN_MASS_SOLAR = 1.17f;
public static final float NEUTRON_STAR_MAX_MASS_SOLAR = 2.35f;
public static final float MAGNETAR_BFIELD_TESLA       = 1e11f; // 10^11 T

// BLACK HOLES
// ─────────────────────────────────────────────────────────────────────────
// Schwarzschild radius: r_s = 2GM/c² = 2.953 km × (M / M☉)
//
// Class          | Mass range          | r_s            | Example
// ──────────────────────────────────────────────────────────────────────────
// Stellar        | 3–100 M☉            | 9–300 km       | Cygnus X-1 ~21 M☉ → 62 km
// Intermediate   | 10²–10⁵ M☉          | 300 km–300,000 km | M82 X-1 ~400 M☉
// Supermassive   | 10⁶–10¹⁰ M☉         | 3–30,000 M_km  | Sgr A* 4.1M M☉ → 12M km
// Ultramassive   | >10¹⁰ M☉            | >30,000 M_km   | TON 618: 6.6×10^10 M☉

public static final double SCHWARZ_KM_PER_SOLAR = 2.953; // multiply by mass in M☉

// Known supermassive BH examples (for game flavor/scale reference):
// Sagittarius A* (Milky Way centre): 4.1×10^6 M☉ → r_s = 12.1M km (0.082 AU)
// M87* (Virgo A): 6.5×10^9 M☉ → r_s = 19.2B km = 128 AU
// TON 618 (quasar): 6.6×10^10 M☉ → r_s ≈ 195B km = 1,300 AU
```

---

## Main Sequence Stars — All Spectral Classes

```
// Luminosity class V. Mass-Radius relation: R ≈ M^0.8 for M < 1 M☉; R ≈ M^0.57 for M > 1 M☉
// Luminosity: L ≈ M^4 for M > 0.43 M☉; L ≈ 0.23×M^2.3 for M < 0.43 M☉
// Main sequence lifetime: t_MS ≈ (M/L) × 10 Gyr
//
// Class | Subtype | T_eff (K)    | R (R☉)    | M (M☉)    | L (L☉)      | Colour      | t_MS
// ──────────────────────────────────────────────────────────────────────────────────────────
// O     | O3V     | 44,000–50,000| 10–15     | 35–80     | 250k–1M     | Blue-violet | <1 Myr
//       | O5V     | 42,000       | 10.9      | 40        | 400,000     |             | ~3 Myr
//       | O9V     | 32,000       | 7.0       | 20        | 60,000      |             | ~10 Myr
// B     | B0V     | 30,000       | 5.7       | 17        | 44,000      | Blue-white  | ~13 Myr
//       | B2V     | 22,000       | 3.5       | 9         | 3,300       |             | ~50 Myr
//       | B5V     | 15,400       | 2.7       | 5.4       | 570         |             | ~200 Myr
//       | B8V     | 11,400       | 1.9       | 3.0       | 120         |             | ~500 Myr
// A     | A0V     | 9,600        | 2.4       | 2.1       | 35          | White       | ~1 Gyr
//       | A5V     | 8,200        | 1.7       | 1.8       | 14          |             | ~1.5 Gyr
// F     | F0V     | 7,200        | 1.5       | 1.5       | 6.5         | Yellow-white| ~2.5 Gyr
//       | F5V     | 6,440        | 1.2       | 1.2       | 2.5         |             | ~4 Gyr
// G     | G0V     | 5,940        | 1.05      | 1.05      | 1.26        | Yellow      | ~9 Gyr
//       | G2V☉    | 5,778        | 1.000     | 1.000     | 1.000       | (our Sun)   | 10 Gyr
//       | G5V     | 5,660        | 0.93      | 0.92      | 0.79        |             |
// K     | K0V     | 5,270        | 0.85      | 0.80      | 0.52        | Orange      | ~17 Gyr
//       | K4V     | 4,560        | 0.74      | 0.67      | 0.21        |             |
//       | K8V     | 3,900        | 0.60      | 0.51      | 0.06        |             |
// M     | M0V     | 3,850        | 0.59      | 0.50      | 0.052       | Red         | ~56 Gyr
//       | M2V     | 3,580        | 0.45      | 0.40      | 0.025       |             | >100 Gyr
//       | M5V     | 3,050        | 0.20      | 0.162     | 0.0071      | Proxima Cen | >100 Gyr
//       | M8V     | 2,500        | 0.12      | 0.09      | 0.00086     |             | >1 Tyr
```

---

## Giant Stars (Luminosity Classes III–II)

```
// Subgiant (IV): just off main sequence; R ≈ 1.5–4 R☉
// Giant (III): core-He burning or RGB; R ≈ 4–100 R☉
// Bright giant (II): R ≈ 20–200 R☉
//
// Class  | T (K)   | R (R☉)  | M (M☉) | L (L☉)  | Example
// ────────────────────────────────────────────────────────────────────────────
// G5III  | 5,100   | 8–12    | 1.5–2  | 40–60   | Capella A (1.25 R☉ → subgiant)
// G8III  | 4,900   | 10      | 2      | 55      | Pollux (8.8 R☉)
// K0III  | 4,800   | 15–25   | 1.5–3  | 80–200  | Arcturus (25.4 R☉, 170 L☉)
// K2III  | 4,500   | 20–40   | 1.5–3  | 150–500 |
// K5III  | 4,000   | 30–60   | 1.5–3  | 200–800 | Aldebaran K5III (44.2 R☉)
// M0III  | 3,800   | 50–100  | 1.5–4  | 500–2k  | Mira at minimum
// M2III  | 3,600   | 80–200  | 1.5–4  | 1k–5k   |
// M5III  | 3,200   | 150–700 | 1–3    | 2k–20k  | Mira at maximum (700 R☉)
//
// Note: AGB (asymptotic giant branch) stars: variable R, strong stellar winds,
// forming planetary nebulae. Mira: 332-day period, R = 332–541 R☉ (pulsates!)
```

---

## Supergiant Stars (Luminosity Classes Ia–Ib)

```
// Supergiants: post-main-sequence massive stars, typically 8–50+ M☉ progenitors
// Live only millions of years; explode as core-collapse supernovae (Type II, Ib, Ic)
//
// Class  | T (K)    | R (R☉)  | M (M☉) | L (L☉)    | Example
// ────────────────────────────────────────────────────────────────────────────
// O Ia   | 30,000   | 15–25   | 20–40  | 200k–600k | Zeta Puppis
// B Ia   | 12,000   | 60–100  | 15–30  | 50k–200k  | Rigel (78 R☉, 120k L☉)
// A Ia   | 8,500    | 150–250 | 12–20  | 50k–100k  | Deneb (203 R☉, 196k L☉)
// F Ia   | 6,500    | 50–80   | 10–15  | 8k–30k    | Polaris (46 R☉)
// G Ia   | 5,500    | 100–200 | 10–15  | 15k–50k   | HR 8752 (G0Ia, 750 R☉ var)
// K Ia   | 4,300    | 200–400 | 10–15  | 20k–100k  | Epsilon Aurigae component
// M Ia   | 3,500    | 500–1200| 10–20  | 50k–500k  | Betelgeuse (887±203 R☉)
//
// Betelgeuse (α Orionis): M2Iab, R ≈ 700–1200 R☉ (variable), 126,000 L☉
//   Distance: 700 LY. If at Sun's position, would engulf Mars's orbit.
```

---

## Hypergiants & Extreme Stars

```
// Hypergiants: luminosity > 10^6 L☉ or log(L/L☉) > 5.5; rarely more than
// a few hundred known. Near Eddington luminosity limit.
//
// Object            | Class   | T (K)  | R (R☉) | L (L☉)    | Note
// ────────────────────────────────────────────────────────────────────────────
// Eta Carinae A     | LBV     | 9,400  | 240    | 5×10^6    | LBV; giant eruption 1843
// P Cygni           | B1Ia+   | 18,000 | 76     | 6×10^5    | Prototype LBV
// VY Canis Majoris  | M5Ia    | 3,490  | 1,420  | 3×10^5    | Formerly largest known star
// UY Scuti          | M4Ia    | 3,365  | 1,708  | 340,000   | Among the largest by radius
// Stephenson 2-18   | M7Ia    | 3,200  | ~2,150 | ~630,000  | Possibly largest known
// R136a1            | WN5h    | 46,000 | 35     | 8.7×10^6  | Most luminous known star
// Pistol Star       | LBV/B?  | 11,800 | 420    | 1.6×10^6  |
//
// Note: "largest star" is contested due to parallax and variability uncertainty.
// For procgen: cap reliable SUPERGIANT radius at 2,000 R☉.
// R > 2,000 R☉ is astrophysically possible but effectively never exists at game scales.
```

---

## Special & Variable Stars

```
// WOLF-RAYET (WR)
//   Type: stripped massive star exposing core; very hot, fast stellar wind (2000–3000 km/s)
//   T: 25,000–100,000 K   R: 1–20 R☉   M: 10–40 M☉ (post-mass-loss)
//   Appear blue-violet; emit strong emission lines
//
// T TAURI (pre-main-sequence)
//   T: 3,500–7,000 K   R: 0.8–4 R☉   Age < 10 Myr   Variable (irregular)
//   Strong X-ray emission; disk accretion; many Herbig-Haro objects nearby
//
// CEPHEID VARIABLES
//   Pulsating supergiants (F–K, luminosity class Iab–II)
//   Period–Luminosity: log(P/days) = a – b × log(L/L☉)  (Leavitt Law)
//   Period: 1–100 days   Amplitude: 0.5–2 mag
//   L range: 300–30,000 L☉   R range: 10–400 R☉
//   Use as standard candles to ~100 Mpc
//
// RR LYRAE
//   Pulsating horizontal-branch stars; all near L = 40–50 L☉
//   Period: 0.2–1 day   Type: A–F   R: 4–6 R☉   T: 6,000–7,600 K
//   Found in globular clusters; standard candle to ~1 Mpc
//
// MIRA VARIABLES (Long-Period Variables)
//   AGB pulsating red giants; period 80–1,000 days; amplitude up to 8 mag
//   R varies: 200–700 R☉ per cycle   Mass loss: 10^-6 M☉/yr → planetary nebula
//
// FLARE STARS (UV Ceti type)
//   M-dwarf; occasional X-ray/UV flares (1000× increase in minutes)
//   Hazardous for life in habitable zone
```

---

## Solar System: Terrestrial Planets

```
// Body     | R (km)  | M (M_E)  | g (m/s²)| P_rot   | P_orb  | Atm           | Moons
// ──────────────────────────────────────────────────────────────────────────────────
// Mercury  | 2,439   | 0.0553   | 3.70    | 58.6d   | 88d    | trace Na/O    | 0
// Venus    | 6,052   | 0.8150   | 8.87    | −243d   | 225d   | 93 bar CO₂   | 0
// Earth    | 6,371   | 1.0000   | 9.807   | 23.9h   | 365d   | 1 bar N₂/O₂  | 1
// Moon     | 1,737   | 0.01230  | 1.62    | 27.3d   | —      | none          | —
// Mars     | 3,390   | 0.1074   | 3.72    | 24.6h   | 687d   | 6 mbar CO₂   | 2
// Ceres    | 473     | 0.000157 | 0.27    | 9.07h   | 4.6yr  | none          | 0
//
// Venus note: retrograde rotation; surface T = 462°C; longest sidereal day in SS
// Mars note: thin atmosphere; seasonal CO₂ polar cap sublimation; Phobos decaying orbit
```

---

## Solar System: Giant Planets & Ice Giants

```
// Body     | R (km)  | M (M_E)  | g (m/s²)| P_rot  | P_orb  | Rings | Moons
// ──────────────────────────────────────────────────────────────────────────────────
// Jupiter  | 71,492  | 317.83   | 24.79   | 9.93h  | 11.9yr | faint | 95
// Saturn   | 60,268  | 95.16    | 10.44   | 10.7h  | 29.5yr | MAJOR | 146
// Uranus   | 25,559  | 14.54    | 8.87    | −17.2h | 84yr   | thin  | 28
// Neptune  | 24,622  | 17.15    | 11.15   | 16.1h  | 165yr  | thin  | 16
//
// Saturn's ring system:
//   D ring:  inner edge  66,900 km from centre
//   A ring:  outer edge 136,775 km from centre
//   Roche limit for ice: ~148,000 km
//   F ring (shepherd):   140,220 km from centre
//   E ring (diffuse):    extends to 480,000 km
//   Total system width from cloud tops: ~70,000 km (A+B+C rings, main system)
//
// Jupiter's Galilean moons: see Large Moons section
// Uranus axial tilt: 97.77° (essentially rolling along orbit)
// Neptune's large moon Triton: retrograde orbit, will disintegrate in ~3.6 Gyr
```

---

## Dwarf Planets & Trans-Neptunian Objects

```
// Body       | R (km)  | M (M_E)    | a (AU)  | e     | Note
// ──────────────────────────────────────────────────────────────────────────────
// Pluto      | 1,188   | 0.00218    | 39.5    | 0.249 | Binary with Charon; N₂ ice
// Eris       | 1,163   | 0.00277    | 67.9    | 0.442 | Most massive known dwarf planet
// Makemake   | 715     | ~0.00055   | 45.8    | 0.159 | Red surface; no atmosphere detected
// Haumea     | 780*    | 0.00067    | 43.1    | 0.195 | *prolate: 1060×840×537 km; ring
// Sedna      | ~500    | ~0.0002    | 506     | 0.845 | Aphelion 937 AU; inner Oort Cloud?
// 2018 AG37  | ~220    | —          | 132     | 0.22  | Farthest known SS body at discovery
// Quaoar     | 556     | 0.00034    | 43.4    | 0.040 | Ring outside Roche limit (unusual)
// Orcus      | 473     | 0.00011    | 39.3    | 0.225 | Neptune resonance 2:3
// Gonggong   | 615     | ~0.0005    | 67.3    | 0.497 | Red surface; methanol ice
//
// Kuiper Belt: 30–50 AU, ~100,000 objects > 100 km
// Scattered Disc: 50–100+ AU, high eccentricity
// Detached objects: perihelia > 40 AU, no Neptune influence
// Inner Oort Cloud: ~2,000–20,000 AU
// Outer Oort Cloud: 20,000–100,000 AU (1.6 LY), ~10^12 objects
```

---

## Large Moons — Solar System & Archetypes

```
// Moon       | Parent  | R (km)  | M (M_E)  | Orbital a (km) | Note
// ──────────────────────────────────────────────────────────────────────────────
// Ganymede   | Jupiter | 2,634   | 0.02482  | 1,070,400      | Largest moon in SS; >Mercury
// Titan      | Saturn  | 2,575   | 0.02226  | 1,221,900      | N₂ atmosphere 1.45 bar; hydrocarbon lakes
// Callisto   | Jupiter | 2,410   | 0.01799  | 1,882,700      | Ancient cratered; subsurface ocean?
// Io         | Jupiter | 1,822   | 0.01496  | 421,700        | Most volcanically active body known
// Moon       | Earth   | 1,737   | 0.01230  | 384,400        | Largest relative to parent
// Europa     | Jupiter | 1,561   | 0.00804  | 671,100        | Subsurface liquid ocean; highest life potential
// Triton     | Neptune | 1,354   | 0.00358  | 354,800        | Retrograde; tidal heating; nitrogen geysers
// Titania    | Uranus  | 789     | 0.000587 | 436,300        | Largest Uranian moon
// Rhea       | Saturn  | 764     | 0.000339 | 527,100        | Thin O₂ atmosphere
// Oberon     | Uranus  | 761     | 0.000517 | 583,500        |
// Iapetus    | Saturn  | 735     | 0.000303 | 3,561,300      | Two-tone: one dark, one bright hemisphere
// Charon     | Pluto   | 606     | 0.000260 | 19,600         | Mass ratio 0.12; mutual tidal lock
// Ariel      | Uranus  | 579     | 0.000228 | 191,200        | Youngest-surface Uranian moon
// Umbriel    | Uranus  | 585     | 0.000196 | 266,000        | Darkest Uranian moon
// Dione      | Saturn  | 561     | 0.000185 | 377,400        | Ice cliffs; water vapour exosphere
// Tethys     | Saturn  | 533     | 0.000108 | 294,700        | Ithaca Chasma: 2000 km long rift
// Enceladus  | Saturn  | 252     | 0.0000180| 238,000        | Active water geysers; subsurface ocean
// Miranda    | Uranus  | 236     | 0.0000088| 129,900        | Verona Rupes: 20 km cliff
// Proteus    | Neptune | 210     | —        | 117,600        | Non-spherical (too small for hydrostatic)
// Hyperion   | Saturn  | 135×ell | —        | 1,481,100      | Chaotic rotation; sponge-like
// Phoebe     | Saturn  | 107     | —        | 12,952,000     | Irregular; retrograde; captured KBO
// Phobos     | Mars    | 11.3    | —        | 9,376          | Decaying orbit; will crash in ~50 Myr
// Deimos     | Mars    | 6.2     | —        | 23,458         | Will escape Mars gravity eventually
```

---

## Asteroids & Small Body Families

```
// ASTEROID BELT FAMILIES
// ──────────────────────────────────────────────────────────────────────────
// Body          | R (km) | a (AU) | Type  | Note
// ──────────────────────────────────────────────────────────────────────────
// Ceres         | 473    | 2.77   | C     | Dwarf planet; water ice mantle
// Vesta         | 265    | 2.36   | V     | Differentiated; HED meteorite parent
// Pallas        | 256    | 2.77   | B     | Primitive; highly inclined (34.8°)
// Hygiea        | 217    | 3.14   | C     | 4th largest; possible Ceres lookalike
// Interamnia    | 165    | 3.06   | F     |
// Europa (ast.) | 152    | 3.10   | C     |
// Davida        | 145    | 3.18   | C     |
// Sylvia        | 143    | 3.49   | X     | Has 2 small moons (Romulus, Remus)
// Eunomia       | 128    | 2.64   | S     | Largest S-type
// Bamberga      | 116    | 2.68   | C     |
// Largest NEO:  | ~30    | varies |       | (433 Eros: 33×13×13 km, S-type)
//
// ASTEROID SIZE DISTRIBUTION (approximate)
// >100 km:   ~200 bodies      >10 km:   ~10,000        >1 km:  ~1,000,000
// >100 m:  ~25,000,000        >10 m: ~1,000,000,000    Total mass: ~3% Moon
//
// NEAR-EARTH ASTEROID FAMILIES
// Aten:  a < 1 AU, Q > 0.983 AU (cross Earth)
// Apollo: a > 1 AU, q < 1.017 AU (cross Earth)
// Amor:  a > 1 AU, 1.017 < q < 1.3 AU (approach)
// Atira: Q < 0.983 AU (entirely inside Earth's orbit)
//
// Trojan asteroids: librate at L4/L5 of Jupiter; >9,800 known > 1 km
// Hildas: 3:2 resonance with Jupiter at ~3.97 AU; ~5,000 known
```

---

## Comets

```
// COMET NUCLEUS SIZE RANGE
// ──────────────────────────────────────────────────────────────────────────
// Tiny comets:         0.1–1 km    (most short-period comets)
// Typical:             1–10 km     (Halley: 15×8 km; 67P: 4.1×3.2 km)
// Large:               10–50 km    (Hale-Bopp: ~60 km; C/2014 UN271 (Bernardinelli-Bernstein): 137 km)
// Very large / centaur: 50–250 km  (Chiron: 210 km; Chariklo: 248 km — has rings!)
//
// COMET ANATOMY
// Component         | Size               | Note
// ──────────────────────────────────────────────────────────────────────────
// Nucleus           | 0.1–137 km         | Dirty ice, rock, organics; bulk density ~0.4–0.6 g/cm³
// Coma              | 1,000–1,000,000 km | Gas/dust; largest transient structure in SS
// Hydrogen envelope | up to 10,000,000 km| Lyman-α emission; invisible to naked eye
// Dust tail         | up to 100,000,000 km| Curved; points away + slightly behind orbit
// Ion tail          | up to 200,000,000 km| Straight; always points directly away from Sun
//
// FAMOUS COMETS (reference data)
// 1P/Halley:          nucleus 15×8 km; period 75.3 yr; q = 0.586 AU
// C/1995 O1 Hale-Bopp: nucleus ~60 km; period ~2,520 yr; brightest of 20th century
// 67P/Churyumov-Gerasimenko: 4.1×3.2 km; period 6.44 yr; Rosetta mission target
// C/2020 F3 NEOWISE: ~5 km; naked-eye July 2020; period ~6,800 yr
```

---

## Ring Systems

```
// All known ring systems (as of 2024):
// Body        | System extent (km from centre) | Note
// ──────────────────────────────────────────────────────────────────────────
// Saturn      | 67,000–480,000                 | Most massive and complex; 1 km thick
// Uranus      | 38,000–98,000                  | 13 narrow rings; dark; discovered 1977
// Neptune     | 42,000–63,000                  | 5 rings; arcs (Adams ring has 3 arcs)
// Jupiter     | 92,000–225,000                 | Faint dust rings; Io/Europa source
// Chariklo    | ~391 km, two rings             | Largest known centaur; first non-planet ring
// Quaoar      | ~4,100 km from centre          | KBO; outside Roche limit — puzzling
// Haumea      | ~2,287 km from centre          | 70 km wide ring
// Chiron?     | Tentative; centaur             | May have rings or coma-related arcs
//
// Ring stability: material inside Roche limit = rings; outside = moons
// Saturn Roche limit (ice): 140,000 km from centre (matches ring outer edge closely)
// Ring thickness: Saturn B ring ~5–15 m; A ring ~10–30 m (very flat!)
```

---

## Terrestrial Exoplanet Types

```
// Category         | R (R_E)  | M (M_E)  | Example             | Note
// ──────────────────────────────────────────────────────────────────────────
// Iron planet      | 0.3–0.7  | 0.5–2    | (theoretical)        | >70% Fe core; no mantle
// Rocky sub-Earth  | 0.3–0.8  | 0.1–0.5  | Kepler-20e           | Mostly rock
// Earth analogue   | 0.8–1.3  | 0.6–2.0  | Kepler-452b, K2-18b  | Habitable zone
// Super-Earth rocky| 1.3–1.7  | 2–8      | 55 Cnc e, Kepler-10b | May have steam/CO₂ atm
// Waterworld       | 1.5–2.5  | 2–10     | K2-18b candidate     | > 50% water by mass
// Hycean world     | 1.2–2.5  | 2–12     | (emerging category)  | Ocean under H₂ atmosphere
// Lava planet      | 0.8–1.5  | 0.5–3    | 55 Cnc e, Kepler-10b | Surface temp > 2000 K
// Mini-Neptune     | 1.7–3.5  | 5–20     | Kepler-11 planets    | Gaseous envelope; no surface
// Carbon planet    | 0.8–1.5  | 0.5–3    | (theoretical)        | Graphite/diamond crust
// Ocean-iron       | 0.5–1.2  | 0.3–3    | (theoretical)        | Dense iron + deep ocean
```

---

## Giant Exoplanet Types

```
// Category          | R (R_J) | M (M_J)  | a (AU)    | Example            | Note
// ──────────────────────────────────────────────────────────────────────────────────
// Hot Jupiter        | 1.0–2.0 | 0.3–13   | 0.01–0.1  | 51 Peg b, WASP-12b | T_day > 2000K
// Ultra-hot Jupiter  | 1.3–2.5 | 0.5–10   | 0.02–0.05 | KELT-9b (T=4050K!) | Metal vapour atm
// Warm Jupiter       | 0.9–1.5 | 0.3–10   | 0.1–1.0   | HAT-P-6b           |
// Cold Jupiter (SS)  | 0.9–1.2 | 0.5–5    | 4–10      | Jupiter, Saturn     |
// Inflated/Puffy Jup | 1.5–2.5 | 0.1–1    | 0.02–0.1  | WASP-79b (2.35 R_J)| Low density; mystery
// Super-Jupiter      | 1.0–2.0 | 3–13     | 0.1–10    | Kappa And b         | Near brown dwarf
// Mini-Neptune/Sub-Nept| 0.3–0.5| 0.05–0.1| 0.05–2    | GJ 436b             | Ice giant scale
// Eccentric giant    | 0.8–1.5 | 0.5–10   | 0.2–5     | HD 80606b (e=0.93) | HD 80606b: T swing 800K
// Rogue              | 0.8–1.5 | 0.5–13   | —         | PSO J318.5         | No parent star
```

---

## Stellar System Architectures

```
// BINARY STAR CLASSIFICATIONS
// Visual binary:   resolved with telescope; periods years to millennia; a > 10 AU typical
// Spectroscopic:   inferred from Doppler shift; a typically 0.01–10 AU
// Eclipsing:       orbit seen edge-on; measured radii and masses directly
// Contact binary:  stars share envelope; period < 1 day; W UMa type
//
// Separation → period (Kepler):  P² = a³ / M_total   (P in yr, a in AU, M in M☉)
//   a=0.01 AU → P≈1 hr    a=0.1 AU → P≈10 days    a=1 AU → P≈1 yr
//   a=10 AU → P≈32 yr     a=100 AU → P≈1000 yr
//
// MULTI-STAR STABILITY ZONES (circumbinary planets need a > 3–5× binary sep)
// S-type (around one star): stable if a < 0.3–0.5× binary separation
// P-type (around both stars): stable if a > 2–5× binary separation
//
// STELLAR MULTIPLICITY (approximate):
// Single:  45% of FGK stars
// Binary:  46% (some surveys: up to 60% for massive O/B stars)
// Triple:  8%
// Higher:  1%
//
// HABITABLE ZONE FORMULAE (using stellar luminosity L in L☉)
// Inner (optimistic):  0.75 × √L  AU   (runaway greenhouse)
// Inner (conservative): 0.95 × √L  AU  (moist greenhouse)
// Outer (conservative): 1.37 × √L  AU  (maximum greenhouse)
// Outer (optimistic):   1.70 × √L  AU  (early Mars equivalent)
```

---

## Circumstellar Structures

```
// PROTOPLANETARY DISKS (PPDs)
// ──────────────────────────────────────────────────────────────────────────
// Radius:        10–1000 AU (typically 100–300 AU; HL Tau: 120 AU with rings)
// Mass:          0.001–0.1 M☉ (dust: 1–1000 M_Earth; gas: 100× more)
// Lifetime:      1–10 Myr for gas; dust up to 100 Myr
// Gap formation: planets open gaps as low as 0.1 M_J
//
// DEBRIS DISKS (post-PPD)
// Fomalhaut:     inner edge 133 AU; outer edge 158 AU; narrow (like Saturn's A ring)
// Vega:          extends to ~330 AU; warmer than expected
// HR 4796A:      ring at 77 AU; 7 AU wide; very reflective
//
// ASTEROID BELT ANALOGUES (exo-zodiacal dust, inferred)
// Detectable with warm Spitzer/WISE around hundreds of nearby stars
//
// STELLAR WIND BUBBLE (Heliosphere analogue)
// Termination shock: ~85 AU (Voyager 1 crossed at 94 AU in 2004)
// Heliopause:         ~120 AU (Voyager 1 at 2012, now 158 AU, still in tail)
// Bow shock/heliotail: extends 300–1000 AU downstream
```

---

## Nebulae

```
// PLANETARY NEBULAE (PN)
// Formed from AGB star mass loss; illuminated by remnant white dwarf
// Radius:    0.1–3 LY    Expansion:  5–40 km/s    Lifetime: ~20,000 yr
// Example: Ring Nebula (M57): 1.0 LY across, 2,300 LY away, WD T=120,000K at centre
// Example: Helix Nebula (NGC 7293): 6.5 LY across, 700 LY away (largest apparent size in sky)
// Example: Dumbbell (M27): 2.5 LY across, 1,360 LY away
//
// EMISSION / HII REGIONS
// Ionised H clouds surrounding young O/B stars
// Radius:   2–200 LY    T = 8,000–10,000 K    Density: 10–10,000 cm⁻³
// Orion Nebula (M42): 24 LY across, 1,344 LY away, 2,000 stars forming
// Carina Nebula: 300 LY across, 7,500 LY away, 10,000+ stars (incl. Eta Car)
// Eagle Nebula (M16/Pillars of Creation): 70 LY across, 5,700 LY away
// Lagoon Nebula (M8): 110 LY across, 4,100 LY away
//
// SUPERNOVA REMNANTS (SNR)
// Crab Nebula (M1): 11 LY across; expanding 1,500 km/s; pulsar at centre; 6,500 LY away
//   (observed SN in 1054 CE; now 970 yr old)
// Cassiopeia A: 10 LY across, 11,000 LY away; hottest known SNR x-ray
// Veil Nebula (Cygnus Loop): 110 LY across; ~8,000 yr old; 2,100 LY away
//
// DARK NEBULAE (molecular clouds)
// Bok globule:    0.1–2 LY    Mass: 1–100 M☉    T: 10–30 K    Forming single stars
// Giant MC:       50–600 LY   Mass: 10^4–10^7 M☉  Star-forming complexes
// Horsehead (B33): 3.5 LY tall; 1,375 LY away; dark against IC 434 emission
// Pillars of Creation: each ~4–5 LY long; evaporating globules (EGGs)
//
// REFLECTION NEBULAE
// Dust reflecting light of nearby (not ionising) star; blue colour
// Merope Nebula (NGC 1435): near Pleiades, 440 LY away
// Radius: 1–30 LY typical
//
// DIFFUSE / LARGE-SCALE NEBULAE
// Orion Molecular Cloud: 500 LY across; 1,500 LY away; multiple HII regions embedded
// California Nebula: 100 LY long; resembles state in shape
```

---

## Star Clusters

```
// OPEN CLUSTERS
// ──────────────────────────────────────────────────────────────────────────
// Cluster       | R (LY)  | Stars  | Age (Myr) | Distance (LY) | Note
// ──────────────────────────────────────────────────────────────────────────
// Pleiades (M45)| 17      | ~1,000 |   115     |   440         | Blue supergiants still
// Hyades        | 18      | ~400   |   625     |   153         | Nearest open cluster to Sun
// Beehive (M44) | 15      | ~1,000 |   730     |   577         | Praesepe
// Alpha Persei  | 20      | ~300   |    50     |   557         |
// NGC 2516      | 25      | ~1,200 |   141     | 1,300         |
// Westerlund 1  | 5       | ~100,000 | 3.5     | 16,000        | Extreme massive cluster; multiple WR, sg
// Open clusters: R = 5–50 LY; loose, gravitationally weakly bound; disrupt in ~1 Gyr
//
// GLOBULAR CLUSTERS
// ──────────────────────────────────────────────────────────────────────────
// Cluster           | R (LY) | Stars     | Age (Gyr) | Distance (kLY) | Note
// ──────────────────────────────────────────────────────────────────────────
// 47 Tucanae (47Tuc)| 120    | ~2M       | 13.06     |   16.7         | Bright, dense; 300+ stars/pc³
// Omega Centauri    | 150    | ~10M      | 11.5      |   17.3         | Largest in MW; possibly former galaxy
// M22 (NGC 6656)    | 50     | ~70,000   | 12.0      |   10.4         |
// M13 (Hercules GC) | 84     | ~300,000  | 11.65     |   22.2         | Most famous NGC
// M3 (NGC 5272)     | 90     | ~500,000  | 11.39     |   33.9         |
// Globulars: R = 30–300 LY; old (10–13 Gyr); 150 known orbiting Milky Way; halo population
// Core density in rich GC: 10^5–10^6 M☉/pc³ (vs 0.04 M☉/pc³ near Sun)
```

---

## Galaxies — Types & Sizes

```
// HUBBLE CLASSIFICATION
// E0–E7 (Elliptical): spherical to highly elliptical; old stars; little gas/dust
// S0 (Lenticular):    disk + bulge; little gas; between E and S
// Sa–Sd (Spiral):     arms; Sa = tightly wound, Sd = loose
// SBa–SBd (Barred):   bar through centre; most spirals (~70%) are barred
// Irr (Irregular):    no clear structure; often interacting or dwarf
//
// GALAXY SIZE REFERENCE TABLE
// Galaxy             | Type  | R (kLY) | Stars (×10⁹) | Distance    | Note
// ──────────────────────────────────────────────────────────────────────────
// Segue 1            | dSph  | 0.9     | 0.0000003    |  75 kLY     | Faintest known dwarf
// Canis Major Dwarf  | Irr   | ~5      | ~1           |  25 kLY     | Closest known galaxy (tidal stream)
// Sagittarius Dw.    | dSph  | 10      | 1.0          |  65 kLY     | Being disrupted by MW
// LMC                | SBm   | 14,000  | 30           | 160 kLY     | Largest Magellanic Cloud
// SMC                | Irr   | 7,000   | 7            | 200 kLY     | Small Magellanic Cloud
// IC 1101            | E/S0  | ~2M     | ~100,000     | 1.07 BLY    | Largest known galaxy
// Milky Way          | SBbc  | 105,000*| 200–400      | (we're in it)| *disk radius; halo to ~300 kLY
// Andromeda (M31)    | Sb    | 110,000 | 1,000        | 2.54 MLY    | Largest in Local Group
// Triangulum (M33)   | Sc    | 30,000  | 40           | 2.73 MLY    | 3rd largest Local Group
// NGC 1300           | SBb   | 100,000 | —            | 69 MLY      | Classic barred spiral
// Milkomeda (future) | —     | ~160,000| ~1,400       | (MW+M31 merger ~4.5 Gyr)
// Alcyoneus          | FRII  | 16.3 MLY| —            | 3 BLY       | Largest known single galaxy
//
// *Milky Way disc radius revised to ~52 kpc = 170 kLY in some surveys; game uses 105 kLY (32 kpc)
//
// GALAXY SIZE STATISTICS (approximate)
// Dwarf elliptical/spheroidal: R = 0.3–30 kLY, 10^5–10^8 M☉
// Large elliptical: R = 50–500 kLY, 10^11–10^13 M☉
// Typical spiral: R = 10–100 kLY, 10^9–10^12 M☉
// Giant elliptical (BCG): R = 200–2000 kLY, 10^12–10^14 M☉
```

---

## Galaxy Groups, Clusters & Large-Scale Structure

```
// LOCAL GROUP (~3 MLY across; 50+ members; MW + M31 dominant)
// Virgo Cluster: ~8 MLY radius; ~1,300 galaxies; 65 MLY away; mass ~10^15 M☉
// Fornax Cluster: 2 MLY radius; ~58 bright galaxies; 62 MLY away
// Coma Cluster: 10 MLY radius; ~1,000 bright galaxies; 321 MLY away; early dark matter evidence
//
// SUPERCLUSTERS
// Virgo Supercluster (Laniakea): 520 MLY diameter; 100,000 galaxies; 10^17 M☉
// Perseus-Pisces: 300 MLY long; filament structure
// Shapley Supercluster: 650 MLY across; massive attractor for local universe
// Sloan Great Wall: 1.38 BLY long (filament); 1 BLY away
// Hercules-Corona Borealis Great Wall: 10 BLY long; largest known structure
//
// COSMIC VOID SIZES
// Typical: 30–300 MLY diameter; near-empty; 1–10 galaxies/Mpc³ (vs 10,000 in filaments)
// Local Void: 150–300 MLY radius (pushes Local Group at ~270 km/s)
// Boötes Void: 250 MLY across; 700 MLY away; extremely underdense
//
// OBSERVABLE UNIVERSE
// Comoving radius:  46.5 BLY  (light travel distance is 13.8 BLY but universe expanded)
// Diameter:         93 BLY
// ~2 trillion galaxies total (revised estimate 2016)
// ~10^24 stars total in observable universe
```

---

## Active Galactic Nuclei & Exotic High-Energy Objects

```
// QUASAR / QSO (Quasi-Stellar Object)
// Core of galaxy with actively accreting SMBH; most luminous persistent objects
// Luminosity: 10^39–10^41 W (10^13–10^15 L☉); outshine entire host galaxy
// Accretion disk: 0.001–1 LY across
// Relativistic jets: 100,000 LY long (some > 1 MLY in radio)
// Most distant: z ≈ 7.5 (GJ-1342+0928; light from 700 Myr after Big Bang)
// Nearest: Markarian 231 (600 MLY; nearest known quasar)
//
// SEYFERT GALAXIES (lower-luminosity AGN; galaxy still visible)
// Seyfert 1: broad+narrow emission; near face-on jet; variable
// Seyfert 2: narrow lines only; obscured; torus blocks broad-line region
// Luminosity: 10^36–10^39 W (10^10–10^13 L☉)
//
// RADIO GALAXIES
// Powerful jets producing synchrotron radio emission; giant ellipticals
// FRII type: edge-brightened lobes; jets > 100 kLY; largest are mega-parsec scale
// Cygnus A: jets 300 kLY; 600 MLY away; among brightest radio sources in sky
//
// BLAZARS (jet pointed at us)
// BL Lac objects & OVV quasars; extreme variability; apparent FTL jet motion
// Mrk 421 (400 MLY): nearest BL Lac; varies on timescales of minutes
//
// GAMMA-RAY BURSTS (GRB)
// Short GRB (< 2 s): neutron star mergers; kilonova; r-process heavy elements
// Long GRB (2 s – minutes): core collapse of massive star (collapsar)
// Peak luminosity: ~10^44–10^47 W for ~0.1–100 s (rivals entire observable universe)
// Afterglow: X-ray to radio; days to years; detects at any redshift
// Closest confirmed: GRB 030329 at z=0.1685 (2.7 BLY)
//
// MAGNETARS
// Neutron star with B field ~10^10–10^11 T (10^14–10^15 Gauss)
// (Earth's field: 0.00005 T; MRI scanner: 1.5–7 T)
// Soft Gamma Repeater (SGR): burst of gamma rays lasting < 1 s
// SGR 1806-20: 2004 burst released 10^39 J in 0.2 s (200× more than Sun in 1 week)
// Rotation period: 2–12 seconds (slow for neutron star)
```

---

## Planet-Scale Terrain Constraints

```java
public class TerrainScaleConstraints {

    // Maximum mountain height fraction of planet radius (isostatic limit)
    // Earth: Everest 8.85 km / 6371 km = 0.00139
    // Mars:  Olympus Mons 21.9 km / 3390 km = 0.00645 (lower gravity)
    public static float maxMountainFraction(float surfaceGravityMs2) {
        return 0.0014f * (9.807f / surfaceGravityMs2);
    }

    public static final float OCEAN_DEPTH_FRACTION    = 0.0018f; // Mariana: 11/6371
    public static final float EARTH_SCALE_HEIGHT_KM   = 8.5f;
    public static final float KARMAN_LINE_SCALE_HEIGHTS = 12f;   // ~100 km for Earth

    public static float scaleHeightKm(float tempK, float gravMs2, float molarMassKgMol) {
        return (8.314f * tempK) / (molarMassKgMol * gravMs2) / 1000f;
    }

    public static float atmosphereVisibleKm(float scaleHeightKm) {
        return scaleHeightKm * 5.5f; // 99.3% of atmospheric mass within 5.5 scale heights
    }
}
```

---

## Ship & Structure Size Ranges

```java
public class GameStructureSizes {
    // Ship hull lengths in METRES (authoritative game ranges)
    public static final float SHUTTLE_M_MIN = 5;      public static final float SHUTTLE_M_MAX = 18;
    public static final float FIGHTER_M_MIN = 10;     public static final float FIGHTER_M_MAX = 30;
    public static final float CORVETTE_M_MIN = 40;    public static final float CORVETTE_M_MAX = 90;
    public static final float FRIGATE_M_MIN = 100;    public static final float FRIGATE_M_MAX = 250;
    public static final float DESTROYER_M_MIN = 250;  public static final float DESTROYER_M_MAX = 600;
    public static final float CRUISER_M_MIN = 600;    public static final float CRUISER_M_MAX = 1_800;
    public static final float BATTLECRUISER_M_MIN = 1_800; public static final float BATTLECRUISER_M_MAX = 4_500;
    public static final float BATTLESHIP_M_MIN = 4_500;    public static final float BATTLESHIP_M_MAX = 14_000;
    public static final float CARRIER_M_MIN = 3_500;       public static final float CARRIER_M_MAX = 18_000;
    public static final float DREADNOUGHT_M_MIN = 12_000;  public static final float DREADNOUGHT_M_MAX = 55_000;
    public static final float FREIGHTER_M_MIN = 80;        public static final float FREIGHTER_M_MAX = 800;

    // Station diameters in METRES
    public static final float OUTPOST_M_MIN = 20;           public static final float OUTPOST_M_MAX = 100;
    public static final float TRADING_POST_M_MIN = 100;     public static final float TRADING_POST_M_MAX = 500;
    public static final float WAYSTATION_M_MIN = 400;       public static final float WAYSTATION_M_MAX = 1_500;
    public static final float STARPORT_M_MIN = 1_200;       public static final float STARPORT_M_MAX = 6_000;
    public static final float SHIPYARD_M_MIN = 3_000;       public static final float SHIPYARD_M_MAX = 15_000;
    public static final float BATTLE_STATION_M_MIN = 5_000; public static final float BATTLE_STATION_M_MAX = 50_000;
    public static final float MIN_1G_RING_RADIUS_M = 225;   // ω ≤ 2 rpm (motion sickness limit)

    // City radii in METRES
    public static final float OUTPOST_CITY_M = 80;
    public static final float FRONTIER_TOWN_M = 400;
    public static final float COLONY_M = 1_500;
    public static final float REGIONAL_HUB_M = 8_000;
    public static final float METROPOLIS_M = 60_000;

    // Building heights in METRES
    public static final float FLOOR_HEIGHT_GROUND_M = 4.5f;
    public static final float FLOOR_HEIGHT_UPPER_M  = 3.2f;
    public static final float FLOOR_HEIGHT_MECH_M   = 2.0f;
    public static final float MAX_SKYSCRAPER_M       = 500f;
}
```

---

## Biological Size Ranges

```java
public class BiologicalSizes {
    // Creature heights in METRES
    public static final float TINY_MAX_M    = 0.30f;
    public static final float SMALL_MAX_M   = 0.80f;
    public static final float MEDIUM_MAX_M  = 2.50f;
    public static final float LARGE_MAX_M   = 8.00f;
    public static final float HUGE_MAX_M    = 25.0f;
    public static final float MEGA_MAX_M    = 80.0f;

    // Maximum height governed by gravity (compressive limit of bone/chitin analogue)
    public static float maxHeightM(float gravityMs2) {
        return 30f * (float) Math.pow(9.807f / gravityMs2, 0.7f);
    }

    // Mass estimate: volume ≈ 0.05 × h³; density 800–1200 kg/m³
    public static double massKg(float heightM, float density) {
        return 0.05 * Math.pow(heightM, 3) * density;
    }

    // Vegetation heights in METRES
    public static final float GRASS_MAX_M       = 2.5f;
    public static final float SHRUB_MAX_M       = 4.0f;
    public static final float TREE_MAX_M        = 120.0f;
    public static final float ALIEN_SPIRE_MAX_M = 200.0f;
}
```

---

## LOD Distance Calibration

```java
public class LODCalibration {

    // At 1920×1080 with 90° horizontal FOV: 0.00082 rad/pixel
    public static final float RAD_PER_PIXEL = 0.00082f;

    // Switch LOD when angular diameter drops below threshold:
    public static float lodSwitch(float objectDiameterM, float thresholdPx) {
        return objectDiameterM / (thresholdPx * RAD_PER_PIXEL);
    }

    // Standard thresholds (pixels of apparent diameter)
    // Object size     LOD0→1     LOD1→2     LOD2→3(billboard)  Cull
    // 0.02 m (grass)  0.12 m     1.2 m       6 m               24 m
    // 0.5 m (shrub)   3 m        31 m        152 m             610 m
    // 2 m (shrub)     12 m       122 m       610 m             2.4 km
    // 10 m (building) 61 m       610 m       3.0 km            12 km
    // 100 m (tower)   610 m      6.1 km      30 km             122 km
    // 65 m (corvette) 397 m      4.0 km      20 km             79 km
    // 9000 m (BB)     55 km      549 km      2,743 km          10,976 km
    // 1000 km (moon)  6,098 km   60,980 km   304,878 km        — (orbital cam)
}
```
