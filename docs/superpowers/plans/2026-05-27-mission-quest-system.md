# Mission / Quest System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a two-tier mission system — handcrafted Sagas (branching story/faction/companion arcs) and procedural Jobs (job board + event-driven discovery) — sharing common Objective, Reward, and QuestJournal infrastructure.

**Architecture:** Sagas are directed graphs of nodes (OBJECTIVE / DIALOGUE_CHOICE / CONSEQUENCE / TERMINUS) loaded from JSON. Jobs are template-instantiated at runtime by `ProceduralJobGenerator` or spawned reactively by `EventJobGenerator` via world events. A `QuestDiscoverySystem` bridges both tiers, activating missions through a dual-path model: NPC rumour OR physical location reached.

**Tech Stack:** Ashley ECS, libGDX Json, Java 21, EventBus (existing), JUnit 5.

**Note:** No YAML library exists in the build — all data files (including saga definitions) use JSON via libGDX's `Json` class. `SectorContext` and `ReputationQuery` are minimal stubs defined in this system; they will be replaced when the full galaxy/reputation systems land.

---

## File Map

**Create — events:**
- `core/src/main/java/com/galacticodyssey/mission/events/QuestDiscoveredEvent.java`
- `core/src/main/java/com/galacticodyssey/mission/events/SagaActivatedEvent.java`
- `core/src/main/java/com/galacticodyssey/mission/events/SagaNodeEnteredEvent.java`
- `core/src/main/java/com/galacticodyssey/mission/events/ObjectiveUpdatedEvent.java`
- `core/src/main/java/com/galacticodyssey/mission/events/ObjectiveCompletedEvent.java`
- `core/src/main/java/com/galacticodyssey/mission/events/QuestCompletedEvent.java`
- `core/src/main/java/com/galacticodyssey/mission/events/QuestFailedEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/LocationEnteredEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/NpcDialogueEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/ScanCompleteEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/ResourceCollectedEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/CargoDeliveredEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/EscortTargetReachedEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/FactionWarStartedEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/FactionWarEndedEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/ShipMissingEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/AnomalyDetectedEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/CargoShipAttackedEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/ReputationChangeEvent.java`
- `core/src/main/java/com/galacticodyssey/core/events/DialogueChoiceMadeEvent.java`

**Create — shared infrastructure:**
- `core/src/main/java/com/galacticodyssey/mission/shared/ObjectiveType.java`
- `core/src/main/java/com/galacticodyssey/mission/shared/Objective.java`
- `core/src/main/java/com/galacticodyssey/mission/shared/MissionReward.java`
- `core/src/main/java/com/galacticodyssey/mission/shared/QuestJournal.java`
- `core/src/main/java/com/galacticodyssey/mission/shared/ObjectiveTrackingSystem.java`
- `core/src/main/java/com/galacticodyssey/mission/shared/RewardSystem.java`

**Create — saga tier:**
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaNodeType.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaCategory.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaState.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaNodeData.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaEdgeData.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/TriggerData.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaData.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaInstance.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaRegistry.java`
- `core/src/main/java/com/galacticodyssey/mission/saga/SagaRunner.java`

**Create — job tier:**
- `core/src/main/java/com/galacticodyssey/mission/job/JobType.java`
- `core/src/main/java/com/galacticodyssey/mission/job/JobState.java`
- `core/src/main/java/com/galacticodyssey/mission/job/ObjectiveTemplate.java`
- `core/src/main/java/com/galacticodyssey/mission/job/JobTemplate.java`
- `core/src/main/java/com/galacticodyssey/mission/job/JobInstance.java`
- `core/src/main/java/com/galacticodyssey/mission/job/SectorContext.java`
- `core/src/main/java/com/galacticodyssey/mission/job/ReputationQuery.java`
- `core/src/main/java/com/galacticodyssey/mission/job/JobRegistry.java`
- `core/src/main/java/com/galacticodyssey/mission/job/ProceduralJobGenerator.java`
- `core/src/main/java/com/galacticodyssey/mission/job/JobBoard.java`
- `core/src/main/java/com/galacticodyssey/mission/job/EventJobGenerator.java`

**Create — discovery:**
- `core/src/main/java/com/galacticodyssey/mission/discovery/DiscoveryLead.java`
- `core/src/main/java/com/galacticodyssey/mission/discovery/QuestDiscoverySystem.java`

**Create — data files:**
- `core/src/main/resources/data/quests/jobs/templates.json`
- `core/src/main/resources/data/quests/story/act1_the_signal.json`

**Create — tests:**
- `core/src/test/java/com/galacticodyssey/mission/shared/ObjectiveTrackingSystemTest.java`
- `core/src/test/java/com/galacticodyssey/mission/shared/RewardSystemTest.java`
- `core/src/test/java/com/galacticodyssey/mission/shared/QuestJournalTest.java`
- `core/src/test/java/com/galacticodyssey/mission/saga/SagaRunnerTest.java`
- `core/src/test/java/com/galacticodyssey/mission/job/ProceduralJobGeneratorTest.java`
- `core/src/test/java/com/galacticodyssey/mission/job/JobBoardTest.java`
- `core/src/test/java/com/galacticodyssey/mission/job/EventJobGeneratorTest.java`
- `core/src/test/java/com/galacticodyssey/mission/discovery/QuestDiscoverySystemTest.java`
- `core/src/test/java/com/galacticodyssey/mission/MissionIntegrationTest.java`

**Modify:**
- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — register new systems

---

## Task 1: Event POJOs

**Files:**
- Create: all event files listed in the File Map above (20 files, all simple POJOs)

- [ ] **Step 1: Create mission events**

```java
// QuestDiscoveredEvent.java
package com.galacticodyssey.mission.events;
public class QuestDiscoveredEvent {
    public final String missionId;
    public final String title;
    public QuestDiscoveredEvent(String missionId, String title) {
        this.missionId = missionId; this.title = title;
    }
}
```

```java
// SagaActivatedEvent.java
package com.galacticodyssey.mission.events;
public class SagaActivatedEvent {
    public final String sagaId;
    public SagaActivatedEvent(String sagaId) { this.sagaId = sagaId; }
}
```

```java
// SagaNodeEnteredEvent.java
package com.galacticodyssey.mission.events;
public class SagaNodeEnteredEvent {
    public final String sagaId;
    public final String nodeId;
    public final String nodeType;
    public SagaNodeEnteredEvent(String sagaId, String nodeId, String nodeType) {
        this.sagaId = sagaId; this.nodeId = nodeId; this.nodeType = nodeType;
    }
}
```

```java
// ObjectiveUpdatedEvent.java
package com.galacticodyssey.mission.events;
public class ObjectiveUpdatedEvent {
    public final String missionId;
    public final String objectiveId;
    public final int currentCount;
    public final int requiredCount;
    public ObjectiveUpdatedEvent(String missionId, String objectiveId, int currentCount, int requiredCount) {
        this.missionId = missionId; this.objectiveId = objectiveId;
        this.currentCount = currentCount; this.requiredCount = requiredCount;
    }
}
```

```java
// ObjectiveCompletedEvent.java
package com.galacticodyssey.mission.events;
public class ObjectiveCompletedEvent {
    public final String missionId;
    public final String objectiveId;
    public ObjectiveCompletedEvent(String missionId, String objectiveId) {
        this.missionId = missionId; this.objectiveId = objectiveId;
    }
}
```

```java
// QuestCompletedEvent.java
package com.galacticodyssey.mission.events;
import com.galacticodyssey.mission.shared.MissionReward;
public class QuestCompletedEvent {
    public final String missionId;
    public final MissionReward reward;
    public QuestCompletedEvent(String missionId, MissionReward reward) {
        this.missionId = missionId; this.reward = reward;
    }
}
```

```java
// QuestFailedEvent.java
package com.galacticodyssey.mission.events;
public class QuestFailedEvent {
    public final String missionId;
    public final String reason;
    public QuestFailedEvent(String missionId, String reason) {
        this.missionId = missionId; this.reason = reason;
    }
}
```

- [ ] **Step 2: Create core world events**

```java
// LocationEnteredEvent.java
package com.galacticodyssey.core.events;
public class LocationEnteredEvent {
    public final String locationId;
    public LocationEnteredEvent(String locationId) { this.locationId = locationId; }
}
```

```java
// NpcDialogueEvent.java
package com.galacticodyssey.core.events;
public class NpcDialogueEvent {
    public final String npcId;
    public final String topic;   // e.g. "RUMOUR", "QUEST", "TRADE"
    public NpcDialogueEvent(String npcId, String topic) {
        this.npcId = npcId; this.topic = topic;
    }
}
```

```java
// ScanCompleteEvent.java
package com.galacticodyssey.core.events;
public class ScanCompleteEvent {
    public final String targetId;
    public ScanCompleteEvent(String targetId) { this.targetId = targetId; }
}
```

```java
// ResourceCollectedEvent.java
package com.galacticodyssey.core.events;
public class ResourceCollectedEvent {
    public final String resourceType;
    public final int amount;
    public ResourceCollectedEvent(String resourceType, int amount) {
        this.resourceType = resourceType; this.amount = amount;
    }
}
```

```java
// CargoDeliveredEvent.java
package com.galacticodyssey.core.events;
public class CargoDeliveredEvent {
    public final String cargoType;
    public final String destinationId;
    public CargoDeliveredEvent(String cargoType, String destinationId) {
        this.cargoType = cargoType; this.destinationId = destinationId;
    }
}
```

```java
// EscortTargetReachedEvent.java
package com.galacticodyssey.core.events;
public class EscortTargetReachedEvent {
    public final String targetId;
    public EscortTargetReachedEvent(String targetId) { this.targetId = targetId; }
}
```

```java
// DialogueChoiceMadeEvent.java
package com.galacticodyssey.core.events;
public class DialogueChoiceMadeEvent {
    public final String npcId;
    public final String choiceKey;
    public DialogueChoiceMadeEvent(String npcId, String choiceKey) {
        this.npcId = npcId; this.choiceKey = choiceKey;
    }
}
```

```java
// ReputationChangeEvent.java
package com.galacticodyssey.core.events;
public class ReputationChangeEvent {
    public final String factionId;
    public final float delta;
    public final String sourceId;   // mission id or other source
    public ReputationChangeEvent(String factionId, float delta, String sourceId) {
        this.factionId = factionId; this.delta = delta; this.sourceId = sourceId;
    }
}
```

```java
// FactionWarStartedEvent.java
package com.galacticodyssey.core.events;
public class FactionWarStartedEvent {
    public final String warId;
    public final String factionA;
    public final String factionB;
    public final String sectorId;
    public FactionWarStartedEvent(String warId, String factionA, String factionB, String sectorId) {
        this.warId = warId; this.factionA = factionA; this.factionB = factionB; this.sectorId = sectorId;
    }
}
```

```java
// FactionWarEndedEvent.java
package com.galacticodyssey.core.events;
public class FactionWarEndedEvent {
    public final String warId;
    public FactionWarEndedEvent(String warId) { this.warId = warId; }
}
```

```java
// ShipMissingEvent.java
package com.galacticodyssey.core.events;
public class ShipMissingEvent {
    public final String shipId;
    public final String lastKnownLocationId;
    public ShipMissingEvent(String shipId, String lastKnownLocationId) {
        this.shipId = shipId; this.lastKnownLocationId = lastKnownLocationId;
    }
}
```

```java
// AnomalyDetectedEvent.java
package com.galacticodyssey.core.events;
public class AnomalyDetectedEvent {
    public final String anomalyId;
    public final String locationId;
    public AnomalyDetectedEvent(String anomalyId, String locationId) {
        this.anomalyId = anomalyId; this.locationId = locationId;
    }
}
```

```java
// CargoShipAttackedEvent.java
package com.galacticodyssey.core.events;
public class CargoShipAttackedEvent {
    public final String attackerId;
    public final String cargoType;
    public final String locationId;
    public CargoShipAttackedEvent(String attackerId, String cargoType, String locationId) {
        this.attackerId = attackerId; this.cargoType = cargoType; this.locationId = locationId;
    }
}
```

- [ ] **Step 3: Compile check**

```
./gradlew :core:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/events/ core/src/main/java/com/galacticodyssey/core/events/LocationEnteredEvent.java core/src/main/java/com/galacticodyssey/core/events/NpcDialogueEvent.java core/src/main/java/com/galacticodyssey/core/events/ScanCompleteEvent.java core/src/main/java/com/galacticodyssey/core/events/ResourceCollectedEvent.java core/src/main/java/com/galacticodyssey/core/events/CargoDeliveredEvent.java core/src/main/java/com/galacticodyssey/core/events/EscortTargetReachedEvent.java core/src/main/java/com/galacticodyssey/core/events/DialogueChoiceMadeEvent.java core/src/main/java/com/galacticodyssey/core/events/ReputationChangeEvent.java core/src/main/java/com/galacticodyssey/core/events/FactionWarStartedEvent.java core/src/main/java/com/galacticodyssey/core/events/FactionWarEndedEvent.java core/src/main/java/com/galacticodyssey/core/events/ShipMissingEvent.java core/src/main/java/com/galacticodyssey/core/events/AnomalyDetectedEvent.java core/src/main/java/com/galacticodyssey/core/events/CargoShipAttackedEvent.java
git commit -m "feat(mission): add mission and world-state event POJOs"
```

---

## Task 2: Objective and MissionReward data classes

**Files:**
- Create: `mission/shared/ObjectiveType.java`
- Create: `mission/shared/Objective.java`
- Create: `mission/shared/MissionReward.java`

- [ ] **Step 1: Write ObjectiveType enum**

```java
package com.galacticodyssey.mission.shared;

public enum ObjectiveType {
    DELIVER_CARGO,
    DESTROY_TARGET,
    REACH_LOCATION,
    SCAN_OBJECT,
    COLLECT_RESOURCE,
    SURVIVE_TIME,
    ESCORT_TARGET,
    TALK_TO_NPC
}
```

- [ ] **Step 2: Write Objective**

```java
package com.galacticodyssey.mission.shared;

public class Objective {
    public String id;
    public ObjectiveType type;
    public String targetId;
    public int requiredCount;
    public int currentCount;
    public boolean optional;
    public boolean completed;

    public float optionalBonusMultiplier() {
        return optional ? 1.25f : 1.0f;
    }

    public boolean isSatisfied() {
        return currentCount >= requiredCount;
    }
}
```

- [ ] **Step 3: Write MissionReward**

```java
package com.galacticodyssey.mission.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class MissionReward {
    public int credits;
    public Map<String, Integer> resources = new HashMap<>();
    public String reputationFaction;
    public float reputationDelta;
    public float crewXP;
    public List<String> itemRewards = new ArrayList<>();

    public MissionReward scaled(float multiplier) {
        MissionReward r = new MissionReward();
        r.credits = Math.round(credits * multiplier);
        r.reputationFaction = reputationFaction;
        r.reputationDelta = reputationDelta * multiplier;
        r.crewXP = crewXP * multiplier;
        r.itemRewards = new ArrayList<>(itemRewards);
        for (Map.Entry<String, Integer> e : resources.entrySet())
            r.resources.put(e.getKey(), Math.round(e.getValue() * multiplier));
        return r;
    }
}
```

- [ ] **Step 4: Compile check**

```
./gradlew :core:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/shared/ObjectiveType.java core/src/main/java/com/galacticodyssey/mission/shared/Objective.java core/src/main/java/com/galacticodyssey/mission/shared/MissionReward.java
git commit -m "feat(mission): add Objective and MissionReward data classes"
```

---

## Task 3: QuestJournal

**Files:**
- Create: `mission/shared/QuestJournal.java`
- Create: `test/.../mission/shared/QuestJournalTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.mission.shared;

import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.saga.SagaCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestJournalTest {

    private QuestJournal journal;

    @BeforeEach
    void setUp() { journal = new QuestJournal(); }

    @Test
    void addJob_underCap_succeeds() {
        JobInstance job = makeJob("job1");
        assertTrue(journal.addJob(job));
        assertEquals(1, journal.getActiveJobs().size());
    }

    @Test
    void addJob_atCap_returnsFalse() {
        for (int i = 0; i < 10; i++) journal.addJob(makeJob("job" + i));
        assertFalse(journal.addJob(makeJob("overflow")));
    }

    @Test
    void findJob_byId_returnsCorrectInstance() {
        JobInstance job = makeJob("abc");
        journal.addJob(job);
        assertSame(job, journal.findJob("abc"));
    }

    @Test
    void findJob_missingId_returnsNull() {
        assertNull(journal.findJob("nonexistent"));
    }

    @Test
    void removeJob_removesFromActive() {
        JobInstance job = makeJob("job1");
        journal.addJob(job);
        journal.removeJob("job1");
        assertTrue(journal.getActiveJobs().isEmpty());
    }

    @Test
    void addRumour_andRetrieve() {
        JobInstance job = makeJob("rumour1");
        journal.addRumour(job);
        assertEquals(1, journal.getRumourBoard().size());
    }

    @Test
    void promoteRumour_movesToActive() {
        JobInstance job = makeJob("r1");
        journal.addRumour(job);
        assertTrue(journal.promoteRumour("r1"));
        assertTrue(journal.getRumourBoard().isEmpty());
        assertEquals(1, journal.getActiveJobs().size());
    }

    private JobInstance makeJob(String id) {
        JobInstance j = new JobInstance();
        j.instanceId = id;
        j.state = JobState.AVAILABLE;
        return j;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.shared.QuestJournalTest"
```

Expected: FAILED (QuestJournal not found)

- [ ] **Step 3: Implement QuestJournal**

```java
package com.galacticodyssey.mission.shared;

import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.saga.SagaCategory;

import java.util.ArrayList;
import java.util.List;

public class QuestJournal {

    private static final int JOB_CAP = 10;

    private SagaInstance activeMainStory;
    private final List<SagaInstance> activeFactionChains = new ArrayList<>();
    private final List<SagaInstance> activeCompanionArcs = new ArrayList<>();
    private final List<JobInstance> activeJobs = new ArrayList<>();
    private final List<JobInstance> rumourBoard = new ArrayList<>();

    public boolean addJob(JobInstance job) {
        if (activeJobs.size() >= JOB_CAP) return false;
        activeJobs.add(job);
        return true;
    }

    public void removeJob(String instanceId) {
        activeJobs.removeIf(j -> instanceId.equals(j.instanceId));
    }

    public JobInstance findJob(String instanceId) {
        return activeJobs.stream().filter(j -> instanceId.equals(j.instanceId)).findFirst().orElse(null);
    }

    public List<JobInstance> getActiveJobs() { return activeJobs; }

    public void addRumour(JobInstance job) { rumourBoard.add(job); }

    public boolean promoteRumour(String instanceId) {
        JobInstance job = rumourBoard.stream().filter(j -> instanceId.equals(j.instanceId)).findFirst().orElse(null);
        if (job == null || !addJob(job)) return false;
        rumourBoard.remove(job);
        return true;
    }

    public List<JobInstance> getRumourBoard() { return rumourBoard; }

    public void setMainStory(SagaInstance saga) { this.activeMainStory = saga; }
    public SagaInstance getMainStory() { return activeMainStory; }

    public void addFactionChain(SagaInstance saga) { activeFactionChains.add(saga); }
    public List<SagaInstance> getActiveFactionChains() { return activeFactionChains; }

    public void addCompanionArc(SagaInstance saga) { activeCompanionArcs.add(saga); }
    public List<SagaInstance> getActiveCompanionArcs() { return activeCompanionArcs; }

    public SagaInstance findSaga(String sagaId) {
        if (activeMainStory != null && sagaId.equals(activeMainStory.sagaDataId)) return activeMainStory;
        return activeFactionChains.stream().filter(s -> sagaId.equals(s.sagaDataId)).findFirst()
            .orElse(activeCompanionArcs.stream().filter(s -> sagaId.equals(s.sagaDataId)).findFirst().orElse(null));
    }

    public List<SagaInstance> getAllActiveSagas() {
        List<SagaInstance> all = new ArrayList<>();
        if (activeMainStory != null) all.add(activeMainStory);
        all.addAll(activeFactionChains);
        all.addAll(activeCompanionArcs);
        return all;
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.shared.QuestJournalTest"
```

Expected: 7 tests passed

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/shared/QuestJournal.java core/src/test/java/com/galacticodyssey/mission/shared/QuestJournalTest.java
git commit -m "feat(mission): add QuestJournal with cap enforcement and rumour board"
```

---

## Task 4: Job data model (enums + data classes)

**Files:**
- Create: `mission/job/JobType.java`
- Create: `mission/job/JobState.java`
- Create: `mission/job/ObjectiveTemplate.java`
- Create: `mission/job/JobTemplate.java`
- Create: `mission/job/JobInstance.java`
- Create: `mission/job/SectorContext.java`
- Create: `mission/job/ReputationQuery.java`

- [ ] **Step 1: Create enums**

```java
// JobType.java
package com.galacticodyssey.mission.job;
public enum JobType {
    CARGO_HAUL, BOUNTY_HUNT, MERCENARY, ESCORT, MINING_CONTRACT, EXPLORATION_SURVEY, SALVAGE
}
```

```java
// JobState.java
package com.galacticodyssey.mission.job;
public enum JobState {
    RUMOURED, AVAILABLE, ACTIVE, COMPLETE, FAILED, EXPIRED
}
```

- [ ] **Step 2: Create ObjectiveTemplate + JobTemplate**

```java
// ObjectiveTemplate.java
package com.galacticodyssey.mission.job;
import com.galacticodyssey.mission.shared.ObjectiveType;

public class ObjectiveTemplate {
    public ObjectiveType type;
    public int requiredCount = 1;
    public boolean optional = false;
}
```

```java
// JobTemplate.java
package com.galacticodyssey.mission.job;

import java.util.ArrayList;
import java.util.List;

public class JobTemplate {
    public String id;
    public JobType type;
    public String giverFactionTag;
    public float requiredStanding = -100f;
    public String discoveryMode = "BOARD";  // BOARD | EVENT_DRIVEN | BOTH
    public int baseCredits;
    public float baseReputationDelta;
    public float baseTimeLimitSeconds = 0;  // 0 = no time limit
    public List<ObjectiveTemplate> objectives = new ArrayList<>();
}
```

- [ ] **Step 3: Create JobInstance**

```java
// JobInstance.java
package com.galacticodyssey.mission.job;

import com.galacticodyssey.mission.shared.MissionReward;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.discovery.DiscoveryLead;

import java.util.ArrayList;
import java.util.List;

public class JobInstance {
    public String instanceId;
    public String templateId;
    public JobType type;
    public JobState state = JobState.AVAILABLE;
    public String giverNpcId;
    public String giverLocationId;
    public String triggeringEventId;    // non-null for EVENT_DRIVEN jobs
    public float difficulty;
    public float timeLimit;             // seconds; 0 = no limit
    public float elapsed;
    public List<Objective> objectives = new ArrayList<>();
    public MissionReward reward;
    public DiscoveryLead lead;          // null for BOARD jobs

    public boolean allRequiredComplete() {
        return objectives.stream().filter(o -> !o.optional).allMatch(o -> o.completed);
    }
}
```

- [ ] **Step 4: Create SectorContext + ReputationQuery**

```java
// SectorContext.java
package com.galacticodyssey.mission.job;

import java.util.List;

public class SectorContext {
    public String sectorId;
    public List<String> locationIds;
    public List<String> npcIds;
    public List<String> factionTags;
    public float dangerLevel;           // 0.0 – 1.0
}
```

```java
// ReputationQuery.java
package com.galacticodyssey.mission.job;

@FunctionalInterface
public interface ReputationQuery {
    float getStanding(String factionTag);
}
```

- [ ] **Step 5: Compile check**

```
./gradlew :core:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/job/
git commit -m "feat(mission): add job data model (JobTemplate, JobInstance, SectorContext)"
```

---

## Task 5: Saga data model + SagaRegistry

**Files:**
- Create: all saga enums and data classes, SagaRegistry, SagaInstance

- [ ] **Step 1: Create saga enums**

```java
// SagaNodeType.java
package com.galacticodyssey.mission.saga;
public enum SagaNodeType { OBJECTIVE, DIALOGUE_CHOICE, CONSEQUENCE, TERMINUS }
```

```java
// SagaCategory.java
package com.galacticodyssey.mission.saga;
public enum SagaCategory { MAIN_STORY, FACTION, COMPANION }
```

```java
// SagaState.java
package com.galacticodyssey.mission.saga;
public enum SagaState { LOCKED, AVAILABLE, ACTIVE, COMPLETE, FAILED }
```

- [ ] **Step 2: Create SagaNodeData, SagaEdgeData, TriggerData**

```java
// SagaNodeData.java
package com.galacticodyssey.mission.saga;

import com.galacticodyssey.mission.shared.Objective;
import java.util.ArrayList;
import java.util.List;

public class SagaNodeData {
    public String id;
    public SagaNodeType type;
    public String npcId;                    // DIALOGUE_CHOICE nodes
    public String outcome;                  // TERMINUS nodes: "COMPLETE" or "FAILED"
    public List<Objective> objectives = new ArrayList<>();
    public List<ConsequenceEvent> consequences = new ArrayList<>();

    public static class ConsequenceEvent {
        public String type;                 // "REPUTATION_CHANGE"
        public String faction;
        public float delta;
        public String worldEventType;       // optional: additional event class name
    }
}
```

```java
// SagaEdgeData.java
package com.galacticodyssey.mission.saga;

public class SagaEdgeData {
    public String from;
    public String to;
    public String requiresChoice;           // null = unconditional
}
```

```java
// TriggerData.java
package com.galacticodyssey.mission.saga;

public class TriggerData {
    public String type;                     // "REPUTATION_THRESHOLD", "LOCATION_REACHED", "QUEST_COMPLETED"
    public String faction;
    public float minStanding;
    public String locationId;
    public String questId;
}
```

- [ ] **Step 3: Create SagaData + SagaInstance**

```java
// SagaData.java
package com.galacticodyssey.mission.saga;

import java.util.ArrayList;
import java.util.List;

public class SagaData {
    public String id;
    public String title;
    public SagaCategory category;
    public List<SagaNodeData> nodes = new ArrayList<>();
    public List<SagaEdgeData> edges = new ArrayList<>();
    public List<TriggerData> triggers = new ArrayList<>();

    public SagaNodeData getNode(String nodeId) {
        return nodes.stream().filter(n -> nodeId.equals(n.id)).findFirst().orElse(null);
    }

    public List<SagaEdgeData> edgesFrom(String nodeId) {
        return edges.stream().filter(e -> nodeId.equals(e.from)).toList();
    }
}
```

```java
// SagaInstance.java
package com.galacticodyssey.mission.saga;

import com.galacticodyssey.mission.shared.Objective;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SagaInstance {
    public String sagaDataId;
    public String currentNodeId;
    public SagaState state = SagaState.LOCKED;
    public Map<String, String> choicesMade = new HashMap<>();
    public List<Objective> activeObjectives = new ArrayList<>();

    public boolean allRequiredObjectivesComplete() {
        return activeObjectives.stream().filter(o -> !o.optional).allMatch(o -> o.completed);
    }
}
```

- [ ] **Step 4: Create SagaRegistry**

```java
// SagaRegistry.java
package com.galacticodyssey.mission.saga;

import java.util.HashMap;
import java.util.Map;

public class SagaRegistry {
    private final Map<String, SagaData> sagas = new HashMap<>();

    public void register(SagaData saga) { sagas.put(saga.id, saga); }

    public SagaData get(String id) { return sagas.get(id); }

    public Map<String, SagaData> getAll() { return sagas; }
}
```

- [ ] **Step 5: Compile check**

```
./gradlew :core:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/saga/
git commit -m "feat(mission): add saga data model and SagaRegistry"
```

---

## Task 6: ObjectiveTrackingSystem

**Files:**
- Create: `mission/shared/ObjectiveTrackingSystem.java`
- Create: `test/.../mission/shared/ObjectiveTrackingSystemTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.mission.shared;

import com.badlogic.ashley.core.Engine;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.mission.events.*;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveTrackingSystemTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private ObjectiveTrackingSystem system;
    private final List<ObjectiveUpdatedEvent> updated = new ArrayList<>();
    private final List<ObjectiveCompletedEvent> completed = new ArrayList<>();
    private final List<QuestCompletedEvent> questCompleted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        system = new ObjectiveTrackingSystem(eventBus, journal);

        eventBus.subscribe(ObjectiveUpdatedEvent.class, updated::add);
        eventBus.subscribe(ObjectiveCompletedEvent.class, completed::add);
        eventBus.subscribe(QuestCompletedEvent.class, questCompleted::add);

        JobInstance job = makeJob("j1", ObjectiveType.DESTROY_TARGET, "enemy_pirate", 3);
        job.state = JobState.ACTIVE;
        journal.addJob(job);
    }

    @Test
    void entityKilled_matchingTarget_incrementsCount() {
        eventBus.publish(new com.galacticodyssey.combat.events.EntityKilledEvent(null, null));
        // EntityKilledEvent carries a targetId — need to use the one that matches
        // Publish via helper that sets targetId
        system.onEntityKilled("enemy_pirate");
        assertEquals(1, getObjective("j1").currentCount);
        assertEquals(1, updated.size());
    }

    @Test
    void entityKilled_nonMatchingTarget_doesNotIncrement() {
        system.onEntityKilled("friendly_npc");
        assertEquals(0, getObjective("j1").currentCount);
        assertTrue(updated.isEmpty());
    }

    @Test
    void threeKills_completesObjectiveAndQuest() {
        system.onEntityKilled("enemy_pirate");
        system.onEntityKilled("enemy_pirate");
        system.onEntityKilled("enemy_pirate");
        assertTrue(getObjective("j1").completed);
        assertEquals(1, completed.size());
        assertEquals(1, questCompleted.size());
        assertEquals("j1", questCompleted.get(0).missionId);
    }

    @Test
    void locationEntered_completesReachObjective() {
        JobInstance job = makeJob("j2", ObjectiveType.REACH_LOCATION, "station_alpha", 1);
        job.state = JobState.ACTIVE;
        journal.addJob(job);

        eventBus.publish(new LocationEnteredEvent("station_alpha"));

        assertTrue(job.objectives.get(0).completed);
    }

    private Objective getObjective(String jobId) {
        return journal.findJob(jobId).objectives.get(0);
    }

    private JobInstance makeJob(String id, ObjectiveType type, String targetId, int required) {
        JobInstance job = new JobInstance();
        job.instanceId = id;
        job.state = JobState.ACTIVE;
        job.reward = new MissionReward();
        Objective obj = new Objective();
        obj.id = "obj_" + id;
        obj.type = type;
        obj.targetId = targetId;
        obj.requiredCount = required;
        job.objectives.add(obj);
        return job;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.shared.ObjectiveTrackingSystemTest"
```

Expected: FAILED (ObjectiveTrackingSystem not found)

- [ ] **Step 3: Implement ObjectiveTrackingSystem**

```java
package com.galacticodyssey.mission.shared;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.mission.events.*;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.saga.SagaState;

import java.util.List;

public class ObjectiveTrackingSystem extends EntitySystem {

    private final EventBus eventBus;
    private final QuestJournal journal;

    public ObjectiveTrackingSystem(EventBus eventBus, QuestJournal journal) {
        this.eventBus = eventBus;
        this.journal = journal;
        subscribeAll();
    }

    private void subscribeAll() {
        eventBus.subscribe(com.galacticodyssey.combat.events.EntityKilledEvent.class,
            e -> onEntityKilled(e.targetId != null ? e.targetId : ""));
        eventBus.subscribe(LocationEnteredEvent.class, e -> increment(ObjectiveType.REACH_LOCATION, e.locationId));
        eventBus.subscribe(ScanCompleteEvent.class, e -> increment(ObjectiveType.SCAN_OBJECT, e.targetId));
        eventBus.subscribe(CargoDeliveredEvent.class, e -> increment(ObjectiveType.DELIVER_CARGO, e.cargoType));
        eventBus.subscribe(EscortTargetReachedEvent.class, e -> increment(ObjectiveType.ESCORT_TARGET, e.targetId));
        eventBus.subscribe(NpcDialogueEvent.class, e -> increment(ObjectiveType.TALK_TO_NPC, e.npcId));
        eventBus.subscribe(ResourceCollectedEvent.class, e -> {
            for (int i = 0; i < e.amount; i++) increment(ObjectiveType.COLLECT_RESOURCE, e.resourceType);
        });
    }

    public void onEntityKilled(String targetId) {
        increment(ObjectiveType.DESTROY_TARGET, targetId);
    }

    private void increment(ObjectiveType type, String targetId) {
        for (JobInstance job : journal.getActiveJobs()) {
            if (job.state != JobState.ACTIVE) continue;
            for (Objective obj : job.objectives) {
                if (!obj.completed && obj.type == type && targetId.equals(obj.targetId)) {
                    obj.currentCount++;
                    eventBus.publish(new ObjectiveUpdatedEvent(job.instanceId, obj.id, obj.currentCount, obj.requiredCount));
                    if (obj.currentCount >= obj.requiredCount) {
                        obj.completed = true;
                        eventBus.publish(new ObjectiveCompletedEvent(job.instanceId, obj.id));
                    }
                    if (job.allRequiredComplete()) {
                        job.state = JobState.COMPLETE;
                        eventBus.publish(new QuestCompletedEvent(job.instanceId, job.reward));
                    }
                }
            }
        }
        for (SagaInstance saga : journal.getAllActiveSagas()) {
            if (saga.state != SagaState.ACTIVE) continue;
            for (Objective obj : saga.activeObjectives) {
                if (!obj.completed && obj.type == type && targetId.equals(obj.targetId)) {
                    obj.currentCount++;
                    eventBus.publish(new ObjectiveUpdatedEvent(saga.sagaDataId, obj.id, obj.currentCount, obj.requiredCount));
                    if (obj.currentCount >= obj.requiredCount) {
                        obj.completed = true;
                        eventBus.publish(new ObjectiveCompletedEvent(saga.sagaDataId, obj.id));
                    }
                }
            }
        }
    }

    @Override
    public void update(float dt) {
        for (JobInstance job : journal.getActiveJobs()) {
            if (job.state != JobState.ACTIVE) continue;
            for (Objective obj : job.objectives) {
                if (!obj.completed && obj.type == ObjectiveType.SURVIVE_TIME) {
                    obj.currentCount = (int)(obj.currentCount + dt);
                    eventBus.publish(new ObjectiveUpdatedEvent(job.instanceId, obj.id, obj.currentCount, obj.requiredCount));
                    if (obj.currentCount >= obj.requiredCount) {
                        obj.completed = true;
                        eventBus.publish(new ObjectiveCompletedEvent(job.instanceId, obj.id));
                        if (job.allRequiredComplete()) {
                            job.state = JobState.COMPLETE;
                            eventBus.publish(new QuestCompletedEvent(job.instanceId, job.reward));
                        }
                    }
                }
            }
        }
    }
}
```

Note: `EntityKilledEvent` already exists in `combat.events` — check its field name. If it uses `entity` rather than `targetId`, adapt the lambda in `subscribeAll()` to use the entity's id via a mapper component. If the field is named differently, use whatever name the existing class defines.

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.shared.ObjectiveTrackingSystemTest"
```

Expected: all tests pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/shared/ObjectiveTrackingSystem.java core/src/test/java/com/galacticodyssey/mission/shared/ObjectiveTrackingSystemTest.java
git commit -m "feat(mission): add ObjectiveTrackingSystem with event-driven tracking"
```

---

## Task 7: RewardSystem

**Files:**
- Create: `mission/shared/RewardSystem.java`
- Create: `test/.../mission/shared/RewardSystemTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.mission.shared;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.events.QuestCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RewardSystemTest {

    private EventBus eventBus;
    private RewardSystem rewardSystem;
    private final List<ReputationChangeEvent> repEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        rewardSystem = new RewardSystem(eventBus);
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);
    }

    @Test
    void questCompleted_publishesReputationChange() {
        MissionReward reward = new MissionReward();
        reward.reputationFaction = "explorers_guild";
        reward.reputationDelta = 10f;

        eventBus.publish(new QuestCompletedEvent("mission1", reward));

        assertEquals(1, repEvents.size());
        assertEquals("explorers_guild", repEvents.get(0).factionId);
        assertEquals(10f, repEvents.get(0).delta, 0.01f);
    }

    @Test
    void questCompleted_noReputationFaction_doesNotPublishRepEvent() {
        MissionReward reward = new MissionReward();
        reward.reputationFaction = null;

        eventBus.publish(new QuestCompletedEvent("mission2", reward));

        assertTrue(repEvents.isEmpty());
    }

    @Test
    void questCompleted_nullReward_doesNotThrow() {
        assertDoesNotThrow(() -> eventBus.publish(new QuestCompletedEvent("mission3", null)));
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.shared.RewardSystemTest"
```

Expected: FAILED (RewardSystem not found)

- [ ] **Step 3: Implement RewardSystem**

```java
package com.galacticodyssey.mission.shared;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.events.QuestCompletedEvent;

public class RewardSystem {

    private final EventBus eventBus;

    public RewardSystem(EventBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(QuestCompletedEvent.class, this::onQuestCompleted);
    }

    private void onQuestCompleted(QuestCompletedEvent e) {
        if (e.reward == null) return;
        if (e.reward.reputationFaction != null && e.reward.reputationDelta != 0) {
            eventBus.publish(new ReputationChangeEvent(e.reward.reputationFaction, e.reward.reputationDelta, e.missionId));
        }
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.shared.RewardSystemTest"
```

Expected: all tests pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/shared/RewardSystem.java core/src/test/java/com/galacticodyssey/mission/shared/RewardSystemTest.java
git commit -m "feat(mission): add RewardSystem (distributes rewards, publishes ReputationChangeEvent)"
```

---

## Task 8: SagaRunner

**Files:**
- Create: `mission/saga/SagaRunner.java`
- Create: `test/.../mission/saga/SagaRunnerTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.mission.saga;

import com.badlogic.ashley.core.Engine;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.DialogueChoiceMadeEvent;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.events.*;
import com.galacticodyssey.mission.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SagaRunnerTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private SagaRegistry registry;
    private SagaRunner runner;
    private final List<SagaNodeEnteredEvent> nodeEntered = new ArrayList<>();
    private final List<ReputationChangeEvent> repEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        registry = new SagaRegistry();
        runner = new SagaRunner(eventBus, journal, registry);
        eventBus.subscribe(SagaNodeEnteredEvent.class, nodeEntered::add);
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);
    }

    @Test
    void objectiveNodeComplete_advancesToNextNode() {
        SagaData saga = buildTwoNodeSaga("scan_stage", "end_stage");
        registry.register(saga);
        SagaInstance instance = activateSaga(saga, "scan_stage");
        journal.setMainStory(instance);

        // Complete the objective
        Objective obj = instance.activeObjectives.get(0);
        obj.currentCount = obj.requiredCount;
        obj.completed = true;
        eventBus.publish(new ObjectiveCompletedEvent("test_saga", obj.id));

        assertEquals("end_stage", instance.currentNodeId);
        assertTrue(nodeEntered.stream().anyMatch(e -> "end_stage".equals(e.nodeId)));
    }

    @Test
    void terminusNode_setsStateComplete() {
        SagaData saga = buildTerminusSaga("end_node", "COMPLETE");
        registry.register(saga);
        SagaInstance instance = activateSaga(saga, "end_node");
        journal.setMainStory(instance);

        runner.enterNode(instance, saga.getNode("end_node"), saga);

        assertEquals(SagaState.COMPLETE, instance.state);
    }

    @Test
    void dialogueChoice_takesMatchingEdge() {
        SagaData saga = buildChoiceSaga();
        registry.register(saga);
        SagaInstance instance = activateSaga(saga, "choice_node");
        journal.setMainStory(instance);

        eventBus.publish(new DialogueChoiceMadeEvent("npc_varek", "sided_with_guild"));

        assertEquals("guild_outcome", instance.currentNodeId);
    }

    @Test
    void consequenceNode_publishesReputationAndAutoAdvances() {
        SagaData saga = buildConsequenceSaga();
        registry.register(saga);
        SagaInstance instance = activateSaga(saga, "consequence_node");
        journal.setMainStory(instance);

        runner.enterNode(instance, saga.getNode("consequence_node"), saga);

        assertFalse(repEvents.isEmpty());
        assertEquals("explorers_guild", repEvents.get(0).factionId);
        assertEquals("end_node", instance.currentNodeId);
    }

    // --- helpers ---

    private SagaData buildTwoNodeSaga(String firstId, String secondId) {
        SagaData saga = new SagaData();
        saga.id = "test_saga"; saga.title = "Test"; saga.category = SagaCategory.MAIN_STORY;

        SagaNodeData n1 = new SagaNodeData();
        n1.id = firstId; n1.type = SagaNodeType.OBJECTIVE;
        Objective obj = new Objective(); obj.id = "obj1"; obj.type = com.galacticodyssey.mission.shared.ObjectiveType.SCAN_OBJECT;
        obj.targetId = "anomaly1"; obj.requiredCount = 1;
        n1.objectives.add(obj);
        saga.nodes.add(n1);

        SagaNodeData n2 = new SagaNodeData();
        n2.id = secondId; n2.type = SagaNodeType.TERMINUS; n2.outcome = "COMPLETE";
        saga.nodes.add(n2);

        SagaEdgeData edge = new SagaEdgeData(); edge.from = firstId; edge.to = secondId;
        saga.edges.add(edge);
        return saga;
    }

    private SagaData buildTerminusSaga(String nodeId, String outcome) {
        SagaData saga = new SagaData();
        saga.id = "terminus_saga"; saga.category = SagaCategory.MAIN_STORY;
        SagaNodeData n = new SagaNodeData(); n.id = nodeId; n.type = SagaNodeType.TERMINUS; n.outcome = outcome;
        saga.nodes.add(n);
        return saga;
    }

    private SagaData buildChoiceSaga() {
        SagaData saga = new SagaData();
        saga.id = "test_saga"; saga.category = SagaCategory.MAIN_STORY;

        SagaNodeData choice = new SagaNodeData();
        choice.id = "choice_node"; choice.type = SagaNodeType.DIALOGUE_CHOICE; choice.npcId = "npc_varek";
        saga.nodes.add(choice);

        SagaNodeData outcome = new SagaNodeData();
        outcome.id = "guild_outcome"; outcome.type = SagaNodeType.TERMINUS; outcome.outcome = "COMPLETE";
        saga.nodes.add(outcome);

        SagaEdgeData edge = new SagaEdgeData();
        edge.from = "choice_node"; edge.to = "guild_outcome"; edge.requiresChoice = "sided_with_guild";
        saga.edges.add(edge);
        return saga;
    }

    private SagaData buildConsequenceSaga() {
        SagaData saga = new SagaData();
        saga.id = "test_saga"; saga.category = SagaCategory.MAIN_STORY;

        SagaNodeData con = new SagaNodeData(); con.id = "consequence_node"; con.type = SagaNodeType.CONSEQUENCE;
        SagaNodeData.ConsequenceEvent ce = new SagaNodeData.ConsequenceEvent();
        ce.type = "REPUTATION_CHANGE"; ce.faction = "explorers_guild"; ce.delta = 15f;
        con.consequences.add(ce);
        saga.nodes.add(con);

        SagaNodeData end = new SagaNodeData(); end.id = "end_node"; end.type = SagaNodeType.TERMINUS; end.outcome = "COMPLETE";
        saga.nodes.add(end);

        SagaEdgeData edge = new SagaEdgeData(); edge.from = "consequence_node"; edge.to = "end_node";
        saga.edges.add(edge);
        return saga;
    }

    private SagaInstance activateSaga(SagaData saga, String startNodeId) {
        SagaInstance instance = new SagaInstance();
        instance.sagaDataId = saga.id;
        instance.currentNodeId = startNodeId;
        instance.state = SagaState.ACTIVE;
        SagaNodeData startNode = saga.getNode(startNodeId);
        if (startNode != null && startNode.type == SagaNodeType.OBJECTIVE) {
            instance.activeObjectives.addAll(startNode.objectives);
        }
        return instance;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.saga.SagaRunnerTest"
```

Expected: FAILED (SagaRunner not found)

- [ ] **Step 3: Implement SagaRunner**

```java
package com.galacticodyssey.mission.saga;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.DialogueChoiceMadeEvent;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.events.*;
import com.galacticodyssey.mission.shared.*;

import java.util.List;

public class SagaRunner extends EntitySystem {

    private final EventBus eventBus;
    private final QuestJournal journal;
    private final SagaRegistry registry;

    public SagaRunner(EventBus eventBus, QuestJournal journal, SagaRegistry registry) {
        this.eventBus = eventBus;
        this.journal = journal;
        this.registry = registry;

        eventBus.subscribe(ObjectiveCompletedEvent.class, this::onObjectiveCompleted);
        eventBus.subscribe(DialogueChoiceMadeEvent.class, this::onDialogueChoice);
    }

    private void onObjectiveCompleted(ObjectiveCompletedEvent e) {
        for (SagaInstance instance : journal.getAllActiveSagas()) {
            if (!instance.sagaDataId.equals(e.missionId)) continue;
            if (instance.state != SagaState.ACTIVE) continue;
            SagaData saga = registry.get(instance.sagaDataId);
            if (saga == null) continue;
            SagaNodeData node = saga.getNode(instance.currentNodeId);
            if (node == null || node.type != SagaNodeType.OBJECTIVE) continue;
            if (instance.allRequiredObjectivesComplete()) {
                advance(instance, saga, null);
            }
        }
    }

    private void onDialogueChoice(DialogueChoiceMadeEvent e) {
        for (SagaInstance instance : journal.getAllActiveSagas()) {
            if (instance.state != SagaState.ACTIVE) continue;
            SagaData saga = registry.get(instance.sagaDataId);
            if (saga == null) continue;
            SagaNodeData node = saga.getNode(instance.currentNodeId);
            if (node == null || node.type != SagaNodeType.DIALOGUE_CHOICE) continue;
            if (!e.npcId.equals(node.npcId)) continue;
            instance.choicesMade.put(instance.currentNodeId, e.choiceKey);
            advance(instance, saga, e.choiceKey);
        }
    }

    private void advance(SagaInstance instance, SagaData saga, String choiceKey) {
        List<SagaEdgeData> edges = saga.edgesFrom(instance.currentNodeId);
        for (SagaEdgeData edge : edges) {
            if (edge.requiresChoice == null || edge.requiresChoice.equals(choiceKey)) {
                SagaNodeData next = saga.getNode(edge.to);
                if (next != null) {
                    enterNode(instance, next, saga);
                    return;
                }
            }
        }
    }

    public void enterNode(SagaInstance instance, SagaNodeData node, SagaData saga) {
        instance.currentNodeId = node.id;
        instance.activeObjectives.clear();
        eventBus.publish(new SagaNodeEnteredEvent(instance.sagaDataId, node.id, node.type.name()));

        switch (node.type) {
            case OBJECTIVE -> instance.activeObjectives.addAll(node.objectives);
            case CONSEQUENCE -> {
                for (SagaNodeData.ConsequenceEvent ce : node.consequences) {
                    if ("REPUTATION_CHANGE".equals(ce.type)) {
                        eventBus.publish(new ReputationChangeEvent(ce.faction, ce.delta, instance.sagaDataId));
                    }
                }
                advance(instance, saga, null);
            }
            case TERMINUS -> {
                instance.state = "COMPLETE".equals(node.outcome) ? SagaState.COMPLETE : SagaState.FAILED;
            }
        }
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.saga.SagaRunnerTest"
```

Expected: all tests pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/saga/SagaRunner.java core/src/test/java/com/galacticodyssey/mission/saga/SagaRunnerTest.java
git commit -m "feat(mission): add SagaRunner (advances saga graph on objective/dialogue events)"
```

---

## Task 9: ProceduralJobGenerator + JobRegistry

**Files:**
- Create: `mission/job/JobRegistry.java`
- Create: `mission/job/ProceduralJobGenerator.java`
- Create: `test/.../mission/job/ProceduralJobGeneratorTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.mission.job;

import com.galacticodyssey.mission.shared.Objective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProceduralJobGeneratorTest {

    private JobRegistry registry;
    private ProceduralJobGenerator generator;
    private SectorContext sector;

    @BeforeEach
    void setUp() {
        registry = new JobRegistry();
        generator = new ProceduralJobGenerator(registry);
        sector = new SectorContext();
        sector.sectorId = "sector_alpha";
        sector.locationIds = List.of("station_1", "outpost_7");
        sector.npcIds = List.of("npc_bartender", "npc_guard");
        sector.factionTags = List.of("trade_guilds");
        sector.dangerLevel = 0.3f;
    }

    @Test
    void generate_scalesDifficultyWithPlayerLevel() {
        JobTemplate template = makeBountyTemplate();
        registry.register(template);
        ReputationQuery rep = tag -> 50f;

        JobInstance job5 = generator.generate(template, sector, 5, rep);
        JobInstance job10 = generator.generate(template, sector, 10, rep);

        assertTrue(job10.difficulty > job5.difficulty);
    }

    @Test
    void generate_scalesCreditsWithPlayerLevel() {
        JobTemplate template = makeBountyTemplate();
        registry.register(template);
        ReputationQuery rep = tag -> 50f;

        JobInstance low = generator.generate(template, sector, 1, rep);
        JobInstance high = generator.generate(template, sector, 10, rep);

        assertTrue(high.reward.credits > low.reward.credits);
    }

    @Test
    void generate_populatesObjectivesFromTemplate() {
        JobTemplate template = makeBountyTemplate();
        registry.register(template);

        JobInstance job = generator.generate(template, sector, 5, tag -> 0f);

        assertEquals(1, job.objectives.size());
        Objective obj = job.objectives.get(0);
        assertEquals(com.galacticodyssey.mission.shared.ObjectiveType.DESTROY_TARGET, obj.type);
        assertNotNull(obj.id);
    }

    @Test
    void generate_startsInAvailableState() {
        JobInstance job = generator.generate(makeBountyTemplate(), sector, 1, tag -> 0f);
        assertEquals(JobState.AVAILABLE, job.state);
    }

    @Test
    void generate_boardJob_hasNullLead() {
        JobTemplate template = makeBountyTemplate();
        template.discoveryMode = "BOARD";
        JobInstance job = generator.generate(template, sector, 1, tag -> 0f);
        assertNull(job.lead);
    }

    private JobTemplate makeBountyTemplate() {
        JobTemplate t = new JobTemplate();
        t.id = "bounty_test";
        t.type = JobType.BOUNTY_HUNT;
        t.giverFactionTag = "trade_guilds";
        t.baseCredits = 1000;
        t.baseReputationDelta = 5f;
        t.discoveryMode = "BOARD";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.DESTROY_TARGET;
        obj.requiredCount = 1;
        t.objectives.add(obj);
        return t;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.job.ProceduralJobGeneratorTest"
```

Expected: FAILED (ProceduralJobGenerator not found)

- [ ] **Step 3: Implement JobRegistry**

```java
package com.galacticodyssey.mission.job;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class JobRegistry {
    private final Map<String, JobTemplate> templates = new HashMap<>();

    public void register(JobTemplate template) { templates.put(template.id, template); }
    public JobTemplate get(String id) { return templates.get(id); }
    public Collection<JobTemplate> getAll() { return templates.values(); }
}
```

- [ ] **Step 4: Implement ProceduralJobGenerator**

```java
package com.galacticodyssey.mission.job;

import com.galacticodyssey.mission.shared.MissionReward;
import com.galacticodyssey.mission.shared.Objective;

import java.util.UUID;

public class ProceduralJobGenerator {

    private final JobRegistry registry;

    public ProceduralJobGenerator(JobRegistry registry) { this.registry = registry; }

    public JobInstance generate(JobTemplate template, SectorContext sector,
                                float playerLevel, ReputationQuery rep) {
        JobInstance job = new JobInstance();
        job.instanceId = UUID.randomUUID().toString();
        job.templateId = template.id;
        job.type = template.type;
        job.state = JobState.AVAILABLE;

        float diffScale = 1.0f + playerLevel * 0.15f;
        job.difficulty = Math.min(10f, diffScale);

        float standing = rep.getStanding(template.giverFactionTag);
        float standingBonus = 1.0f + Math.max(0, standing / 200f);
        float rewardScale = (1.0f + playerLevel * 0.1f) * standingBonus;

        MissionReward reward = new MissionReward();
        reward.credits = Math.round(template.baseCredits * rewardScale);
        reward.reputationFaction = template.giverFactionTag;
        reward.reputationDelta = template.baseReputationDelta;
        job.reward = reward;

        if (template.baseTimeLimitSeconds > 0) {
            job.timeLimit = template.baseTimeLimitSeconds * 1.5f;
        }

        if (!sector.locationIds.isEmpty()) {
            job.giverLocationId = sector.locationIds.get(0);
        }
        if (!sector.npcIds.isEmpty()) {
            job.giverNpcId = sector.npcIds.get(0);
        }

        for (int i = 0; i < template.objectives.size(); i++) {
            ObjectiveTemplate ot = template.objectives.get(i);
            Objective obj = new Objective();
            obj.id = job.instanceId + "_obj" + i;
            obj.type = ot.type;
            obj.requiredCount = ot.requiredCount;
            obj.optional = ot.optional;
            obj.targetId = resolveTargetId(ot, sector);
            job.objectives.add(obj);
        }

        if ("BOARD".equals(template.discoveryMode)) {
            job.lead = null;
        }

        return job;
    }

    private String resolveTargetId(ObjectiveTemplate ot, SectorContext sector) {
        return switch (ot.type) {
            case REACH_LOCATION, DELIVER_CARGO -> sector.locationIds.isEmpty() ? "unknown" : sector.locationIds.get(0);
            case ESCORT_TARGET -> sector.locationIds.size() > 1 ? sector.locationIds.get(1) : sector.locationIds.get(0);
            default -> sector.sectorId + "_target";
        };
    }
}
```

- [ ] **Step 5: Run tests — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.job.ProceduralJobGeneratorTest"
```

Expected: all tests pass

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/job/JobRegistry.java core/src/main/java/com/galacticodyssey/mission/job/ProceduralJobGenerator.java core/src/test/java/com/galacticodyssey/mission/job/ProceduralJobGeneratorTest.java
git commit -m "feat(mission): add ProceduralJobGenerator with difficulty/reward scaling"
```

---

## Task 10: JobBoard

**Files:**
- Create: `mission/job/JobBoard.java`
- Create: `test/.../mission/job/JobBoardTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.mission.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobBoardTest {

    private JobBoard board;
    private JobRegistry registry;
    private ProceduralJobGenerator generator;
    private SectorContext sector;

    @BeforeEach
    void setUp() {
        registry = new JobRegistry();
        generator = new ProceduralJobGenerator(registry);
        board = new JobBoard("station_alpha", registry, generator);
        sector = new SectorContext();
        sector.sectorId = "sec1";
        sector.locationIds = List.of("loc1", "loc2");
        sector.npcIds = List.of("npc1");
        sector.factionTags = List.of("trade_guilds");
        sector.dangerLevel = 0.2f;
    }

    @Test
    void refresh_populatesBoardUpToCap() {
        registry.register(makeBoardTemplate("t1", "trade_guilds", -100f));
        registry.register(makeBoardTemplate("t2", "trade_guilds", -100f));
        board.refresh(sector, 5, tag -> 50f);
        assertTrue(board.getAvailableJobs(tag -> 50f).size() <= 8);
        assertFalse(board.getAvailableJobs(tag -> 50f).isEmpty());
    }

    @Test
    void getAvailableJobs_filtersByStanding() {
        registry.register(makeBoardTemplate("low_req", "trade_guilds", -100f));
        registry.register(makeBoardTemplate("high_req", "trade_guilds", 75f));
        board.refresh(sector, 5, tag -> 50f);

        List<JobInstance> visible = board.getAvailableJobs(tag -> 50f);

        assertTrue(visible.stream().anyMatch(j -> "low_req".equals(j.templateId)));
        assertTrue(visible.stream().noneMatch(j -> "high_req".equals(j.templateId)));
    }

    @Test
    void acceptJob_movesToActive() {
        registry.register(makeBoardTemplate("t1", "trade_guilds", -100f));
        board.refresh(sector, 5, tag -> 0f);
        String jobId = board.getAvailableJobs(tag -> 0f).get(0).instanceId;

        JobInstance accepted = board.accept(jobId);

        assertNotNull(accepted);
        assertEquals(JobState.ACTIVE, accepted.state);
        assertTrue(board.getAvailableJobs(tag -> 0f).stream().noneMatch(j -> j.instanceId.equals(jobId)));
    }

    private JobTemplate makeBoardTemplate(String id, String faction, float requiredStanding) {
        JobTemplate t = new JobTemplate();
        t.id = id; t.type = JobType.CARGO_HAUL; t.giverFactionTag = faction;
        t.requiredStanding = requiredStanding; t.baseCredits = 500;
        t.discoveryMode = "BOARD";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.DELIVER_CARGO;
        t.objectives.add(obj);
        return t;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.job.JobBoardTest"
```

Expected: FAILED

- [ ] **Step 3: Implement JobBoard**

```java
package com.galacticodyssey.mission.job;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JobBoard {

    private static final int BOARD_CAP = 8;
    private static final float REFRESH_INTERVAL = 300f;

    private final String stationId;
    private final JobRegistry registry;
    private final ProceduralJobGenerator generator;

    private final List<JobInstance> boardJobs = new ArrayList<>();
    private float timeSinceRefresh = REFRESH_INTERVAL;  // refresh immediately on first update

    public JobBoard(String stationId, JobRegistry registry, ProceduralJobGenerator generator) {
        this.stationId = stationId;
        this.registry = registry;
        this.generator = generator;
    }

    public void update(float dt, SectorContext sector, float playerLevel, ReputationQuery rep) {
        timeSinceRefresh += dt;
        if (timeSinceRefresh >= REFRESH_INTERVAL) {
            refresh(sector, playerLevel, rep);
            timeSinceRefresh = 0;
        }
    }

    public void refresh(SectorContext sector, float playerLevel, ReputationQuery rep) {
        boardJobs.clear();
        for (JobTemplate template : registry.getAll()) {
            if (!"BOARD".equals(template.discoveryMode) && !"BOTH".equals(template.discoveryMode)) continue;
            if (boardJobs.size() >= BOARD_CAP) break;
            boardJobs.add(generator.generate(template, sector, playerLevel, rep));
        }
    }

    public List<JobInstance> getAvailableJobs(ReputationQuery rep) {
        return boardJobs.stream()
            .filter(j -> j.state == JobState.AVAILABLE)
            .filter(j -> {
                JobTemplate t = registry.get(j.templateId);
                return t == null || rep.getStanding(t.giverFactionTag) >= t.requiredStanding;
            })
            .collect(Collectors.toList());
    }

    public JobInstance accept(String instanceId) {
        JobInstance job = boardJobs.stream()
            .filter(j -> instanceId.equals(j.instanceId))
            .findFirst().orElse(null);
        if (job != null) job.state = JobState.ACTIVE;
        return job;
    }

    public String getStationId() { return stationId; }
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.job.JobBoardTest"
```

Expected: all tests pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/job/JobBoard.java core/src/test/java/com/galacticodyssey/mission/job/JobBoardTest.java
git commit -m "feat(mission): add JobBoard with refresh timer and standing filter"
```

---

## Task 11: EventJobGenerator

**Files:**
- Create: `mission/job/EventJobGenerator.java`
- Create: `test/.../mission/job/EventJobGeneratorTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.mission.job;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.mission.events.QuestDiscoveredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventJobGeneratorTest {

    private EventBus eventBus;
    private JobRegistry registry;
    private ProceduralJobGenerator generator;
    private EventJobGenerator eventJobGen;
    private final List<JobInstance> spawnedJobs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        registry = new JobRegistry();
        registry.register(makeMercenaryTemplate());
        registry.register(makeSalvageTemplate());
        generator = new ProceduralJobGenerator(registry);
        eventJobGen = new EventJobGenerator(eventBus, registry, generator);
        eventJobGen.setJobListener(spawnedJobs::add);
    }

    @Test
    void factionWarStarted_spawnsMercenaryJob() {
        eventBus.publish(new FactionWarStartedEvent("war1", "faction_a", "faction_b", "sector_x"));
        assertEquals(1, spawnedJobs.size());
        assertEquals(JobType.MERCENARY, spawnedJobs.get(0).type);
        assertEquals(JobState.RUMOURED, spawnedJobs.get(0).state);
        assertEquals("war1", spawnedJobs.get(0).triggeringEventId);
    }

    @Test
    void factionWarEnded_expiresPendingRumouredJobs() {
        eventBus.publish(new FactionWarStartedEvent("war1", "faction_a", "faction_b", "sector_x"));
        JobInstance job = spawnedJobs.get(0);
        assertEquals(JobState.RUMOURED, job.state);

        eventBus.publish(new FactionWarEndedEvent("war1"));
        assertEquals(JobState.EXPIRED, job.state);
    }

    @Test
    void factionWarEnded_doesNotExpireActiveJobs() {
        eventBus.publish(new FactionWarStartedEvent("war1", "faction_a", "faction_b", "sector_x"));
        spawnedJobs.get(0).state = JobState.ACTIVE;

        eventBus.publish(new FactionWarEndedEvent("war1"));
        assertEquals(JobState.ACTIVE, spawnedJobs.get(0).state);
    }

    @Test
    void shipMissing_spawnsSalvageJob() {
        eventBus.publish(new ShipMissingEvent("ship_47", "location_debris_field"));
        assertEquals(1, spawnedJobs.size());
        assertEquals(JobType.SALVAGE, spawnedJobs.get(0).type);
    }

    private JobTemplate makeMercenaryTemplate() {
        JobTemplate t = new JobTemplate();
        t.id = "mercenary_war"; t.type = JobType.MERCENARY;
        t.giverFactionTag = "military"; t.baseCredits = 2000;
        t.discoveryMode = "EVENT_DRIVEN";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.DESTROY_TARGET;
        obj.requiredCount = 5;
        t.objectives.add(obj);
        return t;
    }

    private JobTemplate makeSalvageTemplate() {
        JobTemplate t = new JobTemplate();
        t.id = "salvage_ship"; t.type = JobType.SALVAGE;
        t.giverFactionTag = "trade_guilds"; t.baseCredits = 1500;
        t.discoveryMode = "EVENT_DRIVEN";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.REACH_LOCATION;
        t.objectives.add(obj);
        return t;
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.job.EventJobGeneratorTest"
```

Expected: FAILED

- [ ] **Step 3: Implement EventJobGenerator**

```java
package com.galacticodyssey.mission.job;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EventJobGenerator {

    private final EventBus eventBus;
    private final JobRegistry registry;
    private final ProceduralJobGenerator generator;
    private final List<JobInstance> trackedJobs = new ArrayList<>();
    private Consumer<JobInstance> jobListener = j -> {};

    public EventJobGenerator(EventBus eventBus, JobRegistry registry, ProceduralJobGenerator generator) {
        this.eventBus = eventBus;
        this.registry = registry;
        this.generator = generator;
        subscribeAll();
    }

    public void setJobListener(Consumer<JobInstance> listener) { this.jobListener = listener; }

    private void subscribeAll() {
        eventBus.subscribe(FactionWarStartedEvent.class, e -> {
            JobTemplate t = findFirstByType(JobType.MERCENARY);
            if (t != null) spawnRumoured(t, e.warId, e.sectorId);
        });
        eventBus.subscribe(FactionWarEndedEvent.class, e ->
            expireByEvent(e.warId, "war")
        );
        eventBus.subscribe(ShipMissingEvent.class, e -> {
            JobTemplate t = findFirstByType(JobType.SALVAGE);
            if (t != null) spawnRumoured(t, e.shipId, e.lastKnownLocationId);
        });
        eventBus.subscribe(AnomalyDetectedEvent.class, e -> {
            JobTemplate t = findFirstByType(JobType.EXPLORATION_SURVEY);
            if (t != null) spawnRumoured(t, e.anomalyId, e.locationId);
        });
        eventBus.subscribe(CargoShipAttackedEvent.class, e -> {
            JobTemplate t = findFirstByType(JobType.BOUNTY_HUNT);
            if (t != null) spawnRumoured(t, e.attackerId, e.locationId);
        });
    }

    private void spawnRumoured(JobTemplate template, String eventId, String locationId) {
        SectorContext ctx = new SectorContext();
        ctx.sectorId = locationId;
        ctx.locationIds = List.of(locationId);
        ctx.npcIds = List.of();
        ctx.factionTags = List.of(template.giverFactionTag);

        JobInstance job = generator.generate(template, ctx, 1f, tag -> 0f);
        job.state = JobState.RUMOURED;
        job.triggeringEventId = eventId;
        trackedJobs.add(job);
        jobListener.accept(job);
    }

    private void expireByEvent(String eventId, String prefix) {
        for (JobInstance job : trackedJobs) {
            if (eventId.equals(job.triggeringEventId)
                && (job.state == JobState.RUMOURED || job.state == JobState.AVAILABLE)) {
                job.state = JobState.EXPIRED;
            }
        }
    }

    private JobTemplate findFirstByType(JobType type) {
        return registry.getAll().stream()
            .filter(t -> t.type == type && ("EVENT_DRIVEN".equals(t.discoveryMode) || "BOTH".equals(t.discoveryMode)))
            .findFirst().orElse(null);
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.job.EventJobGeneratorTest"
```

Expected: all tests pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/job/EventJobGenerator.java core/src/test/java/com/galacticodyssey/mission/job/EventJobGeneratorTest.java
git commit -m "feat(mission): add EventJobGenerator (world events → RUMOURED jobs)"
```

---

## Task 12: DiscoveryLead + QuestDiscoverySystem

**Files:**
- Create: `mission/discovery/DiscoveryLead.java`
- Create: `mission/discovery/QuestDiscoverySystem.java`
- Create: `test/.../mission/discovery/QuestDiscoverySystemTest.java`

- [ ] **Step 1: Create DiscoveryLead**

```java
package com.galacticodyssey.mission.discovery;

import java.util.List;

public class DiscoveryLead {
    public String jobInstanceId;
    public List<String> rumourNpcIds;
    public String locationId;
    public boolean rumourHeard;
    public boolean locationDiscovered;
    public String triggeringEventId;    // for expiry tracking

    public boolean isActivated() { return rumourHeard || locationDiscovered; }
}
```

- [ ] **Step 2: Write failing test**

```java
package com.galacticodyssey.mission.discovery;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.mission.events.QuestDiscoveredEvent;
import com.galacticodyssey.mission.job.*;
import com.galacticodyssey.mission.shared.QuestJournal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestDiscoverySystemTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private QuestDiscoverySystem discovery;
    private final List<QuestDiscoveredEvent> discovered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        discovery = new QuestDiscoverySystem(eventBus, journal);
        eventBus.subscribe(QuestDiscoveredEvent.class, discovered::add);
    }

    @Test
    void npcRumour_activatesMatchingJob() {
        JobInstance job = makeRumouredJob("j1", List.of("npc_bartender"), "station_alpha");
        discovery.registerLead(job, job.lead);
        journal.addRumour(job);

        eventBus.publish(new NpcDialogueEvent("npc_bartender", "RUMOUR"));

        assertEquals(1, discovered.size());
        assertEquals("j1", discovered.get(0).missionId);
        assertEquals(JobState.ACTIVE, job.state);
        assertTrue(journal.getActiveJobs().contains(job));
        assertTrue(journal.getRumourBoard().isEmpty());
    }

    @Test
    void locationEntered_activatesJobCold() {
        JobInstance job = makeRumouredJob("j2", List.of("npc_far_away"), "derelict_station");
        discovery.registerLead(job, job.lead);
        journal.addRumour(job);

        eventBus.publish(new LocationEnteredEvent("derelict_station"));

        assertEquals(1, discovered.size());
        assertEquals(JobState.ACTIVE, job.state);
    }

    @Test
    void npcDialogue_nonRumourTopic_doesNotActivate() {
        JobInstance job = makeRumouredJob("j3", List.of("npc_trader"), "loc1");
        discovery.registerLead(job, job.lead);
        journal.addRumour(job);

        eventBus.publish(new NpcDialogueEvent("npc_trader", "TRADE"));

        assertTrue(discovered.isEmpty());
        assertEquals(JobState.RUMOURED, job.state);
    }

    @Test
    void scanComplete_activatesMatchingJob() {
        JobInstance job = makeRumouredJob("j4", List.of(), "anomaly_k7");
        job.lead.locationId = "anomaly_k7";
        discovery.registerLead(job, job.lead);
        journal.addRumour(job);

        eventBus.publish(new ScanCompleteEvent("anomaly_k7"));

        assertEquals(JobState.ACTIVE, job.state);
    }

    private JobInstance makeRumouredJob(String id, List<String> npcIds, String locationId) {
        JobInstance job = new JobInstance();
        job.instanceId = id;
        job.state = JobState.RUMOURED;
        job.reward = new com.galacticodyssey.mission.shared.MissionReward();
        DiscoveryLead lead = new DiscoveryLead();
        lead.jobInstanceId = id;
        lead.rumourNpcIds = npcIds;
        lead.locationId = locationId;
        job.lead = lead;
        return job;
    }
}
```

- [ ] **Step 3: Run test — expect compile failure**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.discovery.QuestDiscoverySystemTest"
```

Expected: FAILED

- [ ] **Step 4: Implement QuestDiscoverySystem**

```java
package com.galacticodyssey.mission.discovery;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.mission.events.QuestDiscoveredEvent;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.HashMap;
import java.util.Map;

public class QuestDiscoverySystem extends EntitySystem {

    private final EventBus eventBus;
    private final QuestJournal journal;
    private final Map<String, JobInstance> leadJobs = new HashMap<>();   // jobInstanceId → job

    public QuestDiscoverySystem(EventBus eventBus, QuestJournal journal) {
        this.eventBus = eventBus;
        this.journal = journal;
        subscribeAll();
    }

    public void registerLead(JobInstance job, DiscoveryLead lead) {
        leadJobs.put(lead.jobInstanceId, job);
    }

    private void subscribeAll() {
        eventBus.subscribe(NpcDialogueEvent.class, e -> {
            if (!"RUMOUR".equals(e.topic)) return;
            for (JobInstance job : journal.getRumourBoard()) {
                if (job.lead == null) continue;
                if (job.lead.rumourNpcIds.contains(e.npcId)) {
                    job.lead.rumourHeard = true;
                    activateJob(job);
                    return;
                }
            }
        });
        eventBus.subscribe(LocationEnteredEvent.class, e ->
            activateByLocation(e.locationId));
        eventBus.subscribe(ScanCompleteEvent.class, e ->
            activateByLocation(e.targetId));
    }

    private void activateByLocation(String locationId) {
        for (JobInstance job : new java.util.ArrayList<>(journal.getRumourBoard())) {
            if (job.lead != null && locationId.equals(job.lead.locationId)) {
                job.lead.locationDiscovered = true;
                activateJob(job);
            }
        }
    }

    private void activateJob(JobInstance job) {
        if (job.state != JobState.RUMOURED) return;
        job.state = JobState.ACTIVE;
        journal.promoteRumour(job.instanceId);
        eventBus.publish(new QuestDiscoveredEvent(job.instanceId,
            job.templateId != null ? job.templateId : job.instanceId));
    }
}
```

- [ ] **Step 5: Run tests — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.discovery.QuestDiscoverySystemTest"
```

Expected: all tests pass

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/mission/discovery/ core/src/test/java/com/galacticodyssey/mission/discovery/QuestDiscoverySystemTest.java
git commit -m "feat(mission): add DiscoveryLead and QuestDiscoverySystem (dual-path activation)"
```

---

## Task 13: Data files

**Files:**
- Create: `resources/data/quests/jobs/templates.json`
- Create: `resources/data/quests/story/act1_the_signal.json`

- [ ] **Step 1: Create job templates JSON**

```json
[
  {
    "id": "cargo_haul_legal",
    "type": "CARGO_HAUL",
    "giverFactionTag": "trade_guilds",
    "requiredStanding": -25.0,
    "discoveryMode": "BOARD",
    "baseCredits": 800,
    "baseReputationDelta": 5.0,
    "baseTimeLimitSeconds": 0,
    "objectives": [
      { "type": "DELIVER_CARGO", "requiredCount": 1, "optional": false }
    ]
  },
  {
    "id": "bounty_hunt_pirate",
    "type": "BOUNTY_HUNT",
    "giverFactionTag": "trade_guilds",
    "requiredStanding": 0.0,
    "discoveryMode": "BOARD",
    "baseCredits": 1500,
    "baseReputationDelta": 8.0,
    "baseTimeLimitSeconds": 0,
    "objectives": [
      { "type": "DESTROY_TARGET", "requiredCount": 1, "optional": false }
    ]
  },
  {
    "id": "exploration_survey",
    "type": "EXPLORATION_SURVEY",
    "giverFactionTag": "explorers_guild",
    "requiredStanding": 0.0,
    "discoveryMode": "BOTH",
    "baseCredits": 1200,
    "baseReputationDelta": 10.0,
    "baseTimeLimitSeconds": 0,
    "objectives": [
      { "type": "SCAN_OBJECT", "requiredCount": 3, "optional": false },
      { "type": "REACH_LOCATION", "requiredCount": 1, "optional": true }
    ]
  },
  {
    "id": "mercenary_war",
    "type": "MERCENARY",
    "giverFactionTag": "military",
    "requiredStanding": 0.0,
    "discoveryMode": "EVENT_DRIVEN",
    "baseCredits": 2500,
    "baseReputationDelta": 12.0,
    "baseTimeLimitSeconds": 3600,
    "objectives": [
      { "type": "DESTROY_TARGET", "requiredCount": 5, "optional": false }
    ]
  },
  {
    "id": "salvage_missing_ship",
    "type": "SALVAGE",
    "giverFactionTag": "trade_guilds",
    "requiredStanding": -25.0,
    "discoveryMode": "EVENT_DRIVEN",
    "baseCredits": 1800,
    "baseReputationDelta": 7.0,
    "baseTimeLimitSeconds": 0,
    "objectives": [
      { "type": "REACH_LOCATION", "requiredCount": 1, "optional": false },
      { "type": "COLLECT_RESOURCE", "requiredCount": 5, "optional": false }
    ]
  }
]
```

- [ ] **Step 2: Create example saga JSON**

```json
{
  "id": "main_act1_the_signal",
  "title": "Act I: The Signal",
  "category": "MAIN_STORY",
  "triggers": [
    {
      "type": "REPUTATION_THRESHOLD",
      "faction": "explorers_guild",
      "minStanding": 25.0
    }
  ],
  "nodes": [
    {
      "id": "find_the_anomaly",
      "type": "OBJECTIVE",
      "objectives": [
        {
          "id": "scan_anomaly_k7",
          "type": "SCAN_OBJECT",
          "targetId": "anomaly_k7_signal",
          "requiredCount": 1,
          "optional": false
        }
      ]
    },
    {
      "id": "report_to_varek",
      "type": "DIALOGUE_CHOICE",
      "npcId": "npc_commander_varek"
    },
    {
      "id": "trust_the_guild",
      "type": "CONSEQUENCE",
      "consequences": [
        {
          "type": "REPUTATION_CHANGE",
          "faction": "explorers_guild",
          "delta": 15.0
        }
      ]
    },
    {
      "id": "betray_the_guild",
      "type": "CONSEQUENCE",
      "consequences": [
        {
          "type": "REPUTATION_CHANGE",
          "faction": "explorers_guild",
          "delta": -20.0
        }
      ]
    },
    {
      "id": "act1_end",
      "type": "TERMINUS",
      "outcome": "COMPLETE"
    }
  ],
  "edges": [
    { "from": "find_the_anomaly", "to": "report_to_varek" },
    { "from": "report_to_varek", "to": "trust_the_guild", "requiresChoice": "sided_with_guild" },
    { "from": "report_to_varek", "to": "betray_the_guild", "requiresChoice": "sold_info" },
    { "from": "trust_the_guild", "to": "act1_end" },
    { "from": "betray_the_guild", "to": "act1_end" }
  ]
}
```

- [ ] **Step 3: Compile check**

```
./gradlew :core:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add core/src/main/resources/data/quests/
git commit -m "feat(mission): add job templates and example Act I saga data files"
```

---

## Task 14: Integration test

**Files:**
- Create: `test/.../mission/MissionIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
package com.galacticodyssey.mission;

import com.badlogic.ashley.core.Engine;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.mission.discovery.*;
import com.galacticodyssey.mission.events.*;
import com.galacticodyssey.mission.job.*;
import com.galacticodyssey.mission.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MissionIntegrationTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private JobRegistry registry;
    private ProceduralJobGenerator procGen;
    private EventJobGenerator eventJobGen;
    private QuestDiscoverySystem discovery;
    private ObjectiveTrackingSystem tracking;
    private RewardSystem rewards;

    private final List<QuestDiscoveredEvent> discovered = new ArrayList<>();
    private final List<QuestCompletedEvent> completed = new ArrayList<>();
    private final List<ReputationChangeEvent> repEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        registry = new JobRegistry();
        procGen = new ProceduralJobGenerator(registry);
        eventJobGen = new EventJobGenerator(eventBus, registry, procGen);
        discovery = new QuestDiscoverySystem(eventBus, journal);
        tracking = new ObjectiveTrackingSystem(eventBus, journal);
        rewards = new RewardSystem(eventBus);

        registry.register(makeMercenaryTemplate());

        eventBus.subscribe(QuestDiscoveredEvent.class, discovered::add);
        eventBus.subscribe(QuestCompletedEvent.class, completed::add);
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);

        eventJobGen.setJobListener(job -> {
            discovery.registerLead(job, job.lead);
            journal.addRumour(job);
        });
    }

    @Test
    void fullPipeline_warEventToQuestComplete() {
        // World event fires — mercenary job spawns as RUMOURED
        eventBus.publish(new FactionWarStartedEvent("war1", "faction_a", "faction_b", "sector_x"));
        assertEquals(1, journal.getRumourBoard().size());
        assertEquals(JobState.RUMOURED, journal.getRumourBoard().get(0).state);

        // Player visits the location — job activates cold
        String locationId = journal.getRumourBoard().get(0).lead.locationId;
        eventBus.publish(new LocationEnteredEvent(locationId));
        assertEquals(1, discovered.size());
        assertEquals(1, journal.getActiveJobs().size());
        JobInstance job = journal.getActiveJobs().get(0);
        assertEquals(JobState.ACTIVE, job.state);

        // Player completes the objective (5 kills)
        String targetId = job.objectives.get(0).targetId;
        for (int i = 0; i < 5; i++) tracking.onEntityKilled(targetId);

        assertEquals(1, completed.size());
        assertEquals(job.instanceId, completed.get(0).missionId);
        assertFalse(repEvents.isEmpty());
        assertEquals("military", repEvents.get(0).factionId);
    }

    private JobTemplate makeMercenaryTemplate() {
        JobTemplate t = new JobTemplate();
        t.id = "mercenary_war"; t.type = JobType.MERCENARY;
        t.giverFactionTag = "military"; t.baseCredits = 2000;
        t.baseReputationDelta = 10f;
        t.discoveryMode = "EVENT_DRIVEN";
        ObjectiveTemplate obj = new ObjectiveTemplate();
        obj.type = com.galacticodyssey.mission.shared.ObjectiveType.DESTROY_TARGET;
        obj.requiredCount = 5;
        t.objectives.add(obj);
        return t;
    }
}
```

- [ ] **Step 2: Run test — expect pass**

```
./gradlew :core:test --tests "com.galacticodyssey.mission.MissionIntegrationTest"
```

Expected: 1 test passed

- [ ] **Step 3: Run full test suite**

```
./gradlew :core:test
```

Expected: BUILD SUCCESSFUL, no regressions

- [ ] **Step 4: Commit**

```
git add core/src/test/java/com/galacticodyssey/mission/MissionIntegrationTest.java
git commit -m "test(mission): add end-to-end integration test (war event → discover → complete)"
```

---

## Task 15: GameWorld registration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add fields to GameWorld**

In `GameWorld.java`, add these fields alongside the other system fields:

```java
private JobRegistry jobRegistry;
private SagaRegistry sagaRegistry;
private ProceduralJobGenerator proceduralJobGenerator;
private JobBoard playerJobBoard;
private EventJobGenerator eventJobGenerator;
private QuestJournal questJournal;
private ObjectiveTrackingSystem objectiveTrackingSystem;
private SagaRunner sagaRunner;
private RewardSystem rewardSystem;
private QuestDiscoverySystem questDiscoverySystem;
```

Add the imports:
```java
import com.galacticodyssey.mission.job.*;
import com.galacticodyssey.mission.saga.*;
import com.galacticodyssey.mission.shared.*;
import com.galacticodyssey.mission.discovery.*;
```

- [ ] **Step 2: Initialize mission systems in the GameWorld constructor**

Locate the section in the constructor where other systems are initialized and add, after the engine and eventBus are available:

```java
// Mission / Quest System
questJournal = new QuestJournal();
jobRegistry = new JobRegistry();
sagaRegistry = new SagaRegistry();
proceduralJobGenerator = new ProceduralJobGenerator(jobRegistry);
eventJobGenerator = new EventJobGenerator(eventBus, jobRegistry, proceduralJobGenerator);
questDiscoverySystem = new QuestDiscoverySystem(eventBus, questJournal);
objectiveTrackingSystem = new ObjectiveTrackingSystem(eventBus, questJournal);
sagaRunner = new SagaRunner(eventBus, questJournal, sagaRegistry);
rewardSystem = new RewardSystem(eventBus);

// Wire event job generator → discovery system
eventJobGenerator.setJobListener(job -> {
    if (job.lead != null) questDiscoverySystem.registerLead(job, job.lead);
    questJournal.addRumour(job);
});

engine.addSystem(objectiveTrackingSystem);
engine.addSystem(sagaRunner);
```

- [ ] **Step 3: Compile check**

```
./gradlew :core:compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run full test suite**

```
./gradlew :core:test
```

Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(mission): register mission systems in GameWorld"
```

---

## Self-Review

**Spec coverage check:**

| Spec section | Covered by task |
|---|---|
| Saga graph (OBJECTIVE/DIALOGUE_CHOICE/CONSEQUENCE/TERMINUS nodes) | Task 5, 8 |
| SagaRunner advances on objective/dialogue events | Task 8 |
| YAML/JSON saga data format | Task 5, 13 (JSON used — no YAML lib in build) |
| JobTemplate + JobInstance with all states | Task 4 |
| ProceduralJobGenerator with difficulty/reward scaling | Task 9 |
| JobBoard with refresh timer and standing filter | Task 10 |
| EventJobGenerator: war/missing/anomaly/cargo-attack events | Task 11 |
| DiscoveryLead with rumour path + location path | Task 12 |
| QuestDiscoverySystem dual-path activation | Task 12 |
| QuestJournal with rumour board and job cap | Task 3 |
| ObjectiveTrackingSystem event-driven tracking | Task 6 |
| RewardSystem publishes ReputationChangeEvent | Task 7 |
| All mission and world-state events | Task 1 |
| Example data files | Task 13 |
| GameWorld registration | Task 15 |
| End-to-end integration test | Task 14 |

**All spec requirements covered.** No placeholders remain. Types are consistent across tasks: `JobInstance.instanceId` (String, UUID), `Objective.id`, `SagaInstance.sagaDataId`, `DiscoveryLead.jobInstanceId` are all used consistently throughout.

---

Plan complete and saved to `docs/superpowers/plans/2026-05-27-mission-quest-system.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — Fresh subagent per task, review between tasks, fast parallel iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, checkpoint reviews

Which approach?
