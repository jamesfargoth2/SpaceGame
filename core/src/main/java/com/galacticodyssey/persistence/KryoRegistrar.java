package com.galacticodyssey.persistence;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.CollectionSerializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.esotericsoftware.kryo.serializers.MapSerializer;
import com.galacticodyssey.persistence.snapshots.*;

import java.util.*;

/**
 * Single-point Kryo type registration with fixed integer IDs.
 * <p>
 * IDs must never be changed or reused — doing so breaks binary compatibility with
 * existing save files. Append new registrations at the end of each range.
 * <p>
 * Ranges:
 * <pre>
 *  10–19  JDK types
 *  20–29  Persistence core (ManifestData, SaveBundle, EntitySnapshot, WorldModification)
 *  30–39  Player snapshots
 *  40–49  Combat snapshots
 *  50–59  Equipment snapshots
 *  60–69  Ship snapshots
 *  70–79  Ship subsystem snapshots
 *  80–89  NPC snapshots
 * 100–109  Enum types used as actual enum fields in snapshots
 * </pre>
 */
public final class KryoRegistrar {

    private KryoRegistrar() {}

    /**
     * Registers all snapshot POJOs, inner classes, and enum types with fixed IDs.
     * Call this once on any {@link Kryo} instance used for save I/O or KryoNet transport.
     *
     * @param kryo the Kryo instance to configure
     */
    public static void register(Kryo kryo) {
        kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
        kryo.setRegistrationRequired(true);

        // --- JDK types ---
        // UUID, ArrayList, HashMap, HashSet, EnumMap cannot use CompatibleFieldSerializer
        // (Java module system blocks reflective field access on java.util classes).
        // Register each with its proper built-in Kryo serializer.
        kryo.register(UUID.class, new DefaultSerializers.UUIDSerializer(), 10);
        kryo.register(ArrayList.class, new CollectionSerializer<>(), 11);
        kryo.register(HashMap.class, new MapSerializer<>(), 12);
        kryo.register(HashSet.class, new CollectionSerializer<>(), 13);
        kryo.register(EnumMap.class, new MapSerializer<>(), 14);
        kryo.register(byte[].class, 15);
        kryo.register(float[].class, 16);
        kryo.register(int[].class, 17);
        kryo.register(String[].class, 18);

        // --- Persistence core ---
        kryo.register(ManifestData.class, 20);
        kryo.register(SaveBundle.class, 21);
        kryo.register(EntitySnapshot.class, 22);
        kryo.register(WorldModification.class, 23);
        kryo.register(DiscoveredIds.class, 24);

        // --- Player snapshots ---
        kryo.register(TransformSnapshot.class, 30);
        kryo.register(HealthSnapshot.class, 31);
        kryo.register(PlayerStateSnapshot.class, 32);
        kryo.register(MovementStateSnapshot.class, 33);
        kryo.register(FPSCameraSnapshot.class, 34);
        kryo.register(PlayerWalletSnapshot.class, 35);

        // --- Combat snapshots ---
        kryo.register(ShieldSnapshot.class, 40);
        kryo.register(ArmorSnapshot.class, 41);
        kryo.register(RangedWeaponSnapshot.class, 42);
        kryo.register(MeleeWeaponSnapshot.class, 43);
        kryo.register(WeaponInventorySnapshot.class, 44);
        kryo.register(StatusEffectsSnapshot.class, 45);
        kryo.register(StatusEffectsSnapshot.ActiveEffectData.class, 46);

        // --- Equipment snapshots ---
        kryo.register(ItemSnapshot.class, 50);
        kryo.register(InventorySnapshot.class, 51);
        kryo.register(EquipmentSlotsSnapshot.class, 52);

        // --- Ship snapshots ---
        kryo.register(ShipDataSnapshot.class, 60);
        kryo.register(ShipFlightSnapshot.class, 61);
        kryo.register(CargoBaySnapshot.class, 62);
        kryo.register(EngineSpecSnapshot.class, 63);
        kryo.register(FuelTankSnapshot.class, 64);
        kryo.register(VehicleBaySnapshot.class, 65);

        // --- Ship subsystem snapshots ---
        kryo.register(ThermalStateSnapshot.class, 70);
        kryo.register(StructuralIntegritySnapshot.class, 71);
        kryo.register(StructuralIntegritySnapshot.ZoneData.class, 72);
        kryo.register(CompartmentAtmosphereSnapshot.class, 73);
        kryo.register(DockingStateSnapshot.class, 74);
        kryo.register(ShipSubsystemsSnapshot.class, 75);
        kryo.register(ShipSubsystemsSnapshot.Entry.class, 76);
        kryo.register(BoardingOperationSnapshot.class, 77);
        kryo.register(OwnedShipSnapshot.class, 78);
        kryo.register(PlayerGarageSnapshot.class, 79);
        kryo.register(PlayerGarageSnapshot.Entry.class, 82);

        // --- NPC snapshots ---
        kryo.register(CombatAISnapshot.class, 80);
        kryo.register(SquadSnapshot.class, 81);

        // --- Enum types used as actual enum fields in snapshots ---
        registerEnums(kryo);
    }

    private static void registerEnums(Kryo kryo) {
        kryo.register(com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode.class, 100);
        kryo.register(com.galacticodyssey.combat.CombatEnums.ShieldType.class, 101);
        kryo.register(com.galacticodyssey.combat.CombatEnums.DamageType.class, 102);
        kryo.register(com.galacticodyssey.combat.CombatEnums.HitRegion.class, 103);
        kryo.register(com.galacticodyssey.combat.CombatEnums.FiringMode.class, 104);
        kryo.register(com.galacticodyssey.combat.CombatEnums.WeightClass.class, 105);
        kryo.register(com.galacticodyssey.combat.CombatEnums.AttackDirection.class, 106);
        kryo.register(com.galacticodyssey.combat.CombatEnums.StatusEffectType.class, 107);
    }
}
