package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HostileTagComponent;
import com.galacticodyssey.combat.components.MeleeWeaponComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.ShieldComponent;
import com.galacticodyssey.combat.components.SquadComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.persistence.snapshots.ArmorSnapshot;
import com.galacticodyssey.persistence.snapshots.CargoBaySnapshot;
import com.galacticodyssey.persistence.snapshots.CombatAISnapshot;
import com.galacticodyssey.persistence.snapshots.CompartmentAtmosphereSnapshot;
import com.galacticodyssey.persistence.snapshots.DockingStateSnapshot;
import com.galacticodyssey.persistence.snapshots.EngineSpecSnapshot;
import com.galacticodyssey.persistence.snapshots.EquipmentSlotsSnapshot;
import com.galacticodyssey.persistence.snapshots.FPSCameraSnapshot;
import com.galacticodyssey.persistence.snapshots.FuelTankSnapshot;
import com.galacticodyssey.persistence.snapshots.HealthSnapshot;
import com.galacticodyssey.persistence.snapshots.InventorySnapshot;
import com.galacticodyssey.persistence.snapshots.MeleeWeaponSnapshot;
import com.galacticodyssey.persistence.snapshots.MovementStateSnapshot;
import com.galacticodyssey.persistence.snapshots.PlayerStateSnapshot;
import com.galacticodyssey.persistence.snapshots.PlayerStatsSnapshot;
import com.galacticodyssey.persistence.snapshots.PlayerWalletSnapshot;
import com.galacticodyssey.persistence.snapshots.PowerStateSnapshot;
import com.galacticodyssey.persistence.snapshots.RangedWeaponSnapshot;
import com.galacticodyssey.persistence.snapshots.ShieldSnapshot;
import com.galacticodyssey.persistence.snapshots.ShipCargoSnapshot;
import com.galacticodyssey.persistence.snapshots.ShipDataSnapshot;
import com.galacticodyssey.persistence.snapshots.ShipFlightSnapshot;
import com.galacticodyssey.persistence.snapshots.ShipLoadoutSnapshot;
import com.galacticodyssey.persistence.snapshots.ShipPilotAISnapshot;
import com.galacticodyssey.persistence.snapshots.SquadSnapshot;
import com.galacticodyssey.persistence.snapshots.StatusEffectsSnapshot;
import com.galacticodyssey.persistence.snapshots.BoardingOperationSnapshot;
import com.galacticodyssey.persistence.snapshots.ShipSubsystemsSnapshot;
import com.galacticodyssey.persistence.snapshots.StructuralIntegritySnapshot;
import com.galacticodyssey.persistence.snapshots.ThermalStateSnapshot;
import com.galacticodyssey.persistence.snapshots.WeaponInventorySnapshot;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;
import com.galacticodyssey.ship.components.EngineSpecComponent;
import com.galacticodyssey.ship.components.FuelTankComponent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.VehicleBayComponent;
import com.galacticodyssey.persistence.snapshots.VehicleBaySnapshot;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.OwnedShipComponent;
import com.galacticodyssey.ship.boarding.PlayerGarageComponent;
import com.galacticodyssey.persistence.snapshots.OwnedShipSnapshot;
import com.galacticodyssey.persistence.snapshots.PlayerGarageSnapshot;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.docking.DockingStateComponent;
import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.power.PowerStateComponent;
import com.galacticodyssey.ship.lifesupport.CompartmentAtmosphereComponent;
import com.galacticodyssey.ship.structure.StructuralIntegrityComponent;
import com.galacticodyssey.ship.thermal.ThermalStateComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Maps snapshot type-name strings to component factory + restore logic, replacing
 * reflection with explicit registrations for each persisted component type.
 *
 * <p>Type names correspond to the simple class name with the {@code "Component"}
 * suffix stripped (e.g. {@code "Health"} → {@link HealthComponent}), matching the
 * convention used by {@link EntitySnapshotBuilder}.</p>
 */
public final class SnapshotComponentRegistry {

    /** Map from type-name to a (snapshotClass, factory) entry for Snapshotable components. */
    private static final Map<String, RestoreEntry<?>> REGISTRY = new HashMap<>();

    /** Map from full class-name to a no-arg factory for tag (marker) components. */
    private static final Map<String, Supplier<Component>> TAG_REGISTRY = new HashMap<>();

    static {
        // ----- Player -----
        register("Health",        HealthSnapshot.class,        HealthComponent::new);
        register("PlayerState",   PlayerStateSnapshot.class,   PlayerStateComponent::new);
        register("MovementState", MovementStateSnapshot.class, MovementStateComponent::new);
        register("FPSCamera",     FPSCameraSnapshot.class,     FPSCameraComponent::new);
        register("PlayerWallet",  PlayerWalletSnapshot.class,  PlayerWalletComponent::new);
        register("PlayerStats",   PlayerStatsSnapshot.class,   PlayerStatsComponent::new);

        // ----- Combat -----
        register("Shield",          ShieldSnapshot.class,          ShieldComponent::new);
        register("Armor",           ArmorSnapshot.class,           ArmorComponent::new);
        register("RangedWeapon",    RangedWeaponSnapshot.class,    RangedWeaponComponent::new);
        register("MeleeWeapon",     MeleeWeaponSnapshot.class,     MeleeWeaponComponent::new);
        register("WeaponInventory", WeaponInventorySnapshot.class, WeaponInventoryComponent::new);
        register("StatusEffects",   StatusEffectsSnapshot.class,   StatusEffectsComponent::new);
        register("CombatAI",        CombatAISnapshot.class,        CombatAIComponent::new);
        register("Squad",           SquadSnapshot.class,           SquadComponent::new);
        register("ShipPilotAI",     ShipPilotAISnapshot.class,     ShipPilotAIComponent::new);

        // ----- Equipment -----
        register("Inventory",       InventorySnapshot.class,
                snap -> new InventoryComponent(snap.gridWidth, snap.gridHeight, snap.maxWeight));
        register("EquipmentSlots",  EquipmentSlotsSnapshot.class,  EquipmentSlotsComponent::new);

        // ----- Ship core -----
        register("ShipData",    ShipDataSnapshot.class,    ShipDataComponent::new);
        register("ShipFlight",  ShipFlightSnapshot.class,  ShipFlightComponent::new);
        register("CargoBay",    CargoBaySnapshot.class,    CargoBayComponent::new);
        register("EngineSpec",  EngineSpecSnapshot.class,  EngineSpecComponent::new);
        register("FuelTank",    FuelTankSnapshot.class,    FuelTankComponent::new);
        register("ShipLoadout", ShipLoadoutSnapshot.class, ShipLoadoutComponent::new);
        register("ShipCargo",   ShipCargoSnapshot.class,   ShipCargoComponent::new);

        // ----- Ship subsystems -----
        register("ThermalState",           ThermalStateSnapshot.class,           ThermalStateComponent::new);
        register("StructuralIntegrity",    StructuralIntegritySnapshot.class,    StructuralIntegrityComponent::new);
        register("CompartmentAtmosphere",  CompartmentAtmosphereSnapshot.class,  CompartmentAtmosphereComponent::new);
        register("DockingState",           DockingStateSnapshot.class,           DockingStateComponent::new);
        register("PowerState",             PowerStateSnapshot.class,             PowerStateComponent::new);
        register("ShipSubsystems",         ShipSubsystemsSnapshot.class,         ShipSubsystemsComponent::new);
        register("BoardingOperation",      BoardingOperationSnapshot.class,      BoardingOperationComponent::new);
        register("OwnedShip",    OwnedShipSnapshot.class,    OwnedShipComponent::new);
        register("PlayerGarage", PlayerGarageSnapshot.class, PlayerGarageComponent::new);
        register("VehicleBay",             VehicleBaySnapshot.class,             VehicleBayComponent::new);

        // ----- Tags -----
        TAG_REGISTRY.put("HostileTagComponent", HostileTagComponent::new);
        TAG_REGISTRY.put("PlayerTagComponent",  PlayerTagComponent::new);
    }

    private SnapshotComponentRegistry() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Look up {@code typeName} in the registry, create a fresh component via its
     * factory, and call {@link Snapshotable#restoreFromSnapshot(Object)} with the
     * supplied snapshot object.
     *
     * @param typeName the canonical type name (e.g. {@code "Health"})
     * @param snapshot the raw snapshot object stored in {@link EntitySnapshot}
     * @return the populated component, or {@code null} if the type is not registered
     */
    public static Component createAndRestore(String typeName, Object snapshot) {
        RestoreEntry<?> entry = REGISTRY.get(typeName);
        if (entry == null) return null;
        return entry.createAndRestore(snapshot);
    }

    /**
     * Create a fresh tag (marker) component by its full class simple name.
     *
     * @param tagName the full simple class name (e.g. {@code "HostileTagComponent"})
     * @return a new instance, or {@code null} if the tag is not registered
     */
    public static Component createTag(String tagName) {
        Supplier<Component> supplier = TAG_REGISTRY.get(tagName);
        return supplier != null ? supplier.get() : null;
    }

    // -------------------------------------------------------------------------
    // Registration helpers
    // -------------------------------------------------------------------------

    /** Register a component that has a no-arg constructor. */
    @SuppressWarnings("unchecked")
    private static <S> void register(String name, Class<S> snapshotClass,
                                      Supplier<? extends Component> factory) {
        REGISTRY.put(name, new RestoreEntry<>(snapshotClass, snap -> factory.get()));
    }

    /**
     * Register a component whose constructor requires snapshot data (e.g. dimensions).
     * The supplied factory receives the typed snapshot and returns a freshly constructed
     * component; {@link Snapshotable#restoreFromSnapshot} is then called to populate
     * mutable state.
     */
    @SuppressWarnings("unchecked")
    private static <S> void register(String name, Class<S> snapshotClass,
                                      Function<S, ? extends Component> factory) {
        REGISTRY.put(name, new RestoreEntry<>(snapshotClass, snap -> factory.apply(snapshotClass.cast(snap))));
    }

    // -------------------------------------------------------------------------
    // Internal entry
    // -------------------------------------------------------------------------

    private static final class RestoreEntry<S> {
        final Class<S> snapshotClass;
        /** Factory receives the raw (already cast) snapshot object. */
        final Function<Object, ? extends Component> factory;

        RestoreEntry(Class<S> snapshotClass, Function<Object, ? extends Component> factory) {
            this.snapshotClass = snapshotClass;
            this.factory = factory;
        }

        @SuppressWarnings("unchecked")
        Component createAndRestore(Object snapshot) {
            Component component = factory.apply(snapshot);
            if (component instanceof Snapshotable<?>) {
                ((Snapshotable<S>) component).restoreFromSnapshot(snapshotClass.cast(snapshot));
            }
            return component;
        }
    }
}
