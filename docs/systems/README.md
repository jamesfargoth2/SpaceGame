# System Documentation Index

Reference docs for each implemented system in Galactic Odyssey. For game design intent and feature scope, see [../DESIGN.md](../DESIGN.md).

| Document | What it covers |
|---|---|
| [core.md](core.md) | Floating-origin coordinates, EventBus, ECS bootstrap, Bullet physics, gravity, black holes, solar physics, tether/cable physics |
| [combat.md](combat.md) | Damage pipeline, hitscan/projectile/melee weapons, explosions, status effects, combat AI, behavior tree tasks |
| [ship.md](ship.md) | Procedural hull/interior generation, 6DOF flight, ship weapons, structural integrity, thermal management, life support, flooding, fluid dynamics, docking |
| [player.md](player.md) | FPS movement, input aggregation, camera, ADS, recoil, screen shake, interaction, pilot transition, skills |
| [economy.md](economy.md) | Planetary production, dynamic pricing, buy/sell transactions, commodity data |
| [npc.md](npc.md) | Procedural NPC generation, crew assignment, morale, experience/rank progression |
| [mission.md](mission.md) | Objective tracking, procedural job system, saga quest graph |
| [galaxy.md](galaxy.md) | Galaxy chunk streaming, star generation, nebulae, derelicts, factions, anomalies, seed reproducibility |
| [planet.md](planet.md) | Surface terrain, wheeled vehicle physics, environmental hazards, cave systems, craters |
| [water.md](water.md) | Player swimming/diving, ocean waves, surface vessels, weather, submarine ballast/flooding/pressure |
| [persistence.md](persistence.md) | Save/load pipeline, entity snapshotting, save bundles, migration, auto-save |
| [vfx-audio.md](vfx-audio.md) | Particle effects, event→effect bindings, 3D positional audio, dynamic music, ambient blending |
| [equipment.md](equipment.md) | Item model, inventory, equipment slots, loot generation, weapon assembly |
| [mech.md](mech.md) | Multi-legged mech gait, independent torso orientation, per-foot ground contact, joint limits |
