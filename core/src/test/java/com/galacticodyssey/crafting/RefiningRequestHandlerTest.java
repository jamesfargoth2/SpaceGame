package com.galacticodyssey.crafting;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.data.RefiningRecipe;
import com.galacticodyssey.crafting.data.RefiningRecipeRegistry;
import com.galacticodyssey.crafting.events.RefiningFailedEvent;
import com.galacticodyssey.crafting.events.RefiningRequestEvent;
import com.galacticodyssey.crafting.events.RefiningStartedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.systems.DefaultSkillProvider;
import com.galacticodyssey.crafting.systems.RefiningRequestHandler;
import com.galacticodyssey.core.EventBus;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningRequestHandlerTest {

    private Engine engine;
    private EventBus eventBus;
    private MaterialRegistry materialRegistry;
    private RefiningRecipeRegistry recipeRegistry;
    private RefiningRequestHandler handler;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();

        materialRegistry = new MaterialRegistry();
        materialRegistry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", "iron_ore"));
        materialRegistry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("iron_ingot", "Iron Ingot",
            MaterialTier.REFINED, MaterialCategory.METAL, 1.0f, 0.5f, 50, "", null));

        recipeRegistry = new RefiningRecipeRegistry();
        recipeRegistry.register(new RefiningRecipe("process_iron_ore", "Process Iron Ore",
            RecipeCategory.PROCESSING, 1,
            List.of(new RecipeInput("iron_ore", 5)),
            List.of(new RecipeOutput("iron_concentrate", 3)),
            30.0f, 10f));
        recipeRegistry.register(new RefiningRecipe("refine_iron", "Refine Iron",
            RecipeCategory.REFINEMENT, 2,
            List.of(new RecipeInput("iron_concentrate", 4)),
            List.of(new RecipeOutput("iron_ingot", 2)),
            60.0f, 20f));

        RefiningConfig config = new RefiningConfig(0.005f, "engineering");
        handler = new RefiningRequestHandler(eventBus, recipeRegistry, materialRegistry,
            config, new DefaultSkillProvider());
        engine.addSystem(handler);
    }

    private Entity createRefineryEntity(int tier, int queueSize) {
        Entity entity = new Entity();
        entity.add(new RefineryComponent(tier, queueSize, 1.0f, 10f));
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        entity.add(storage);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    void validRequest_consumesInputsAndQueuesJob() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 10);

        AtomicReference<RefiningStartedEvent> started = new AtomicReference<>();
        eventBus.subscribe(RefiningStartedEvent.class, started::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertNotNull(started.get());
        assertEquals(5, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_ore"));
        assertEquals(1, entity.getComponent(RefineryComponent.class).getJobQueue().size());
    }

    @Test
    void noRefinery_firesFailedEvent() {
        Entity entity = new Entity();
        MaterialStorageComponent storage = new MaterialStorageComponent(1000f, 1000f, materialRegistry);
        entity.add(storage);
        engine.addEntity(entity);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.NO_REFINERY, failed.get().reason);
    }

    @Test
    void tierTooLow_firesFailedEvent() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_concentrate", 10);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "refine_iron"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.TIER_TOO_LOW, failed.get().reason);
    }

    @Test
    void insufficientMaterials_firesFailedEvent() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 2);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.INSUFFICIENT_MATERIALS, failed.get().reason);
        assertEquals(2, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_ore"));
    }

    @Test
    void queueFull_firesFailedEvent() {
        Entity entity = createRefineryEntity(1, 1);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 20);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.QUEUE_FULL, failed.get().reason);
    }

    @Test
    void unknownRecipe_firesFailedEvent() {
        Entity entity = createRefineryEntity(3, 4);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "nonexistent_recipe"));
        engine.update(0.016f);

        assertNotNull(failed.get());
        assertEquals(RefiningFailureReason.RECIPE_NOT_FOUND, failed.get().reason);
    }

    @Test
    void batchCount_queuesMultipleJobs() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 15);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore", 3));
        engine.update(0.016f);

        assertEquals(3, entity.getComponent(RefineryComponent.class).getJobQueue().size());
        assertEquals(0, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_ore"));
    }

    @Test
    void batchCount_partialBatch_queuesAsManyAsPossible() {
        Entity entity = createRefineryEntity(1, 4);
        entity.getComponent(MaterialStorageComponent.class).tryAdd("iron_ore", 7);

        AtomicReference<RefiningFailedEvent> failed = new AtomicReference<>();
        eventBus.subscribe(RefiningFailedEvent.class, failed::set);

        eventBus.publish(new RefiningRequestEvent(entity, "process_iron_ore", 3));
        engine.update(0.016f);

        assertEquals(1, entity.getComponent(RefineryComponent.class).getJobQueue().size());
        assertEquals(2, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_ore"));
        assertNotNull(failed.get());
    }
}
