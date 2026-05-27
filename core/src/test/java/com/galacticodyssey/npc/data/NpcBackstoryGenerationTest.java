package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.BackstoryHook;
import com.galacticodyssey.npc.HookType;
import com.galacticodyssey.npc.NPCRole;
import com.galacticodyssey.npc.components.NpcBackstoryComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NpcBackstoryGenerationTest {

    private NpcDataRegistry registry;
    private NpcGenerator generator;
    private Engine engine;

    private static final ComponentMapper<NpcBackstoryComponent> BACKSTORY_M =
        ComponentMapper.getFor(NpcBackstoryComponent.class);

    private static final long FIXED_SEED = 12345L;

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();
        engine = new Engine();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.portraitIds.addAll(List.of("portrait_human_01"));
        registry.registerSpecies(human);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        registry.registerBackground(military);

        registry.registerNames("human",
            List.of("James", "Elena"),
            List.of("Voss", "Chen"));

        generator = new NpcGenerator(registry);
    }

    @Test
    void generatedNpcHasBackstoryComponent() {
        Entity npc = generator.generate(engine, FIXED_SEED);
        assertNotNull(BACKSTORY_M.get(npc));
    }

    @Test
    void backstoryHasOneToTwoHooks() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcBackstoryComponent b = BACKSTORY_M.get(npc);
            assertTrue(b.hooks.size() >= 1 && b.hooks.size() <= 2,
                "NPC " + i + " has " + b.hooks.size() + " hooks, expected 1-2");
        }
    }

    @Test
    void hooksHaveUniqueTypes() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcBackstoryComponent b = BACKSTORY_M.get(npc);
            Set<HookType> types = new HashSet<>();
            for (BackstoryHook hook : b.hooks) {
                assertTrue(types.add(hook.type),
                    "NPC " + i + " has duplicate hook type: " + hook.type);
            }
        }
    }

    @Test
    void pirateCaptainAlwaysHasWantedCriminalHook() {
        for (int i = 0; i < 100; i++) {
            Entity npc = generator.generate(engine,
                SeedDeriver.forId(FIXED_SEED, i), "human", "military", NPCRole.PIRATE_CAPTAIN);
            NpcBackstoryComponent b = BACKSTORY_M.get(npc);
            boolean hasWanted = false;
            for (BackstoryHook hook : b.hooks) {
                if (hook.type == HookType.WANTED_CRIMINAL) {
                    hasWanted = true;
                    break;
                }
            }
            assertTrue(hasWanted,
                "PIRATE_CAPTAIN " + i + " missing WANTED_CRIMINAL hook");
        }
    }

    @Test
    void backstoryIsDeterministic() {
        Entity npc1 = generator.generate(engine, FIXED_SEED);
        Entity npc2 = generator.generate(engine, FIXED_SEED);
        NpcBackstoryComponent b1 = BACKSTORY_M.get(npc1);
        NpcBackstoryComponent b2 = BACKSTORY_M.get(npc2);
        assertEquals(b1.hooks.size(), b2.hooks.size());
        for (int i = 0; i < b1.hooks.size(); i++) {
            assertEquals(b1.hooks.get(i).type, b2.hooks.get(i).type);
            assertEquals(b1.hooks.get(i).questSeed, b2.hooks.get(i).questSeed);
        }
    }

    @Test
    void hookSummariesAreNonEmpty() {
        Entity npc = generator.generate(engine, FIXED_SEED);
        NpcBackstoryComponent b = BACKSTORY_M.get(npc);
        for (BackstoryHook hook : b.hooks) {
            assertNotNull(hook.summary);
            assertFalse(hook.summary.isEmpty());
        }
    }
}
