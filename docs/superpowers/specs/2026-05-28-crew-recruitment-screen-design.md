# Crew Recruitment Screen Design

**Date:** 2026-05-28
**Status:** Approved

## Overview

A cantina-scene recruitment screen where the player browses recruitable NPCs placed in an atmospheric station environment. Each NPC appears as a clickable portrait in the scene with a name tag showing species, role, and top stats. Clicking an NPC slides up a detail overlay with partial stat reveal, a quote, and wage range. A "Talk" button transitions into the existing DialogSystem for full recruitment dialog — backstory exploration, hidden stat reveal, wage negotiation, and condition discovery. A Hiring Board object in the scene provides an alternate list/filter view for efficient browsing. This screen is recruitment-only; crew management is a separate screen.

---

## 1. Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Nexus Station — Lower Deck Cantina      ⬡ 12,450 cr       │
│  4 candidates · 2/6 crew slots           Payroll: 900 cr/wk│
│                                                             │
│        ┌──────┐                                             │
│        │HIRING│         ┌──────┐                            │
│        │BOARD │         │  🦎  │                            │
│   ┌──────┐    │         │Threx │     ┌──────┐              │
│   │  👤  │    │         │ -Ka  │     │  👤  │    ┌──────┐  │
│   │ Kira │    └──────┘  └──────┘     │ Orin │    │  🪨  │  │
│   │ Voss │                           │ Mael │    │Garak │  │
│   └──────┘                           └──────┘    │ Durn │  │
│  ─────────────bar counter────────────             └──────┘  │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🦎 Threx-Ka  Veloxi · Engineer · Specialist    550-650 │ │
│ │ "Former shipyard tech. Left after the fleet recall."    │ │
│ │ REP 85  SCI 68  PIL ???  CMB ???                        │ │
│ │                              [💬 Talk]  [✕ Dismiss]     │ │
│ └─────────────────────────────────────────────────────────┘ │
│ [ESC] Leave Cantina                                         │
└─────────────────────────────────────────────────────────────┘
```

- **Header bar**: Location name, candidate count, crew slots (filled/max), player credits, weekly payroll cost.
- **Scene area**: Background image or gradient with ambient elements (lights, bar counter, atmospheric detail). NPC portraits placed at authored positions per station. Hiring Board is a clickable scene object.
- **Detail overlay**: Slides up from the bottom when an NPC is selected. Shows portrait, name, species, role, rank, partial stats (top 2-3 visible, rest as ???), one-line quote, wage range, and Talk/Dismiss buttons.
- **Footer**: ESC keybinding hint.

---

## 2. Interaction Flow

### State Machine

The screen has 5 states:

```
BROWSE → SELECTED → DIALOG → OFFER → RESULT
  ↑         │          │        │        │
  └─────────┘──────────┘────────┘────────┘
  (Dismiss/Leave/Decline all return to BROWSE)
```

### State Details

**BROWSE**: NPCs visible in scene with name tags. Mouse-over highlights the hovered NPC (border glow). Clicking an NPC transitions to SELECTED. Clicking the Hiring Board opens the list overlay. ESC leaves the cantina.

**SELECTED**: Clicked NPC gains a green glow. Detail overlay slides up from the bottom with a ~200ms tween. Overlay contains:
- Portrait (circular, 56px), name, species, role, rank
- One-line quote/hook from NPC data
- Partial stats: top 2–3 stats by value are shown with bars; remaining stats show "???"
- Wage range (min–max from `RecruitableComponent`)
- Two buttons: "Talk" (primary, accent color) and "Dismiss" (secondary)
- Clicking another NPC in the scene switches selection (overlay updates)
- Dismiss or ESC returns to BROWSE

**DIALOG**: "Talk" transitions to the existing `DialogSystem`. The cantina scene stays loaded but dims behind the dialog UI. Dialog trees are parameterized by NPC data:
- Conversation branches reveal hidden stats (fires `StatRevealedEvent`)
- Wage negotiation branch uses player Persuasion stat for a skill check
- Conditions/dealbreakers surface through specific dialog paths
- Dialog can end in: offer (→ OFFER), walk away (→ BROWSE), or "need to think about it" (→ BROWSE, NPC remembers dialog state)

**OFFER**: A confirmation modal at the end of a successful dialog. Shows:
- Full stat sheet (all stats now revealed)
- Final negotiated wage
- Any conditions the NPC has
- Crew slot status (e.g., "3/6 slots will be filled")
- Two buttons: "Hire" (fires `CrewMemberHiredEvent`) and "Decline"

**RESULT**: Brief toast/banner confirmation ("Threx-Ka hired — Engineer, 580 cr/wk"). NPC entity removed from the cantina scene. Candidate count updates. After 2s or click, returns to BROWSE.

---

## 3. Hiring Board Overlay

The Hiring Board is an interactive object within the cantina scene. Clicking it opens a list/filter overlay on top of the scene (scene dims behind it). This provides the efficient browsing path from the "combined approach."

```
┌─────────────────────────────────────────────┐
│  HIRING BOARD — Nexus Station      [ESC]    │
│                                             │
│  [All(4)] [Pilot(0)] [Gunner(1)]            │
│  [Engineer(1)] [Medic(1)] [Marine(1)]       │
│                                             │
│  ┌─────────────────────────────────────────┐│
│  │ Kira Voss   Human · Gunner    ACC 78    ││
│  │                               ~450 cr   ││
│  ├─────────────────────────────────────────┤│
│  │ Threx-Ka    Veloxi · Engineer REP 85    ││
│  │                               ~600 cr   ││
│  ├─────────────────────────────────────────┤│
│  │ Orin Mael   Human · Medic     MED 81    ││
│  │                               ~520 cr   ││
│  ├─────────────────────────────────────────┤│
│  │ Garak Durn  Krethian · Marine CMB 88    ││
│  │                               ~700 cr   ││
│  └─────────────────────────────────────────┘│
└─────────────────────────────────────────────┘
```

- **Role filter tabs** along the top, showing count per role. "All" selected by default.
- **Candidate rows** show: name, species, role, top stat, wage estimate.
- **Click a row** to close the overlay and select that NPC in the scene (transitions to SELECTED state with the detail overlay).
- **ESC** closes the hiring board overlay, returns to BROWSE.

---

## 4. Candidate Info & Stat Reveal

### Partial Reveal Strategy

On the Hiring Board and NPC name tags, the player sees:
- Name, species, role
- Top 2 stats by value (the NPC's strongest attributes)
- Wage range (approximate: ±10% from true midpoint)

On the detail overlay (SELECTED state), additionally:
- Rank
- One-line quote/hook
- Top 3 stats with visual bars

Through dialog (DIALOG state), the player can reveal hidden stats:
- "Tell me about your combat experience" → reveals Combat + Accuracy
- "What's your technical background?" → reveals Repair + Science
- "Ever piloted a ship?" → reveals Piloting
- "How do you handle people?" → reveals Persuasion
- "Can you move quietly when needed?" → reveals Stealth
- "What medical training do you have?" → reveals Medical

Each reveal fires a `StatRevealedEvent` so the UI updates live. By the time the player reaches the OFFER state, all stats are revealed.

### Wage Negotiation

A dialog branch allows wage negotiation:
1. Player chooses to negotiate ("That's steep — can we work something out?")
2. System rolls against player's Persuasion stat (or negotiator crew member if assigned)
3. **Success**: Wage reduced by 10–25% from asking max. `WageNegotiatedEvent` fired.
4. **Failure**: NPC holds firm at asking max. No penalty — the candidate doesn't leave.
5. **Critical success** (Persuasion > 80 + favorable personality match): Wage reduced by 25–40%.

### Candidate Conditions

Some NPCs have conditions stored in `RecruitableComponent.conditions`. These are revealed through dialog and shown on the OFFER confirmation:
- **Species aversion**: "I won't serve alongside Krethians" — hiring check against current roster
- **Facility requirement**: "I need a proper med bay on board" — check against ship rooms
- **Faction allegiance**: "I won't run jobs for the Hegemony" — flags future mission conflicts
- **Personal quest**: "I'm looking for my brother — help me find him and I'll sign on for half wage" — unlocks side quest, reduces wage on completion

Conditions that are currently met show green. Unmet conditions show red with a warning.

---

## 5. Architecture

### New Components

#### RecruitableComponent

```java
public class RecruitableComponent implements Component {
    public float askingWageMin;
    public float askingWageMax;
    public float negotiatedWage;           // Set after negotiation, -1 if not yet negotiated
    public List<RecruitCondition> conditions;
    public EnumSet<StatType> revealedStats; // Stats the player has uncovered
    public String dialogTreeId;             // ID into recruit_dialog_templates
    public RecruitInteractionState interactionState; // UNMET, TALKED, OFFERED, DECLINED
    public String hookLine;                 // One-line quote for the detail overlay
}
```

#### CantinaSeatComponent

```java
public class CantinaSeatComponent implements Component {
    public String seatId;    // Authored position key (e.g., "bar_stool_1", "corner_booth")
    public float sceneX;     // X position in cantina scene (0-1 normalized)
    public float sceneY;     // Y position in cantina scene (0-1 normalized)
}
```

#### RecruitInteractionState (enum)

`UNMET`, `TALKED`, `OFFERED`, `DECLINED`

#### StatType (enum)

`ACCURACY`, `REPAIR`, `MEDICAL`, `PILOTING`, `SCIENCE`, `COMBAT`, `PERSUASION`, `STEALTH`

Maps to `NpcStatsComponent` fields. Used by `RecruitableComponent.revealedStats` to track which stats have been shown to the player.

#### RecruitCondition

```java
public class RecruitCondition {
    public RecruitConditionType type;  // SPECIES_AVERSION, FACILITY_REQUIRED, FACTION_ALLEGIANCE, PERSONAL_QUEST
    public String targetId;            // Species ID, room type, faction ID, or quest ID
    public String description;         // Player-facing text
    public boolean met;                // Evaluated at offer time
}
```

### New Systems

#### RecruitmentScreenSystem (Ashley EntitySystem)

- **Location**: `core/src/main/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystem.java`
- Owns a Scene2D `Stage` with `FitViewport` (reference resolution: 1280x720)
- Manages the cantina scene rendering, NPC portrait actors, detail overlay, hiring board overlay, and hire confirmation dialog
- Subscribes to `RecruitmentOpenedEvent` to open, `RecruitmentClosedEvent` to close
- On open: pushes Stage to InputMultiplexer, disables `PlayerInputSystem`, hides HUD via `CockpitHUDSystem`
- On close: removes Stage, re-enables player input, restores HUD
- Queries entities with `RecruitableComponent` + `CantinaSeatComponent` to populate the scene
- Listens for `StatRevealedEvent` and `WageNegotiatedEvent` to update the detail overlay live
- Listens for `CrewMemberHiredEvent` to remove the NPC from the scene and show result toast
- Manages the BROWSE → SELECTED → DIALOG → OFFER → RESULT state machine
- `update(float delta)` calls `stage.act()` and `stage.draw()` when open

#### CandidatePoolSystem (Ashley EntitySystem)

- **Location**: `core/src/main/java/com/galacticodyssey/npc/systems/CandidatePoolSystem.java`
- Generates recruitable NPC entities when the player arrives at a station
- Uses `NpcGenerator` to create NPCs from seed (station ID + timestamp for deterministic but varying pools)
- Attaches `NpcIdentityComponent`, `NpcStatsComponent`, `NpcPersonalityComponent`, `RecruitableComponent`, `CantinaSeatComponent`
- Reads station config from `cantina_layouts.json` to determine: seat positions, NPC capacity (3–8 per station), background scene key
- Candidate pool persists while the player is at the station. Refreshes when the player leaves and returns, or after a configurable time window (e.g., 3 in-game days)
- NPCs the player has talked to but not hired persist across refreshes (interaction state saved)

### New Events

| Event | Payload | Published by |
|-------|---------|-------------|
| `RecruitmentOpenedEvent` | `String stationId` | Player interaction trigger |
| `RecruitmentClosedEvent` | (none) | `RecruitmentScreenSystem` on ESC / leave |
| `CandidateSelectedEvent` | `Entity npcEntity` | `RecruitmentScreenSystem` on NPC click |
| `StatRevealedEvent` | `Entity npcEntity, StatType stat, float value` | Dialog action handler |
| `WageNegotiatedEvent` | `Entity npcEntity, float finalWage, float discountPercent` | Dialog action handler |

Reuses existing: `CrewMemberHiredEvent`, `DialogOpenedEvent`, `DialogClosedEvent`.

### Existing Systems (integrated, not modified)

| System | Integration |
|--------|-------------|
| `DialogSystem` | Drives conversation after "Talk" button. Recruitment dialog trees reference NPC entity for parameterization. |
| `CrewAssignmentSystem` | Recalculates effectiveness after a new hire. |
| `CrewXPSystem` | Starts XP tracking for the new crew member. |
| `PlayerInputSystem` | Disabled while recruitment screen is open. |
| `CockpitHUDSystem` | HUD hidden while recruitment screen is open. |

---

## 6. Scene2D Actor Hierarchy

```
RecruitmentScreenSystem (owns Stage)
├── CantinaSceneActor (Group)
│   ├── Background image or gradient
│   └── Ambient elements (lights, bar counter, decorative)
├── NpcPortraitGroup (Group)
│   ├── NpcPortraitActor × N (extends Group, clickable)
│   │   ├── Portrait image (circular, 64px, species-colored border)
│   │   └── NameTagActor (Table: name, species·role, top stats)
│   └── HiringBoardActor (extends Group, clickable scene element)
├── CandidateDetailOverlay (Table, slides up from bottom)
│   ├── Portrait + name/species/role/rank row
│   ├── Quote label (italic)
│   ├── Stat bars (revealed stats as bars, hidden as "???")
│   ├── Wage range label
│   └── ButtonRow: [Talk] [Dismiss]
├── HiringBoardOverlay (Table, centered modal)
│   ├── Header row with title + close button
│   ├── RoleFilterBar (HorizontalGroup of TextButtons)
│   └── CandidateListPane (ScrollPane of CandidateRowActor items)
├── HireConfirmationDialog (Table, centered modal)
│   ├── Full stat sheet (all stats revealed)
│   ├── Negotiated wage + conditions list
│   ├── Crew slot status
│   └── ButtonRow: [Hire] [Decline]
├── ResultToast (Table, top-center fade-in/out)
│   └── "Name hired — Role, Wage" label
└── HeaderBar (Table, top of screen)
    ├── Location label + candidate count + crew slots
    └── Credits label + payroll label
```

### Actor Details

**NpcPortraitActor**: Circular portrait (placeholder: species-colored circle with emoji). Border color indicates interaction state: red (UNMET), yellow (TALKED), green (OFFERED). Mouse-over scales up slightly (1.05x) and brightens border. Click fires `CandidateSelectedEvent`.

**NameTagActor**: Small Table below portrait. Dark semi-transparent background. Shows name (accent color), species·role (dim), top 2 stats (green). Always visible in BROWSE state.

**CandidateDetailOverlay**: Table anchored to bottom of screen. Uses `MoveToAction` for slide-up animation (~200ms, Interpolation.circleOut). Height ~25% of screen. Semi-transparent dark background with top border highlight matching the selected NPC's species color.

**HiringBoardOverlay**: Centered Table, ~60% screen width, ~70% screen height. Dark background with green terminal-style border. Role filter tabs are TextButtons that filter the candidate list. Each CandidateRowActor shows name, species, role, top stat, wage estimate. Click closes overlay and selects that NPC.

**HireConfirmationDialog**: Centered modal, ~40% screen width. Shows final terms. Hire button fires the hiring sequence. Decline returns to BROWSE.

---

## 7. Data Files

### New Files

#### data/npcs/recruit_conditions.json

Condition templates that `CandidatePoolSystem` randomly assigns to generated NPCs.

```json
[
  {
    "id": "no_krethians",
    "type": "SPECIES_AVERSION",
    "targetId": "krethian",
    "description": "Won't serve alongside Krethians",
    "weight": 0.15
  },
  {
    "id": "needs_medbay",
    "type": "FACILITY_REQUIRED",
    "targetId": "MEDBAY",
    "description": "Requires a functional med bay on board",
    "weight": 0.10
  },
  {
    "id": "anti_hegemony",
    "type": "FACTION_ALLEGIANCE",
    "targetId": "hegemony",
    "description": "Refuses jobs for the Hegemony",
    "weight": 0.08
  }
]
```

`weight` is the probability of a generated NPC having this condition. NPCs can have 0–2 conditions.

#### data/npcs/recruit_dialog_templates.json

Parameterized dialog tree templates for recruitment conversations. The `DialogSystem` substitutes NPC data (name, species, stats, background) into template placeholders at runtime.

```json
{
  "recruitment_standard": {
    "entryNode": "greeting",
    "nodes": {
      "greeting": {
        "speakerRef": "npc",
        "text": "{{npc.hookLine}}",
        "choices": [
          { "text": "Tell me about your experience.", "next": "background_reveal" },
          { "text": "What are your technical skills?", "next": "tech_reveal" },
          { "text": "What's your asking wage?", "next": "wage_discuss" },
          { "text": "Not interested.", "next": "exit" }
        ]
      }
    }
  }
}
```

Full template structure supports branching based on species, background, personality, and conditions. Templates are selected by `CandidatePoolSystem` based on NPC traits.

#### data/stations/cantina_layouts.json

Per-station cantina configuration: seat positions, capacity, and scene background.

```json
{
  "nexus_station": {
    "backgroundKey": "cantina_nexus",
    "capacity": 5,
    "seats": [
      { "id": "bar_stool_1", "x": 0.12, "y": 0.35 },
      { "id": "bar_stool_2", "x": 0.25, "y": 0.38 },
      { "id": "center_table", "x": 0.45, "y": 0.48 },
      { "id": "corner_booth", "x": 0.72, "y": 0.30 },
      { "id": "wall_standing", "x": 0.82, "y": 0.55 }
    ],
    "hiringBoard": { "x": 0.30, "y": 0.22 }
  }
}
```

Coordinates are normalized (0–1) and mapped to the scene viewport at render time.

---

## 8. Screen Entry

The player opens the recruitment screen by interacting with a cantina entrance or doorway at a station (using the existing `InteractionSystem` interact key). The interaction fires `RecruitmentOpenedEvent` with the station's ID. The `RecruitmentScreenSystem` listens for this event and opens the cantina scene.

If the station has no cantina (not all stations do), no interaction point is present.

---

## 9. Input Handling

| Key / Action | Effect |
|-------------|--------|
| Left-click NPC | Select NPC, show detail overlay (BROWSE → SELECTED) |
| Left-click another NPC | Switch selection (stays in SELECTED) |
| Left-click "Talk" | Enter dialog (SELECTED → DIALOG) |
| Left-click "Dismiss" | Close overlay (SELECTED → BROWSE) |
| Left-click Hiring Board | Open list overlay |
| Left-click candidate row | Close list, select NPC in scene |
| Left-click "Hire" | Confirm hire (OFFER → RESULT) |
| Left-click "Decline" | Cancel (OFFER → BROWSE) |
| ESC | Context-dependent: close overlay → close hiring board → leave cantina |
| Mouse-over NPC | Highlight glow + slight scale |

### Input Multiplexer Integration

When recruitment screen opens:
1. `RecruitmentScreenSystem` Stage pushed to top of `InputMultiplexer`
2. `PlayerInputSystem` disabled via `setEnabled(false)`
3. `CockpitHUDSystem` HUD hidden
4. `RecruitmentOpenedEvent` published

When recruitment screen closes:
1. Stage removed from `InputMultiplexer`
2. `PlayerInputSystem` re-enabled
3. HUD restored
4. `RecruitmentClosedEvent` published

---

## 10. Hiring Sequence

When the player clicks "Hire" on the confirmation dialog:

1. Deduct signing bonus from player credits (one-time cost: `negotiatedWage * 2`, or `askingWageMax * 2` if no negotiation)
2. Attach `CrewMemberComponent` to the NPC entity:
   - `role`: from `NpcIdentityComponent.role` (mapped to `CrewRole`)
   - `rank`: `RECRUIT` (or higher if NPC background warrants, e.g., ex-military → `CREWMAN`)
   - `morale`: 75 (default starting)
   - `loyalty`: 50 (default starting)
   - `wage`: negotiated or asking wage
3. Remove `RecruitableComponent` and `CantinaSeatComponent` from the entity
4. Fire `CrewMemberHiredEvent(npcEntity, role)`
5. Update the scene: remove NPC portrait, decrement candidate count, update crew slot display
6. Show result toast for 2 seconds
7. Return to BROWSE state

If the player cannot afford the signing bonus, the "Hire" button is grayed out with a tooltip showing the cost deficit.

---

## 11. Candidate Generation

`CandidatePoolSystem` generates candidates when `RecruitmentOpenedEvent` fires (if the pool for this station is stale or empty).

### Generation Algorithm

1. Read station config from `cantina_layouts.json` → get capacity and seat positions
2. Determine how many candidates to generate: `capacity - persistedCandidatesAtStation`
3. For each new candidate:
   a. Seed RNG with `stationId.hashCode() ^ candidateIndex ^ dayCounter` for deterministic generation
   b. Use `NpcGenerator` to create NPC entity (species, background, name, stats, personality, portrait)
   c. Roll for role affinity based on stats (highest stat cluster → matching `CrewRole`)
   d. Set wage range: `baseWageForRole * (1 + statQualityModifier) * (0.9 to 1.1 random spread)`
   e. Roll for conditions: 60% chance of 0, 30% chance of 1, 10% chance of 2 (weighted from `recruit_conditions.json`)
   f. Select dialog template based on species + background + personality
   g. Generate hook line from template or background flavor text
   h. Assign to a seat position from the layout
   i. Set `interactionState = UNMET`, `revealedStats` to top 2 stats by value (shown on name tags; detail overlay adds the 3rd)

### Pool Persistence

- Candidates persist while the player remains at the station
- Candidates the player has interacted with (`TALKED`, `OFFERED`, `DECLINED`) persist across station visits and pool refreshes
- Uninteracted candidates are regenerated on: player departure + return, or after 3 in-game days
- A station can have at most `capacity` candidates at any time

---

## 12. Package Layout

```
npc/
  components/
    RecruitableComponent.java         (NEW)
    RecruitCondition.java             (NEW)
    RecruitConditionType.java         (NEW — enum)
    RecruitInteractionState.java      (NEW — enum)
    StatType.java                     (NEW — enum)
    CantinaSeatComponent.java         (NEW)
  systems/
    CandidatePoolSystem.java          (NEW)

ui/
  systems/
    RecruitmentScreenSystem.java      (NEW)
  actors/
    CantinaSceneActor.java            (NEW)
    NpcPortraitActor.java             (NEW)
    NameTagActor.java                 (NEW)
    HiringBoardActor.java             (NEW)
    CandidateDetailOverlay.java       (NEW)
    HiringBoardOverlay.java           (NEW)
    CandidateRowActor.java            (NEW)
    HireConfirmationDialog.java       (NEW)
    ResultToast.java                  (NEW)

npc/
  events/
    RecruitmentOpenedEvent.java       (NEW)
    RecruitmentClosedEvent.java       (NEW)
    CandidateSelectedEvent.java       (NEW)
    StatRevealedEvent.java            (NEW)
    WageNegotiatedEvent.java          (NEW)

data/
  npcs/
    recruit_conditions.json           (NEW)
    recruit_dialog_templates.json     (NEW)
  stations/
    cantina_layouts.json              (NEW)
```

---

## 13. Implementation Phases

1. **Phase 1 — Data model & candidate generation**: `RecruitableComponent`, `CantinaSeatComponent`, enums, `RecruitCondition`, new events, `CandidatePoolSystem`, JSON data files. Unit tests for generation and condition evaluation.
2. **Phase 2 — Cantina scene UI**: `RecruitmentScreenSystem`, `CantinaSceneActor`, `NpcPortraitActor`, `NameTagActor`, `HeaderBar`. BROWSE and SELECTED states with detail overlay. Input handling and InputMultiplexer integration.
3. **Phase 3 — Hiring board overlay**: `HiringBoardActor`, `HiringBoardOverlay`, `CandidateRowActor`, role filter tabs. Click-to-select integration with scene.
4. **Phase 4 — Dialog integration**: Wire "Talk" button to `DialogSystem` with recruitment dialog templates. Stat reveal through dialog choices. Wage negotiation with Persuasion check. Condition discovery.
5. **Phase 5 — Hire flow**: `HireConfirmationDialog`, `ResultToast`, hiring sequence (attach `CrewMemberComponent`, deduct credits, fire events). Full BROWSE → SELECTED → DIALOG → OFFER → RESULT loop.
6. **Phase 6 — Polish**: Portrait hover animations, overlay slide tweens, species-colored borders, interaction state visual indicators, pool persistence across station visits.
