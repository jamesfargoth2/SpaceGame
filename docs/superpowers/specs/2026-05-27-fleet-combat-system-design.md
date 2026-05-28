# Fleet Combat System Design

## Overview

A large-scale fleet combat system where the player flies their own ship in real-time while issuing fleet orders via a dedicated tactical map. Battles scale to 25-100+ ships per side with LOD AI. NPC factions wage autonomous fleet wars that reshape galaxy territory. Ships span a full naval hierarchy from fighters to dreadnoughts, with expendable small ships and persistent capturable capital ships.

**Architecture:** Fleet-as-Entity (Approach A). A fleet is an Ashley ECS entity with its own components. Member ships reference their fleet via `FleetMemberComponent`. Off-screen fleets exist as single collapsed entities with aggregate stats; they expand into individual ship entities when the player enters their sector.

**Package:** `core/src/main/java/com/galacticodyssey/combat/fleet/`

---

## 1. Fleet Entity Architecture

### Fleet Entity

A fleet is an Ashley entity carrying three components:

**FleetComponent:**
- `String fleetId` — unique identifier
- `String factionId` — owning faction
- `String fleetName` — procedurally generated or player-assigned
- `Entity admiralEntity` — NPC entity serving as admiral (null for player fleet)
- `Entity flagshipEntity` — the fleet's flagship
- `FleetDoctrine doctrine` — enum: AGGRESSIVE, BALANCED, DEFENSIVE, EVASIVE
- `FleetState state` — enum: PATROL, INTERCEPT, ENGAGED, RETREATING, REGROUPING, MUSTERING, JUMPING
- `float aggregateFirepower` — cached total fleet strength (sum of ship firepower weighted by class)
- `float aggregateHP` — cached total fleet HP
- `float aggregateSpeed` — cached fleet speed (limited by slowest ship)
- `List<FleetShipEntry> composition` — ship manifest for collapsed fleets (class, count, HP ratios)
- `boolean expanded` — whether individual ship entities currently exist

**FleetFormationComponent:**
- `String formationTemplateId` — active formation template (line, wedge, box, sphere, wall, scattered)
- `double anchorX, anchorY, anchorZ` — formation anchor in galaxy-space (64-bit)
- `float localAnchorX, localAnchorY, localAnchorZ` — formation anchor in local-space (32-bit, near player)
- `float headingYaw, headingPitch` — formation facing direction
- `float spacingScale` — multiplier on formation slot distances (default 1.0)

**FleetTacticsComponent:**
- `Map<String, Float> threatAssessment` — enemy fleet ID to threat score
- `float engageMinRange, engageMaxRange` — preferred engagement distance
- `float retreatThreshold` — fraction of losses before retreat (0.0-1.0)
- `FleetShipClass priorityTargetClass` — preferred target class
- `Queue<FleetOrder> orders` — pending orders from admiral AI or player

### Ship Fleet Membership

Ship entities receive:

**FleetMemberComponent:**
- `Entity fleetEntity` — reference to parent fleet entity
- `int squadronIndex` — squadron group (0-N, groups of 3-5 ships)
- `FleetRole role` — enum: FLAGSHIP, VANGUARD, ESCORT, FIRE_SUPPORT, INTERCEPTOR, SUPPORT, RESERVE
- `int formationSlotIndex` — position within formation template
- `Vector3 localFormationOffset` — computed offset from formation anchor

### Expand/Collapse Lifecycle

- **Collapsed:** Fleet entity exists with `FleetComponent.composition` list and aggregate stats. No individual ship entities. Used for galaxy-wide simulation.
- **Expanding:** `FleetExpansionSystem` detects player entering a sector with a collapsed fleet. Spawns individual ship entities from composition data, assigns `FleetMemberComponent`, `ShipDataComponent`, `CombatAIComponent`, weapon components. Places ships in formation around the anchor point. Sets `FleetComponent.expanded = true`.
- **Collapsing:** When the player leaves the sector, individual ship entities are despawned. Current HP, ammo, crew state written back to `FleetComponent.composition`. Sets `FleetComponent.expanded = false`.
- Transition is seamless — ships spawn at their expected formation positions and immediately begin AI behavior.

---

## 2. Ship Classification & Fleet Composition

### FleetShipClass Enum

Maps onto the existing `ShipSizeClass` (SMALL, MEDIUM, LARGE) with fleet-specific role identity:

| FleetShipClass | ShipSizeClass | Default FleetRole | Expendable? | Notes |
|---|---|---|---|---|
| FIGHTER | SMALL | INTERCEPTOR | Yes | Fast, fragile, attacks in wings of 4-6 |
| BOMBER | SMALL | FIRE_SUPPORT | Yes | Slow for size, heavy anti-capital ordnance |
| CORVETTE | SMALL | ESCORT / VANGUARD | Yes | Fast picket, screens for capitals |
| FRIGATE | MEDIUM | ESCORT | No | Anti-fighter, point defense escort |
| DESTROYER | MEDIUM | VANGUARD | No | Torpedo runs, aggressive flanking |
| CRUISER | MEDIUM | FIRE_SUPPORT | No | Versatile backbone, balanced firepower/armor |
| BATTLECRUISER | LARGE | VANGUARD | No | Fast capital, high damage, less armor than battleship |
| BATTLESHIP | LARGE | FIRE_SUPPORT | No | Heavy broadsides, thick armor, slow |
| CARRIER | LARGE | SUPPORT | No | Launches/recovers fighter and bomber wings |
| DREADNOUGHT | LARGE | FLAGSHIP | No | Rare, massive, heaviest weapons and armor |

Expendable ships (Fighter, Bomber, Corvette) are permanently destroyed on death. Non-expendable ships (Frigate+) are disabled, not destroyed, and can be captured, repaired, or scuttled.

### Fleet Composition Data

Data-driven templates loaded from JSON. Each faction ethos gets templates:

```json
{
  "id": "militarist_battle_fleet",
  "factionEthos": "MILITARIST",
  "slots": [
    { "shipClass": "DREADNOUGHT", "count": [0, 1] },
    { "shipClass": "BATTLESHIP", "count": [1, 3] },
    { "shipClass": "CRUISER", "count": [3, 6] },
    { "shipClass": "DESTROYER", "count": [2, 5] },
    { "shipClass": "FRIGATE", "count": [4, 8] },
    { "shipClass": "FIGHTER", "count": [12, 24] }
  ],
  "doctrineDefault": "AGGRESSIVE"
}
```

`FactionData.militaryStrength` scales the count ranges: a strong faction rolls near the top of each range, a weak one near the bottom.

Fleet strength is a single computed value: `sum(shipClassWeight * count)` per class. Used for off-screen battle resolution and encounter balancing.

### Faction Composition Tendencies

| FactionEthos | Tendency |
|---|---|
| MILITARIST | Capital-heavy, battleships and dreadnoughts, aggressive doctrine |
| CORPORATE | Balanced fleets, strong escorts, BALANCED doctrine |
| FEDERATION | Cruiser-heavy, diplomatic screening, DEFENSIVE doctrine |
| PIRATE_SYNDICATE | Raider packs — many corvettes/destroyers, few capitals, EVASIVE doctrine |
| ISOLATIONIST | Defensive pickets — frigates and fighters, DEFENSIVE doctrine |

---

## 3. Fleet AI & Tactics

Three tiers of AI, each at different scope and update frequency.

### Tier 1: Admiral AI

Runs on the fleet entity. Uses a behavior tree stored in `FleetTacticsComponent`. Ticks every 1-2 seconds.

**Responsibilities:**
- **Threat assessment:** Scans known enemy fleets/ships in the engagement. Computes relative strength ratio (own strength / enemy strength). Updates `FleetTacticsComponent.threatAssessment`.
- **Doctrine execution:**
  - AGGRESSIVE — engages at strength ratio > 0.6, pushes forward, focuses fire on enemy capitals
  - BALANCED — engages at strength ratio > 0.8, holds formation, systematic target elimination
  - DEFENSIVE — holds position, waits for enemy approach, prioritizes point defense and escorts
  - EVASIVE — avoids engagement at strength ratio < 1.2, hit-and-run on stragglers
- **Retreat decision:** When losses exceed `retreatThreshold` (default 40% for AGGRESSIVE, 30% for BALANCED, 25% for DEFENSIVE, 20% for EVASIVE), issues fleet-wide RETREAT order.
- **Order issuance:** Pushes `FleetOrder` objects into the orders queue: ATTACK_TARGET, HOLD_POSITION, ADVANCE, RETREAT, REGROUP, LAUNCH_FIGHTERS.

For the player's fleet, admiral AI is replaced by player commands from the tactical map. The player can optionally delegate to an NPC admiral (their fleet's highest-ranked captain) for autonomous operation.

### Tier 2: Squadron AI

Not a separate entity. `SquadronCoordinationSystem` groups ships by `FleetMemberComponent.squadronIndex` (3-5 ships per squadron). Ticks every 0.5 seconds.

**Responsibilities:**
- Translates admiral orders into squadron-level behavior
- Picks specific targets within the admiral's priority class
- Coordinates focus fire (all squadron members attack the same target)
- Manages formation-keeping within the squadron
- Relays threat data up to admiral tier via `ThreatDetectedEvent`

This is a direct scale-up of the existing `SquadTacticsSystem` pattern: group entities by ID, aggregate threats, issue group orders.

### Tier 3: Individual Ship AI

The existing `CombatAIComponent` + behavior tree, extended with fleet awareness. Runs every frame for nearby ships.

**Fleet-aware behavior:**
- Ship AI checks `FleetMemberComponent` for current fleet orders before autonomous decisions
- ATTACK_TARGET order: behavior tree prioritizes that target, but can still pick closer threats if under direct fire
- RETREAT order: ship disengages, moves toward retreat waypoint
- No fleet orders (stragglers, unassigned): falls back to autonomous combat using existing behavior tree

### LOD AI Tiers

| Distance from player | AI level | Update rate | Details |
|---|---|---|---|
| < 2km | Full | Every frame | Full behavior tree, weapon systems, physics, projectiles |
| 2-10km | Simplified | Every 0.25s | Pick target, move toward/away, fire at intervals, no projectiles (hitscan approximation) |
| 10-50km | Abstract | Every 1s | Damage exchanges between ship pairs, no projectiles, simplified movement |
| Off-screen | Aggregate | Every 5s | Fleet strength vs strength, casualty rolls |

`FleetLODSystem` manages promotion/demotion between tiers based on distance to player camera. Ships transitioning from abstract to full get behavior trees initialized; ships going the other direction get trees paused.

---

## 4. Tactical Map & Player Commands

### Screen Design

`TacticalMapScreen` — a separate screen toggled with a keybind (default: Tab). Combat continues in real-time while the map is open. A time-slow toggle (0.25x speed) is available on the map.

**Rendering:**
- Top-down 2D projection using a separate `OrthographicCamera` and `SpriteBatch`
- Queries Ashley engine for all entities with `FleetMemberComponent` and hostile ships in sensor range
- Player fleet ships: colored chevrons, color-coded by squadron
- Enemy ships: red icons
- Icon shape by class: small triangle (fighters), diamond (frigates/destroyers), large square (cruisers/battleships), hexagon (carriers), star (dreadnoughts)
- Disabled/retreating ships: reduced opacity
- Formation template overlay: ghost positions when formation is selected
- Fog of war: only enemy ships within fleet sensor range are visible (sensor range scales with ship class)

### Selection

- Click: select individual ship or squadron
- Drag-box: select multiple ships
- Hotkeys: 1-9 for squadron groups, E for all escorts, F for all fighters, A for entire fleet
- Selected ships highlighted with a ring

### Orders

| Order | Hotkey | Input | Effect |
|---|---|---|---|
| Attack target | — | Right-click enemy | Selected ships focus fire on target |
| Move to | — | Right-click empty space | Selected ships move to position, hold there |
| Hold position | H | — | Selected ships stop and hold current position |
| Escort | — | Right-click friendly ship | Selected ships form up around target ship |
| Formation | F1-F6 | — | Switch selected ships to formation preset |
| Retreat | R | — | Selected ships disengage, move to fleet rally point |
| Launch fighters | L | — | Carriers launch all docked fighter/bomber wings |
| Recall fighters | Ctrl+L | — | Fighters return to carrier |

**Fleet-wide orders:**
- Ctrl+A then order: issues to all ships
- Ctrl+D: cycle doctrine (AGGRESSIVE / BALANCED / DEFENSIVE / EVASIVE)
- Ctrl+R: fleet-wide retreat

### Implementation

Input handled by `TacticalMapInputProcessor` pushed onto the input multiplexer when map opens. Orders published as `FleetOrderEvent` on EventBus. `FleetCommandSystem` routes orders to appropriate fleet/squadron/ship components.

Player's own ship always visible with a distinct icon. Clicking the player ship re-centers cockpit view.

### Formation Templates

Six presets loaded from data:

| Template | Shape | Use Case |
|---|---|---|
| LINE | Ships abreast in a line | Broadside engagement, maximum firepower forward |
| WEDGE | V-shape, flagship at point | Aggressive advance, concentrated spearhead |
| BOX | Rectangular formation | Balanced defense, mutual support |
| SPHERE | 3D sphere around flagship | All-around protection, anti-ambush |
| WALL | Flat plane facing enemy | Maximum forward firepower, defensive screen |
| SCATTERED | Random spread with minimum spacing | Anti-area-of-effect, evasive posture |

Each template defines slot positions as unit vectors from the anchor. `FleetFormationSystem` multiplies by `spacingScale` and ship class size to compute actual positions.

---

## 5. NPC Fleet Wars & Galaxy Simulation

### Fleet Lifecycle

Factions generate fleets based on `FactionData.militaryStrength` and `economicStrength`. Each faction maintains a fleet pool scaled by military strength:

- MILITARIST (0.8 strength): 6-8 fleets
- CORPORATE (0.6 strength): 4-6 fleets
- FEDERATION (0.5 strength): 3-5 fleets
- PIRATE_SYNDICATE (0.3 strength): 3-4 raider packs
- ISOLATIONIST (0.4 strength): 2-4 defensive pickets

Fleets spawn as collapsed entities at faction shipyards/stations, assigned patrol routes via the existing `PatrolRoute` system.

### Fleet States (Galaxy-Level)

| State | Behavior |
|---|---|
| PATROL | Follows assigned `PatrolRoute` waypoints. Detects hostiles within sensor range. |
| INTERCEPT | Hostile fleet detected — moving to engage. |
| ENGAGED | In combat with enemy fleet. Resolves via off-screen simulation if player absent. |
| RETREATING | Lost the fight, moving toward nearest friendly station for repair. |
| REGROUPING | At friendly station, repairing and reinforcing. Duration proportional to losses. |
| MUSTERING | Newly spawned fleet assembling at shipyard before deployment. |

### Off-Screen Battle Resolution

`FleetSimulationSystem` resolves battles between collapsed fleets:

1. Compare aggregate fleet strength (firepower-weighted sum).
2. Run combat rounds (each round = ~5 simulated seconds):
   - Each side deals damage proportional to remaining firepower.
   - Damage removes ships starting from smallest class (fighters first, capitals last).
   - Doctrine modifies damage: AGGRESSIVE +20% dealt / +20% taken, DEFENSIVE -20% dealt / -20% taken.
3. Check retreat thresholds after each round. Losing fleet retreats when threshold hit.
4. Winner holds the field. Loser enters RETREATING state.
5. Publish `FleetBattleResolvedEvent` with winner/loser IDs, casualties, territory changes.

### Territory Effects

When a faction fleet is destroyed or retreats from a system, `TerritoryAssigner` can flip system control. Systems on faction borders (tracked in `FactionTerritory.borderSystems`) are contested — multiple battles may be needed to flip control.

Territory changes trigger `TerritoryChangedEvent`, affecting:
- Trade routes and economy
- Patrol route assignments
- Available missions
- Player reputation context

### Player Interaction with NPC Wars

- Player can enter a sector with an active fleet battle — nearby fleets expand into individual ship entities
- Player can join either side (or neither)
- Joining a faction's side in battle earns reputation with that faction, loses rep with the enemy
- Player can take faction contracts to reinforce specific fronts
- Player's personal fleet counts toward battle outcome — a strong player fleet can turn the tide

### Performance Budget

40-60 collapsed fleet entities galaxy-wide. `FleetSimulationSystem` ticks every 5 seconds — each tick is simple arithmetic (strength comparisons, casualty rolls). Only 1-2 fleets ever expanded simultaneously (those near the player).

---

## 6. Combat Resolution & Consequences

### Damage Model

Fleet combat uses the existing `DamageSystem` and `DamageType` enum for expanded ships. Damage flow: weapon fires -> projectile/hitscan hit -> `DamageDealtEvent` -> `DamageSystem` applies mitigation from `ArmorComponent`/`ShieldComponent` -> reduces `HealthComponent.currentHP`.

**Structural damage zones** via existing `StructuralIntegrityComponent`: capital ships have zones (bow, port, starboard, stern, engineering). Concentrated fire on one zone disables subsystems in that zone (engines, weapons, shields) before the ship is fully destroyed. Flanking a battleship to hit its unshielded stern is more effective than pounding forward shields.

### Destruction vs Disabling

| Ship Class | On "Death" | Crew Fate | Salvageable? |
|---|---|---|---|
| Fighter, Bomber | Explodes. Permanent loss. | Killed. | No |
| Corvette | Explodes. Permanent loss. | Eject in escape pods (50% survival). | No |
| Frigate, Destroyer | Disabled — drifts, weapons offline, engines dead. | Survive aboard. | Yes — tow to station |
| Cruiser, Battlecruiser | Disabled — can be boarded and captured. | Surrender or fight boarders. | Yes — capture or repair |
| Battleship, Carrier, Dreadnought | Disabled in stages — subsystems fail progressively. | Evacuate to interior shelters. | Yes — major prize |

### Capture Mechanic

When a capital ship (cruiser+) is disabled, the player or an allied ship with marines can board it. Boarding resolves as a simplified ground combat check: attacker marine strength vs defender remaining crew. Success transfers ownership — captured ship joins the player's fleet with reduced HP and possibly damaged subsystems.

Capturing an enemy capital ship is a major event: significant reputation shift with both factions involved.

### Escape Pods

Corvette+ crews eject in escape pods when their ship is destroyed/disabled. Pods are small entities that drift until:
- Rescued by friendly ship (tractor beam)
- Rescued by enemy (reputation choice — rescuing enemy pods earns enemy faction respect, may annoy allies)
- Despawn after a timer

### Post-Battle

`FleetPostBattleSystem` triggers after combat ends (all enemies destroyed, disabled, or retreated):
- Surviving ships auto-repair minor damage (hull restored to 50% of missing HP over 60 seconds)
- Disabled friendly ships flagged for tow/repair
- Loot generated from destroyed ships (`SalvageComponent` on wreckage entities)
- Battle summary event published: kills, captures, losses, reputation changes
- Fleet strength recalculated with updated composition

---

## 7. Fleet Acquisition & Management

### Path 1: Purchase Ships

Shipyards at stations sell ships. Available inventory depends on station faction, economic strength, and player reputation:

| Reputation Tier | Available Classes |
|---|---|
| NEUTRAL | Fighters, Corvettes |
| FRIENDLY | + Frigates, Destroyers |
| ALLIED | + Cruisers, Bombers |
| HONORED | + Battlecruisers, Battleships |
| EXALTED | + Carriers, Dreadnoughts |

Purchased ships come with a basic AI crew (via `NpcGenerator`). Player can assign a captain from recruited NPCs — captain stats (piloting, combat, persuasion from `NpcStatsComponent`) modify ship combat effectiveness (fire rate, evasion, crew morale).

### Path 2: Faction Fleet Assignment

At ALLIED reputation or above, factions assign escort ships for faction missions. Ships are on loan — follow orders during the mission, return afterward.

At HONORED+, player can request a permanent detachment: a small squadron that stays with the player's fleet as long as reputation remains above the threshold. Dropping below triggers recall.

Faction-assigned ships have their own captains, can't be sold or modified, and won't violate their faction's ethics (e.g., FEDERATION ship won't attack civilian transports).

### Path 3: Capture

Disable and board enemy capital ships (see Section 6). Captured ships require:
1. Repair at a station (costs credits, takes time)
2. Hire crew (or reassign from another ship)
3. Optional refit weapons at shipyard

Captured ships from hostile factions may carry a reputation cost with other factions.

### Fleet Management UI

Accessible from stations or from the tactical map's fleet panel:
- Ship roster: all owned/assigned ships with class, captain, HP, loadout summary
- Drag to assign ships to squadrons (numbered groups)
- Assign/swap captains between ships
- Set individual ship roles (overrides default from FleetShipClass)
- Refit weapons at shipyards (swap hardpoint loadouts)
- Repair and resupply (costs credits, immediate at stations)
- Dismiss ships (sell back at reduced price, or release faction-assigned ships)

### Fleet Capacity

Player fleet capacity is limited by flagship class and fleet command skill/perks:

| Flagship Class | Base Capacity |
|---|---|
| Corvette | 5-8 ships |
| Frigate | 8-12 ships |
| Cruiser | 12-20 ships |
| Battleship | 20-35 ships |
| Dreadnought | 40-60 ships |

Capacity can increase through ship upgrades (command module hardpoints) or faction reputation bonuses (higher tiers grant +N fleet slots).

---

## 8. Events & Integration

### New Events

**Fleet-Level:**
- `FleetCreatedEvent(String fleetId, String factionId, double x, double y, double z)`
- `FleetDestroyedEvent(String fleetId, String factionId)`
- `FleetOrderEvent(String fleetId, FleetOrder order, int[] targetSquadrons)`
- `FleetDoctrineChangedEvent(String fleetId, FleetDoctrine oldDoctrine, FleetDoctrine newDoctrine)`
- `FleetStateChangedEvent(String fleetId, FleetState oldState, FleetState newState)`
- `FleetExpandedEvent(String fleetId)` — collapsed fleet spawned into individual ships
- `FleetCollapsedEvent(String fleetId)` — individual ships despawned to aggregate

**Battle:**
- `FleetEngagementStartedEvent(String attackerFleetId, String defenderFleetId, double x, double y, double z)`
- `FleetBattleResolvedEvent(String winnerFleetId, String loserFleetId, int[] casualties, List<String> capturedShips)`
- `ShipDisabledEvent(Entity ship, Entity attacker)` — capital ship disabled, available for capture
- `ShipCapturedEvent(Entity ship, Entity captor, String oldFactionId)`
- `EscapePodLaunchedEvent(Entity pod, Entity sourceShip)`

**Galaxy:**
- `TerritoryChangedEvent(String systemId, String oldFactionId, String newFactionId)`
- `FactionFleetMusteredEvent(String factionId, String fleetId)` — new fleet spawned
- `FactionReinforcementEvent(String fleetId, String reinforcingFleetId)`

### Integration with Existing Systems

| Existing System | Integration |
|---|---|
| `ReputationManager` | Listens to `FleetBattleResolvedEvent`, `ShipCapturedEvent` — adjusts faction standings |
| `CombatAISystem` | Ship behavior trees check `FleetMemberComponent` for fleet orders before autonomous targeting |
| `SquadTacticsSystem` | Extended by `SquadronCoordinationSystem` for fleet-scale groups |
| `ShipWeaponSystem` | Unchanged — individual ship weapon firing works as-is |
| `PointDefenseSystem` | Unchanged — capital ships auto-intercept incoming missiles |
| `TerritoryAssigner` | Listens to `FleetBattleResolvedEvent` to update system control |
| `PatrolRouteGenerator` | Generates routes for new faction fleets, updates routes on territory changes |
| `EncounterTableGenerator` | Extended to generate fleet encounter events based on sector activity |
| `PoliticalRelationGraph` | Fleet battles between factions can shift relations (war escalation, peace from exhaustion) |

### New ECS Systems

| System | Priority | Tick Rate | Role |
|---|---|---|---|
| `FleetSimulationSystem` | 2 | Every 5s | Off-screen fleet movement and battle resolution |
| `FleetExpansionSystem` | 3 | On-demand | Expand/collapse fleets near player |
| `FleetCommandSystem` | 5 | Every 1s | Processes admiral AI and player orders |
| `SquadronCoordinationSystem` | 6 | Every 0.5s | Squadron-level target coordination and focus fire |
| `FleetFormationSystem` | 7 | Every frame | Positions ships in formation slots |
| `FleetLODSystem` | 8 | Every 1s | Promotes/demotes ship AI tiers by distance |
| `FleetPostBattleSystem` | 15 | On-demand | Post-battle cleanup, salvage, repair |

### Package Structure

```
core/src/main/java/com/galacticodyssey/combat/fleet/
    components/
        FleetComponent.java
        FleetMemberComponent.java
        FleetFormationComponent.java
        FleetTacticsComponent.java
    systems/
        FleetCommandSystem.java
        FleetFormationSystem.java
        FleetSimulationSystem.java
        FleetExpansionSystem.java
        FleetLODSystem.java
        FleetPostBattleSystem.java
        SquadronCoordinationSystem.java
    data/
        FleetCompositionData.java
        FleetCompositionRegistry.java
        FormationTemplate.java
        FleetShipClass.java
        FleetDoctrine.java
        FleetState.java
        FleetRole.java
        FleetOrder.java
    events/
        FleetCreatedEvent.java
        FleetDestroyedEvent.java
        FleetOrderEvent.java
        FleetDoctrineChangedEvent.java
        FleetStateChangedEvent.java
        FleetExpandedEvent.java
        FleetCollapsedEvent.java
        FleetEngagementStartedEvent.java
        FleetBattleResolvedEvent.java
        ShipDisabledEvent.java
        ShipCapturedEvent.java
        EscapePodLaunchedEvent.java
        TerritoryChangedEvent.java
        FactionFleetMusteredEvent.java
        FactionReinforcementEvent.java
    ai/
        AdmiralBehaviorTree.java
        (behavior tree task/condition classes)
    ui/
        TacticalMapScreen.java
        TacticalMapInputProcessor.java
        FleetManagementPanel.java

core/src/main/resources/data/
    fleet_compositions.json
    formation_templates.json
```

### Persistence

All fleet components implement `Snapshotable<T>` following the existing pattern:
- `FleetSnapshot` — fleet ID, faction, doctrine, state, composition, aggregate stats, formation, orders
- `FleetMemberSnapshot` — fleet reference, squadron, role, slot index
- Collapsed fleets save/load as single entries; expanded fleets serialize each ship individually

Fleet state is fully recoverable across save/load cycles.
