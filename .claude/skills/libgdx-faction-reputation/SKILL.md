---
name: libgdx-faction-reputation
description: >
  Enforces correct faction reputation tracking, reputation-gated content,
  faction-based NPC behavior, and diplomatic standing mechanics for a libGDX
  3D space game using Ashley ECS. Use this skill whenever writing or modifying:
  faction definitions, reputation score changes, reputation thresholds that
  unlock or lock content, faction-specific pricing, faction military response
  to hostile players, pre-FTL civilization interactions, or any code that
  checks faction standing to gate services, missions, or dialogue. Also
  triggers when adding inter-faction diplomacy, smuggling detection, or
  faction alignment for settlements and stations.
---

# libGDX Faction & Reputation System

## Reputation Scale

Reputation is a float in [-100, +100] per faction per player. Thresholds gate gameplay:

| Range | Standing | Effects |
|---|---|---|
| -100 to -50 | Hostile | Kill-on-sight, no docking, military pursuit patrols |
| -50 to 0 | Unfriendly | Denied most services, 50% price markup |
| 0 | Neutral | Basic trading and docking permitted |
| 0 to +25 | Friendly | 10% discount, additional inventory available |
| +25 to +50 | Allied | Military hardware access, faction base docking |
| +50 to +75 | Honored | Unique ships, elite crew recruits, story missions |
| +75 to +100 | Exalted | Faction leadership influence, endgame content |

## Faction Data

Define factions in data files, not code. The runtime model:

```java
public class FactionData {
    public String   id;
    public String   displayName;
    public FactionType type;
    public TechLevel techLevel;
    public Culture   culture;
    public Hostility baseHostility;
    public ObjectMap<String, Float> diplomacy = new ObjectMap<>();
}

public enum FactionType { MAJOR_POWER, MINOR, ALIEN_GOVERNMENT, LAWLESS }
public enum TechLevel { PRE_INDUSTRIAL, INDUSTRIAL, SPACE_AGE, FTL, POST_SINGULARITY }
public enum Culture { MILITARISTIC, DIPLOMATIC, MERCANTILE, ISOLATIONIST, NOMADIC, HIVE }
public enum Hostility { PACIFIST, CAUTIOUS, NEUTRAL, AGGRESSIVE, XENOPHOBIC }
```

## Player Reputation Component

```java
public class ReputationComponent implements Component, Pool.Poolable {
    public ObjectMap<String, Float> standings = new ObjectMap<>();

    public float getStanding(String factionId) {
        return standings.get(factionId, 0f);
    }

    public void modify(String factionId, float delta) {
        float current = getStanding(factionId);
        standings.put(factionId, MathUtils.clamp(current + delta, -100f, 100f));
    }

    @Override public void reset() { standings.clear(); }
}
```

## Reputation Change Events

Never modify reputation directly -- post events so all interested systems react:

```java
public class ReputationChangeEvent {
    public Entity  player;
    public String  factionId;
    public float   delta;
    public String  reason;
}
```

Standard deltas:

| Action | Delta | Notes |
|---|---|---|
| Complete faction mission | +5 to +20 | Scales with mission difficulty |
| Trade transaction | +1 | Each completed trade |
| Destroy faction ship | -10 to -50 | Scales with ship class |
| Smuggle contraband (caught) | -5 to -30 | Scales with cargo value |
| Aid faction enemies | -10 to -25 | Detected via faction alliance data |
| Diplomacy skill check | +variable | Bonus = diplomacyLevel * 0.5 |

## Reputation-Gated Services

```java
public class FactionServiceComponent implements Component {
    public String  factionId;
    public float   requiredStanding;
    public ServiceType serviceType;
}

public boolean canAccess(Entity player, FactionServiceComponent service) {
    ReputationComponent rep = Mappers.reputation.get(player);
    return rep.getStanding(service.factionId) >= service.requiredStanding;
}
```

## Economic Impact

```java
public float getPriceModifier(float standing) {
    if (standing < -50f) return 1.5f;
    if (standing < 0f)   return 1.25f;
    if (standing < 25f)  return 1.0f;
    if (standing < 50f)  return 0.9f;
    return 0.8f;
}
```

## NPC Behavior by Standing

At hostile standing, faction patrols hunt the player:

```java
public void onSectorEnter(SectorEnterEvent event) {
    String controllingFaction = event.sector.controllingFaction;
    if (controllingFaction == null) return;
    float standing = getPlayerStanding(event.player, controllingFaction);
    if (standing < -50f) spawnPursuitPatrol(event.sector, controllingFaction);
}
```

## Pre-FTL Civilizations

| Choice | Reputation Effect | Gameplay Result |
|---|---|---|
| Observe | +5 Science faction, +2 general | Research data reward |
| Uplift | +20 with species, -10 isolationist factions | New trade partner |
| Exploit | -15 general, -30 diplomatic factions | Immediate resources |
| Ignore | No change | No reward |

## Inter-Faction Diplomacy

```java
public void applyDiplomaticRipple(String helpedFaction, float baseDelta,
                                   ReputationComponent playerRep) {
    FactionData helped = factionRegistry.get(helpedFaction);
    for (ObjectMap.Entry<String, Float> entry : helped.diplomacy) {
        float ripple = baseDelta * (entry.value / 200f);
        playerRep.modify(entry.key, ripple);
    }
}
```

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Hostile threshold | -50 | KOS behavior activates |
| Friendly threshold | +25 | Discounts and faction missions |
| Trade rep gain | +1 | Per transaction |
| Diplomacy bonus | 0.5 per level | Rep gain multiplier |
| Price markup (hostile) | 50% | Buy price increase |
| Ripple strength | 50% of base | Allied/enemy faction effect |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Hardcoding faction IDs | Load from data files |
| Modifying rep without events | Always post ReputationChangeEvent |
| Forgetting clamp | Always clamp to [-100, +100] |
| No diplomatic ripple | Helping one faction must affect allies/enemies |
| KOS at -49 | Threshold is -50; use < not <= |
| Pre-FTL with no consequence | Every choice needs rep + gameplay effects |
