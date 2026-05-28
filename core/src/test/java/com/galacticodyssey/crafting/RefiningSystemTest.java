package com.galacticodyssey.crafting;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.crafting.components.MaterialStorageComponent;
import com.galacticodyssey.crafting.components.RefineryComponent;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.crafting.events.RefiningCompletedEvent;
import com.galacticodyssey.crafting.events.RefiningQueueChangedEvent;
import com.galacticodyssey.crafting.systems.RefiningSystem;
import com.galacticodyssey.core.EventBus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefiningSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private MaterialRegistry materialRegistry;
    private RefiningSystem system;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        materialRegistry = new MaterialRegistry();
        materialRegistry.register(new MaterialDefinition("iron_ore", "Iron Ore",
            MaterialTier.RAW, MaterialCategory.METAL, 2.0f, 1.0f, 99, "", null));
        materialRegistry.register(new MaterialDefinition("iron_concentrate", "Iron Concentrate",
            MaterialTier.PROCESSED, MaterialCategory.METAL, 1.5f, 0.8f, 99, "", null));

        system = new RefiningSystem(eventBus);
        engine.addSystem(system);
    }

    private Entity createEntityWithActiveJob(float totalTime) {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(1, 4, 1.0f, 10f);
        RefiningJob job = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            totalTime);
        job.setState(RefiningJobState.ACTIVE);
        refinery.addJob(job);
        entity.add(refinery);
        entity.add(new MaterialStorageComponent(1000f, 1000f, materialRegistry));
        engine.addEntity(entity);
        return entity;
    }

    @Test
    void update_advancesActiveJobProgress() {
        Entity entity = createEntityWithActiveJob(10.0f);
        engine.update(2.5f);

        RefiningJob job = entity.getComponent(RefineryComponent.class).getActiveJob();
        assertEquals(0.25f, job.getProgress(), 0.01f);
    }

    @Test
    void update_jobCompletesAtFullProgress() {
        Entity entity = createEntityWithActiveJob(1.0f);

        AtomicReference<RefiningCompletedEvent> completed = new AtomicReference<>();
        eventBus.subscribe(RefiningCompletedEvent.class, completed::set);

        engine.update(1.0f);

        assertNotNull(completed.get());
        assertEquals(3, completed.get().producedMaterials.get("iron_concentrate"));
    }

    @Test
    void update_completedJob_addsMaterialsToStorage() {
        Entity entity = createEntityWithActiveJob(1.0f);
        engine.update(1.0f);

        MaterialStorageComponent storage = entity.getComponent(MaterialStorageComponent.class);
        assertEquals(3, storage.getQuantity("iron_concentrate"));
    }

    @Test
    void update_completedJob_removedFromQueue() {
        Entity entity = createEntityWithActiveJob(1.0f);
        engine.update(1.0f);

        RefineryComponent refinery = entity.getComponent(RefineryComponent.class);
        assertTrue(refinery.getJobQueue().isEmpty());
    }

    @Test
    void update_nextJobActivatesAfterCompletion() {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(1, 4, 1.0f, 10f);

        RefiningJob job1 = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            1.0f);
        job1.setState(RefiningJobState.ACTIVE);

        RefiningJob job2 = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            2.0f);
        job2.setState(RefiningJobState.QUEUED);

        refinery.addJob(job1);
        refinery.addJob(job2);
        entity.add(refinery);
        entity.add(new MaterialStorageComponent(1000f, 1000f, materialRegistry));
        engine.addEntity(entity);

        engine.update(1.0f);

        RefiningJob activeJob = refinery.getActiveJob();
        assertNotNull(activeJob);
        assertEquals(RefiningJobState.ACTIVE, activeJob.getState());
        assertSame(job2, activeJob);
    }

    @Test
    void update_noActiveJob_doesNothing() {
        Entity entity = new Entity();
        entity.add(new RefineryComponent(1, 4, 1.0f, 10f));
        entity.add(new MaterialStorageComponent(1000f, 1000f, materialRegistry));
        engine.addEntity(entity);

        assertDoesNotThrow(() -> engine.update(1.0f));
    }

    @Test
    void update_speedMultiplier_affectsProgress() {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(2, 4, 2.0f, 20f);
        RefiningJob job = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            10.0f);
        job.setState(RefiningJobState.ACTIVE);
        refinery.addJob(job);
        entity.add(refinery);
        entity.add(new MaterialStorageComponent(1000f, 1000f, materialRegistry));
        engine.addEntity(entity);

        // totalTime = 10, speedMultiplier = 2.0, deltaTime = 2.5
        // progress = (2.5 * 2.0) / 10.0 = 0.5
        engine.update(2.5f);
        assertEquals(0.5f, job.getProgress(), 0.01f);
    }

    @Test
    void update_completedJob_storageFullSkipsDeposit() {
        Entity entity = new Entity();
        RefineryComponent refinery = new RefineryComponent(1, 4, 1.0f, 10f);
        RefiningJob job = new RefiningJob("process_iron_ore",
            Map.of("iron_ore", 5),
            List.of(new RefiningJob.Output("iron_concentrate", 3)),
            1.0f);
        job.setState(RefiningJobState.ACTIVE);
        refinery.addJob(job);
        entity.add(refinery);
        // Storage too small: 3 iron_concentrate * 0.8 volume = 2.4, but max volume = 1.0
        entity.add(new MaterialStorageComponent(1000f, 1.0f, materialRegistry));
        engine.addEntity(entity);

        AtomicReference<RefiningCompletedEvent> completed = new AtomicReference<>();
        eventBus.subscribe(RefiningCompletedEvent.class, completed::set);

        engine.update(1.0f);

        assertNotNull(completed.get());
        assertTrue(completed.get().producedMaterials.isEmpty());
        assertEquals(0, entity.getComponent(MaterialStorageComponent.class).getQuantity("iron_concentrate"));
    }

    @Test
    void update_multipleTicksToComplete() {
        Entity entity = createEntityWithActiveJob(4.0f);

        engine.update(1.0f); // 0.25
        engine.update(1.0f); // 0.50
        engine.update(1.0f); // 0.75

        RefiningJob job = entity.getComponent(RefineryComponent.class).getActiveJob();
        assertNotNull(job);
        assertEquals(0.75f, job.getProgress(), 0.01f);

        AtomicReference<RefiningCompletedEvent> completed = new AtomicReference<>();
        eventBus.subscribe(RefiningCompletedEvent.class, completed::set);

        engine.update(1.0f); // 1.0 -> complete
        assertNotNull(completed.get());
    }
}
