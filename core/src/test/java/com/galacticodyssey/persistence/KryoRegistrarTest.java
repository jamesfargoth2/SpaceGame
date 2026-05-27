package com.galacticodyssey.persistence;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.galacticodyssey.persistence.snapshots.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KryoRegistrarTest {
    private Kryo kryo;

    @BeforeEach
    void setUp() {
        kryo = new Kryo();
        KryoRegistrar.register(kryo);
    }

    @Test
    void roundTripManifestData() {
        ManifestData original = new ManifestData("test-save", 42L,
            UUID.randomUUID(), UUID.randomUUID());

        byte[] bytes = serialize(original);
        ManifestData restored = deserialize(bytes, ManifestData.class);

        assertEquals("test-save", restored.saveName);
        assertEquals(42L, restored.galaxySeed);
        assertEquals(original.playerEntityId, restored.playerEntityId);
    }

    @Test
    void roundTripEntitySnapshot() {
        EntitySnapshot original = new EntitySnapshot(UUID.randomUUID());
        original.putSnapshot("Health", new HealthSnapshot());
        original.addTag("HostileTag");

        byte[] bytes = serialize(original);
        EntitySnapshot restored = deserialize(bytes, EntitySnapshot.class);

        assertEquals(original.entityId, restored.entityId);
        assertTrue(restored.hasTag("HostileTag"));
        assertNotNull(restored.componentSnapshots.get("Health"));
    }

    @Test
    void roundTripTransformSnapshot() {
        TransformSnapshot original = new TransformSnapshot(
            1_000_000.5, 2_000_000.5, 3_000_000.5,
            0f, 0.707f, 0f, 0.707f);

        byte[] bytes = serialize(original);
        TransformSnapshot restored = deserialize(bytes, TransformSnapshot.class);

        assertEquals(1_000_000.5, restored.galaxyX, 1e-10);
        assertEquals(0.707f, restored.rotY, 1e-5f);
    }

    // -------------------------------------------------------------------------
    // Additional coverage: all other snapshot types
    // -------------------------------------------------------------------------

    @Test
    void roundTripHealthSnapshot() {
        HealthSnapshot original = new HealthSnapshot();
        original.currentHP = 75f;
        original.maxHP = 100f;
        original.alive = true;

        HealthSnapshot restored = roundTrip(original, HealthSnapshot.class);

        assertEquals(75f, restored.currentHP, 1e-5f);
        assertEquals(100f, restored.maxHP, 1e-5f);
        assertTrue(restored.alive);
    }

    @Test
    void roundTripPlayerStateSnapshot() {
        PlayerStateSnapshot original = new PlayerStateSnapshot();
        original.currentMode = com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode.PILOTING;
        original.currentShipId = UUID.randomUUID();

        PlayerStateSnapshot restored = roundTrip(original, PlayerStateSnapshot.class);

        assertEquals(original.currentMode, restored.currentMode);
        assertEquals(original.currentShipId, restored.currentShipId);
    }

    @Test
    void roundTripShieldSnapshot() {
        ShieldSnapshot original = new ShieldSnapshot();
        original.currentShield = 80f;
        original.maxShield = 100f;
        original.shieldType = com.galacticodyssey.combat.CombatEnums.ShieldType.ENERGY;

        ShieldSnapshot restored = roundTrip(original, ShieldSnapshot.class);

        assertEquals(80f, restored.currentShield, 1e-5f);
        assertEquals(com.galacticodyssey.combat.CombatEnums.ShieldType.ENERGY, restored.shieldType);
    }

    @Test
    void roundTripStatusEffectsSnapshot() {
        StatusEffectsSnapshot original = new StatusEffectsSnapshot();
        StatusEffectsSnapshot.ActiveEffectData effect = new StatusEffectsSnapshot.ActiveEffectData();
        effect.type = com.galacticodyssey.combat.CombatEnums.StatusEffectType.BLEEDING;
        effect.remainingDuration = 5f;
        effect.magnitude = 2f;
        effect.stacks = 1;
        original.activeEffects.add(effect);

        StatusEffectsSnapshot restored = roundTrip(original, StatusEffectsSnapshot.class);

        assertEquals(1, restored.activeEffects.size());
        assertEquals(com.galacticodyssey.combat.CombatEnums.StatusEffectType.BLEEDING,
            restored.activeEffects.get(0).type);
        assertEquals(5f, restored.activeEffects.get(0).remainingDuration, 1e-5f);
    }

    @Test
    void roundTripStructuralIntegritySnapshot() {
        StructuralIntegritySnapshot original = new StructuralIntegritySnapshot();
        StructuralIntegritySnapshot.ZoneData zone = new StructuralIntegritySnapshot.ZoneData();
        zone.zoneName = "Hull-A";
        zone.integrity = 0.9f;
        zone.isBreached = false;
        original.zones.add(zone);

        StructuralIntegritySnapshot restored = roundTrip(original, StructuralIntegritySnapshot.class);

        assertEquals(1, restored.zones.size());
        assertEquals("Hull-A", restored.zones.get(0).zoneName);
        assertEquals(0.9f, restored.zones.get(0).integrity, 1e-5f);
    }

    @Test
    void roundTripItemSnapshot() {
        ItemSnapshot original = new ItemSnapshot();
        original.itemId = "pistol_01";
        original.displayName = "Nova Pistol";
        original.stackCount = 1;
        original.weight = 1.2f;

        ItemSnapshot restored = roundTrip(original, ItemSnapshot.class);

        assertEquals("pistol_01", restored.itemId);
        assertEquals("Nova Pistol", restored.displayName);
        assertEquals(1.2f, restored.weight, 1e-5f);
    }

    @Test
    void roundTripCombatAISnapshot() {
        CombatAISnapshot original = new CombatAISnapshot();
        original.aggroRange = 50f;
        original.aggression = 0.8f;
        original.currentTargetId = UUID.randomUUID();

        CombatAISnapshot restored = roundTrip(original, CombatAISnapshot.class);

        assertEquals(50f, restored.aggroRange, 1e-5f);
        assertEquals(original.currentTargetId, restored.currentTargetId);
    }

    @Test
    void roundTripSaveBundle() {
        SaveBundle original = new SaveBundle();
        original.manifest = new ManifestData("my-save", 99L, UUID.randomUUID(), UUID.randomUUID());
        EntitySnapshot player = new EntitySnapshot(UUID.randomUUID());
        original.playerSnapshot = player;

        SaveBundle restored = roundTrip(original, SaveBundle.class);

        assertNotNull(restored.manifest);
        assertEquals("my-save", restored.manifest.saveName);
        assertNotNull(restored.playerSnapshot);
        assertEquals(player.entityId, restored.playerSnapshot.entityId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Output output = new Output(baos)) {
            kryo.writeObject(output, obj);
        }
        return baos.toByteArray();
    }

    private <T> T deserialize(byte[] bytes, Class<T> type) {
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            return kryo.readObject(input, type);
        }
    }

    private <T> T roundTrip(T obj, Class<T> type) {
        return deserialize(serialize(obj), type);
    }
}
