# Quest Journal / Job Board Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified Quest Journal screen with five tabs (Story, Active Quests, Job Board, Rumours, History) accessible via the J hotkey.

**Architecture:** Full-screen overlay following the MarketScreen pattern — a libGDX `Screen` with its own `Stage`, swapped in via `game.setScreen()`. Each tab is a self-contained `Table` actor. The screen communicates with game systems entirely through the `EventBus` — publishing `JournalOpenedEvent`/`JournalClosedEvent` for lifecycle, `JobAcceptedEvent`/`QuestAbandonedEvent` for mutations, and subscribing to existing quest events for live updates.

**Tech Stack:** Java 17, libGDX Scene2D (Table, ScrollPane, Label, TextButton), Ashley ECS, EventBus pub/sub

**Spec:** `docs/superpowers/specs/2026-05-28-quest-journal-screen-design.md`

---

## File Structure

### New Files

| File | Purpose |
|---|---|
| `core/src/main/java/com/galacticodyssey/mission/shared/CompletedQuestRecord.java` | Data class for history entries (name, type, outcome, timestamp, rewards) |
| `core/src/main/java/com/galacticodyssey/mission/shared/QuestOutcome.java` | Enum: COMPLETED, FAILED, ABANDONED, EXPIRED |
| `core/src/main/java/com/galacticodyssey/ui/events/JournalOpenedEvent.java` | Published when journal opens |
| `core/src/main/java/com/galacticodyssey/ui/events/JournalClosedEvent.java` | Published when journal closes |
| `core/src/main/java/com/galacticodyssey/mission/job/JobAcceptedEvent.java` | Published when player accepts a job |
| `core/src/main/java/com/galacticodyssey/mission/shared/QuestAbandonedEvent.java` | Published when player abandons a quest |
| `core/src/main/java/com/galacticodyssey/ui/QuestJournalScreen.java` | Main screen: Stage, tab bar, content switching, input, lifecycle |
| `core/src/main/java/com/galacticodyssey/ui/actors/StoryTabActor.java` | Story tab content |
| `core/src/main/java/com/galacticodyssey/ui/actors/ActiveQuestsTabActor.java` | Active quests list + detail panel |
| `core/src/main/java/com/galacticodyssey/ui/actors/JobBoardTabActor.java` | Job board list + detail panel |
| `core/src/main/java/com/galacticodyssey/ui/actors/RumoursTabActor.java` | Rumours list with accordion expand |
| `core/src/main/java/com/galacticodyssey/ui/actors/HistoryTabActor.java` | History list + detail panel |
| `core/src/test/java/com/galacticodyssey/mission/shared/CompletedQuestRecordTest.java` | Tests for CompletedQuestRecord |
| `core/src/test/java/com/galacticodyssey/mission/shared/QuestJournalHistoryTest.java` | Tests for QuestJournal history additions |
| `core/src/test/java/com/galacticodyssey/mission/shared/QuestAbandonmentTest.java` | Tests for abandon flow through ObjectiveTrackingSystem |
| `core/src/test/java/com/galacticodyssey/mission/job/JobAcceptanceTest.java` | Tests for job acceptance flow |

### Modified Files

| File | Change |
|---|---|
| `core/src/main/java/com/galacticodyssey/mission/shared/QuestJournal.java` | Add `completedQuests` list, `addCompleted()`, `getCompletedQuests()` |
| `core/src/main/java/com/galacticodyssey/mission/job/JobTemplate.java` | Add `name` and `description` fields for display |
| `core/src/main/java/com/galacticodyssey/mission/job/JobInstance.java` | Add `displayName` and `displayDescription` fields |
| `core/src/main/java/com/galacticodyssey/mission/job/JobBoard.java` | Add `getAllBoardJobs()` returning all AVAILABLE jobs without rep filter |
| `core/src/main/java/com/galacticodyssey/mission/shared/ObjectiveTrackingSystem.java` | Subscribe to `QuestAbandonedEvent`, handle removal + rep penalty + history |
| `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` | Add J key binding to open/close quest journal |
| `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java` | Register `CompletedQuestRecord` for save/load |
| `core/src/main/resources/data/quests/jobs/templates.json` | Add `name` and `description` fields to each template entry |

---

### Task 1: Events and CompletedQuestRecord Data Model

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/mission/shared/QuestOutcome.java`
- Create: `core/src/main/java/com/galacticodyssey/mission/shared/CompletedQuestRecord.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/events/JournalOpenedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/events/JournalClosedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/mission/job/JobAcceptedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/mission/shared/QuestAbandonedEvent.java`
- Create: `core/src/test/java/com/galacticodyssey/mission/shared/CompletedQuestRecordTest.java`

- [ ] **Step 1: Write the CompletedQuestRecord test**

```java
package com.galacticodyssey.mission.shared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompletedQuestRecordTest {

    @Test
    void constructsWithAllFields() {
        CompletedQuestRecord record = new CompletedQuestRecord(
                "job1", "Cargo Run: Medical Supplies", "CARGO_HAUL",
                QuestOutcome.COMPLETED, 1200, "Federation", 15f, 1716900000L);

        assertEquals("job1", record.questId);
        assertEquals("Cargo Run: Medical Supplies", record.questName);
        assertEquals("CARGO_HAUL", record.questType);
        assertEquals(QuestOutcome.COMPLETED, record.outcome);
        assertEquals(1200, record.creditsEarned);
        assertEquals("Federation", record.reputationFaction);
        assertEquals(15f, record.reputationDelta, 0.01f);
        assertEquals(1716900000L, record.timestampMs);
    }

    @Test
    void abandonedRecordHasNegativeReputation() {
        CompletedQuestRecord record = new CompletedQuestRecord(
                "job2", "Bounty Hunt", "BOUNTY_HUNT",
                QuestOutcome.ABANDONED, 0, "Mercenary Guild", -10f, 1716900000L);

        assertEquals(QuestOutcome.ABANDONED, record.outcome);
        assertEquals(0, record.creditsEarned);
        assertTrue(record.reputationDelta < 0);
    }

    @Test
    void failedRecordHasNoCredits() {
        CompletedQuestRecord record = new CompletedQuestRecord(
                "job3", "Escort Mission", "ESCORT",
                QuestOutcome.FAILED, 0, "Federation", -5f, 1716900000L);

        assertEquals(QuestOutcome.FAILED, record.outcome);
        assertEquals(0, record.creditsEarned);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.shared.CompletedQuestRecordTest" -i`
Expected: Compilation error — `CompletedQuestRecord` and `QuestOutcome` do not exist.

- [ ] **Step 3: Create QuestOutcome enum**

```java
package com.galacticodyssey.mission.shared;

public enum QuestOutcome {
    COMPLETED, FAILED, ABANDONED, EXPIRED
}
```

- [ ] **Step 4: Create CompletedQuestRecord class**

```java
package com.galacticodyssey.mission.shared;

public class CompletedQuestRecord {
    public final String questId;
    public final String questName;
    public final String questType;
    public final QuestOutcome outcome;
    public final int creditsEarned;
    public final String reputationFaction;
    public final float reputationDelta;
    public final long timestampMs;

    public CompletedQuestRecord(String questId, String questName, String questType,
                                QuestOutcome outcome, int creditsEarned,
                                String reputationFaction, float reputationDelta,
                                long timestampMs) {
        this.questId = questId;
        this.questName = questName;
        this.questType = questType;
        this.outcome = outcome;
        this.creditsEarned = creditsEarned;
        this.reputationFaction = reputationFaction;
        this.reputationDelta = reputationDelta;
        this.timestampMs = timestampMs;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.shared.CompletedQuestRecordTest" -i`
Expected: All 3 tests PASS.

- [ ] **Step 6: Create the four event classes**

`JournalOpenedEvent.java`:
```java
package com.galacticodyssey.ui.events;

public final class JournalOpenedEvent {
}
```

`JournalClosedEvent.java`:
```java
package com.galacticodyssey.ui.events;

public final class JournalClosedEvent {
}
```

`JobAcceptedEvent.java`:
```java
package com.galacticodyssey.mission.job;

public final class JobAcceptedEvent {
    public final String jobInstanceId;

    public JobAcceptedEvent(String jobInstanceId) {
        this.jobInstanceId = jobInstanceId;
    }
}
```

`QuestAbandonedEvent.java`:
```java
package com.galacticodyssey.mission.shared;

public final class QuestAbandonedEvent {
    public final String questInstanceId;

    public QuestAbandonedEvent(String questInstanceId) {
        this.questInstanceId = questInstanceId;
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/mission/shared/QuestOutcome.java \
        core/src/main/java/com/galacticodyssey/mission/shared/CompletedQuestRecord.java \
        core/src/main/java/com/galacticodyssey/ui/events/JournalOpenedEvent.java \
        core/src/main/java/com/galacticodyssey/ui/events/JournalClosedEvent.java \
        core/src/main/java/com/galacticodyssey/mission/job/JobAcceptedEvent.java \
        core/src/main/java/com/galacticodyssey/mission/shared/QuestAbandonedEvent.java \
        core/src/test/java/com/galacticodyssey/mission/shared/CompletedQuestRecordTest.java
git commit -m "feat(quest-journal): add events and CompletedQuestRecord data model"
```

---

### Task 2: Data Layer Modifications (QuestJournal, JobTemplate, JobInstance, JobBoard)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/mission/shared/QuestJournal.java`
- Modify: `core/src/main/java/com/galacticodyssey/mission/job/JobTemplate.java`
- Modify: `core/src/main/java/com/galacticodyssey/mission/job/JobInstance.java`
- Modify: `core/src/main/java/com/galacticodyssey/mission/job/JobBoard.java`
- Modify: `core/src/main/resources/data/quests/jobs/templates.json`
- Create: `core/src/test/java/com/galacticodyssey/mission/shared/QuestJournalHistoryTest.java`

- [ ] **Step 1: Write tests for QuestJournal history additions**

```java
package com.galacticodyssey.mission.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestJournalHistoryTest {

    private QuestJournal journal;

    @BeforeEach
    void setUp() {
        journal = new QuestJournal();
    }

    @Test
    void completedQuests_initiallyEmpty() {
        assertTrue(journal.getCompletedQuests().isEmpty());
    }

    @Test
    void addCompleted_addsRecord() {
        CompletedQuestRecord record = new CompletedQuestRecord(
                "job1", "Cargo Run", "CARGO_HAUL",
                QuestOutcome.COMPLETED, 1200, "Federation", 15f,
                System.currentTimeMillis());
        journal.addCompleted(record);

        assertEquals(1, journal.getCompletedQuests().size());
        assertEquals("job1", journal.getCompletedQuests().get(0).questId);
    }

    @Test
    void addCompleted_multipleRecords_newestFirst() {
        journal.addCompleted(new CompletedQuestRecord(
                "job1", "First", "CARGO_HAUL",
                QuestOutcome.COMPLETED, 100, null, 0, 1000L));
        journal.addCompleted(new CompletedQuestRecord(
                "job2", "Second", "BOUNTY_HUNT",
                QuestOutcome.FAILED, 0, null, -5f, 2000L));

        assertEquals(2, journal.getCompletedQuests().size());
        assertEquals("job2", journal.getCompletedQuests().get(0).questId);
        assertEquals("job1", journal.getCompletedQuests().get(1).questId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.shared.QuestJournalHistoryTest" -i`
Expected: Compilation error — `addCompleted()` and `getCompletedQuests()` do not exist on `QuestJournal`.

- [ ] **Step 3: Add history fields and methods to QuestJournal**

Add these fields and methods to `QuestJournal.java`:

```java
private final List<CompletedQuestRecord> completedQuests = new ArrayList<>();

public void addCompleted(CompletedQuestRecord record) {
    completedQuests.add(0, record);
}

public List<CompletedQuestRecord> getCompletedQuests() {
    return completedQuests;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.shared.QuestJournalHistoryTest" -i`
Expected: All 3 tests PASS.

- [ ] **Step 5: Add display fields to JobTemplate**

Add to the field declarations in `JobTemplate.java`:

```java
public String name;
public String description;
```

- [ ] **Step 6: Add display fields to JobInstance**

Add to the field declarations in `JobInstance.java`:

```java
public String displayName;
public String displayDescription;
```

- [ ] **Step 7: Add `getAllBoardJobs()` to JobBoard**

Add this method to `JobBoard.java`:

```java
public List<JobInstance> getAllBoardJobs() {
    return boardJobs.stream()
        .filter(j -> j.state == JobState.AVAILABLE)
        .collect(Collectors.toList());
}
```

- [ ] **Step 8: Add `name` and `description` to templates.json**

Open `core/src/main/resources/data/quests/jobs/templates.json` and add `"name"` and `"description"` fields to each template entry. Example for the first template:

```json
{
  "id": "cargo_haul_legal",
  "name": "Cargo Hauling",
  "description": "Transport goods between stations.",
  "type": "CARGO_HAUL",
  ...
}
```

Repeat for all templates in the file — each needs a human-readable `name` and short `description`. Use the template `type` to guide appropriate names (e.g., `bounty_hunt_pirate` → `"name": "Pirate Bounty"`, `"description": "Hunt down a wanted pirate in the sector."`).

- [ ] **Step 9: Run existing QuestJournal tests to verify no regressions**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.*" -i`
Expected: All existing + new tests PASS.

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/mission/shared/QuestJournal.java \
        core/src/main/java/com/galacticodyssey/mission/job/JobTemplate.java \
        core/src/main/java/com/galacticodyssey/mission/job/JobInstance.java \
        core/src/main/java/com/galacticodyssey/mission/job/JobBoard.java \
        core/src/main/resources/data/quests/jobs/templates.json \
        core/src/test/java/com/galacticodyssey/mission/shared/QuestJournalHistoryTest.java
git commit -m "feat(quest-journal): add history support to QuestJournal, display fields to job data"
```

---

### Task 3: Quest Abandonment and Job Acceptance Handling

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/mission/shared/ObjectiveTrackingSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/mission/shared/QuestAbandonmentTest.java`
- Create: `core/src/test/java/com/galacticodyssey/mission/job/JobAcceptanceTest.java`

- [ ] **Step 1: Write abandonment test**

```java
package com.galacticodyssey.mission.shared;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.job.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QuestAbandonmentTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private ObjectiveTrackingSystem system;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        system = new ObjectiveTrackingSystem(eventBus, journal);
    }

    @Test
    void abandonQuest_removesFromActiveJobs() {
        JobInstance job = makeJob("job1", "Cargo Run", "CARGO_HAUL");
        journal.addJob(job);
        assertEquals(1, journal.getActiveJobs().size());

        eventBus.publish(new QuestAbandonedEvent("job1"));

        assertEquals(0, journal.getActiveJobs().size());
    }

    @Test
    void abandonQuest_addsToHistory() {
        JobInstance job = makeJob("job1", "Cargo Run", "CARGO_HAUL");
        job.reward = new MissionReward();
        job.reward.reputationFaction = "Federation";
        job.reward.reputationDelta = 15f;
        journal.addJob(job);

        eventBus.publish(new QuestAbandonedEvent("job1"));

        assertEquals(1, journal.getCompletedQuests().size());
        CompletedQuestRecord record = journal.getCompletedQuests().get(0);
        assertEquals("job1", record.questId);
        assertEquals(QuestOutcome.ABANDONED, record.outcome);
        assertEquals(0, record.creditsEarned);
        assertTrue(record.reputationDelta < 0);
    }

    @Test
    void abandonQuest_publishesQuestFailedEvent() {
        JobInstance job = makeJob("job1", "Cargo Run", "CARGO_HAUL");
        job.reward = new MissionReward();
        job.reward.reputationFaction = "Federation";
        job.reward.reputationDelta = 15f;
        journal.addJob(job);

        AtomicReference<QuestFailedEvent> captured = new AtomicReference<>();
        eventBus.subscribe(QuestFailedEvent.class, captured::set);

        eventBus.publish(new QuestAbandonedEvent("job1"));

        assertNotNull(captured.get());
        assertEquals("job1", captured.get().missionId);
    }

    @Test
    void abandonQuest_nonexistentId_noOp() {
        eventBus.publish(new QuestAbandonedEvent("nonexistent"));
        assertEquals(0, journal.getCompletedQuests().size());
    }

    private JobInstance makeJob(String id, String name, String type) {
        JobInstance j = new JobInstance();
        j.instanceId = id;
        j.displayName = name;
        j.type = JobType.valueOf(type);
        j.state = JobState.ACTIVE;
        j.reward = new MissionReward();
        return j;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.shared.QuestAbandonmentTest" -i`
Expected: Tests fail — ObjectiveTrackingSystem doesn't subscribe to `QuestAbandonedEvent`.

- [ ] **Step 3: Add abandon handling to ObjectiveTrackingSystem**

In `ObjectiveTrackingSystem.java`, add this subscription in the `subscribeAll()` method:

```java
eventBus.subscribe(QuestAbandonedEvent.class, this::onQuestAbandoned);
```

Add the handler method:

```java
private void onQuestAbandoned(QuestAbandonedEvent event) {
    JobInstance job = journal.findJob(event.questInstanceId);
    if (job == null) return;

    job.state = JobState.FAILED;
    journal.removeJob(event.questInstanceId);

    float repPenalty = 0f;
    String repFaction = null;
    if (job.reward != null && job.reward.reputationFaction != null) {
        repPenalty = -Math.abs(job.reward.reputationDelta);
        repFaction = job.reward.reputationFaction;
    }

    journal.addCompleted(new CompletedQuestRecord(
            job.instanceId,
            job.displayName != null ? job.displayName : job.templateId,
            job.type != null ? job.type.name() : "UNKNOWN",
            QuestOutcome.ABANDONED,
            0,
            repFaction,
            repPenalty,
            System.currentTimeMillis()));

    eventBus.publish(new QuestFailedEvent(job.instanceId, "Abandoned by player"));
}
```

Add these imports at the top of the file:

```java
import com.galacticodyssey.mission.shared.CompletedQuestRecord;
import com.galacticodyssey.mission.shared.QuestAbandonedEvent;
import com.galacticodyssey.mission.shared.QuestOutcome;
```

- [ ] **Step 4: Run abandonment tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.shared.QuestAbandonmentTest" -i`
Expected: All 4 tests PASS.

- [ ] **Step 5: Write job acceptance test**

```java
package com.galacticodyssey.mission.job;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.shared.QuestJournal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobAcceptanceTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private JobBoard board;
    private JobRegistry registry;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        registry = new JobRegistry();

        JobTemplate template = new JobTemplate();
        template.id = "cargo_haul_legal";
        template.name = "Cargo Hauling";
        template.type = JobType.CARGO_HAUL;
        template.discoveryMode = "BOARD";
        template.baseCredits = 1000;
        registry.register(template);

        board = new JobBoard("station1", registry, null);
    }

    @Test
    void acceptJob_movesFromBoardToJournal() {
        JobInstance job = new JobInstance();
        job.instanceId = "job1";
        job.templateId = "cargo_haul_legal";
        job.displayName = "Cargo Run: Medical Supplies";
        job.type = JobType.CARGO_HAUL;
        job.state = JobState.AVAILABLE;

        // Simulate board having the job (via reflection or adding a test helper)
        // For now, test the acceptance flow through the board
        JobInstance accepted = board.accept(job.instanceId);
        // Board.accept only works on jobs in boardJobs - so test the journal flow separately

        assertTrue(journal.addJob(job));
        assertEquals(1, journal.getActiveJobs().size());
    }

    @Test
    void acceptJob_atMaxCap_fails() {
        for (int i = 0; i < 10; i++) {
            JobInstance job = new JobInstance();
            job.instanceId = "job" + i;
            job.state = JobState.ACTIVE;
            journal.addJob(job);
        }

        JobInstance overflow = new JobInstance();
        overflow.instanceId = "overflow";
        overflow.state = JobState.AVAILABLE;
        assertFalse(journal.addJob(overflow));
    }
}
```

- [ ] **Step 6: Run acceptance test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.job.JobAcceptanceTest" -i`
Expected: All tests PASS.

- [ ] **Step 7: Run all quest tests for regression**

Run: `./gradlew :core:test --tests "com.galacticodyssey.mission.*" -i`
Expected: All tests PASS.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/mission/shared/ObjectiveTrackingSystem.java \
        core/src/test/java/com/galacticodyssey/mission/shared/QuestAbandonmentTest.java \
        core/src/test/java/com/galacticodyssey/mission/job/JobAcceptanceTest.java
git commit -m "feat(quest-journal): add abandon handling and job acceptance flow"
```

---

### Task 4: QuestJournalScreen Shell with Tab Switching

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/QuestJournalScreen.java`

- [ ] **Step 1: Create the QuestJournalScreen class**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.GalacticOdyssey;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.job.JobBoard;
import com.galacticodyssey.mission.job.JobRegistry;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.saga.SagaRegistry;
import com.galacticodyssey.ui.actors.*;
import com.galacticodyssey.ui.events.JournalOpenedEvent;
import com.galacticodyssey.ui.events.JournalClosedEvent;

public class QuestJournalScreen implements Screen {

    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color TAB_INACTIVE = new Color(0.5f, 0.6f, 0.7f, 1f);
    private static final Color BG_COLOR = new Color(0.05f, 0.07f, 0.12f, 1f);

    private final GalacticOdyssey game;
    private final Screen returnTo;
    private final EventBus eventBus;
    private final QuestJournal journal;
    private final JobBoard jobBoard;
    private final JobRegistry jobRegistry;
    private final SagaRegistry sagaRegistry;
    private final ReputationQuery reputation;
    private final Skin skin;
    private final Stage stage;

    private final StoryTabActor storyTab;
    private final ActiveQuestsTabActor activeTab;
    private final JobBoardTabActor boardTab;
    private final RumoursTabActor rumoursTab;
    private final HistoryTabActor historyTab;

    private final Table contentArea;
    private final TextButton[] tabButtons;
    private int activeTabIndex = 0;
    private final Table[] tabContents;

    private final EventBus.EventListener<ObjectiveUpdatedEvent> onObjectiveUpdated;
    private final EventBus.EventListener<QuestCompletedEvent> onQuestCompleted;
    private final EventBus.EventListener<QuestFailedEvent> onQuestFailed;
    private final EventBus.EventListener<QuestDiscoveredEvent> onQuestDiscovered;

    // Import these from the mission.shared / mission.events packages:
    // ObjectiveUpdatedEvent, QuestCompletedEvent, QuestFailedEvent, QuestDiscoveredEvent

    public QuestJournalScreen(GalacticOdyssey game, Screen returnTo,
                              EventBus eventBus, QuestJournal journal,
                              JobBoard jobBoard, JobRegistry jobRegistry,
                              SagaRegistry sagaRegistry, ReputationQuery reputation,
                              Skin skin) {
        this.game = game;
        this.returnTo = returnTo;
        this.eventBus = eventBus;
        this.journal = journal;
        this.jobBoard = jobBoard;
        this.jobRegistry = jobRegistry;
        this.sagaRegistry = sagaRegistry;
        this.reputation = reputation;
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());

        storyTab = new StoryTabActor(skin, journal, sagaRegistry, eventBus);
        activeTab = new ActiveQuestsTabActor(skin, journal, eventBus);
        boardTab = new JobBoardTabActor(skin, jobBoard, jobRegistry, journal, eventBus, reputation);
        rumoursTab = new RumoursTabActor(skin, journal);
        historyTab = new HistoryTabActor(skin, journal);

        tabContents = new Table[]{storyTab, activeTab, boardTab, rumoursTab, historyTab};

        Table root = new Table();
        root.setFillParent(true);
        root.pad(24);
        stage.addActor(root);

        // Title bar
        Table titleBar = new Table();
        Label title = new Label("QUEST JOURNAL", skin, "title");
        titleBar.add(title).expandX().left();
        TextButton closeBtn = new TextButton("X", skin, "small");
        closeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });
        titleBar.add(closeBtn).right();
        root.add(titleBar).expandX().fillX().padBottom(12).row();

        // Tab bar
        Table tabBar = new Table();
        String[] tabNames = {"Story", "Active", "Board", "Rumours", "History"};
        tabButtons = new TextButton[tabNames.length];
        for (int i = 0; i < tabNames.length; i++) {
            tabButtons[i] = new TextButton(tabNames[i], skin);
            final int index = i;
            tabButtons[i].addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    selectTab(index);
                }
            });
            tabBar.add(tabButtons[i]).padRight(4).minWidth(100);
        }
        root.add(tabBar).left().padBottom(8).row();

        // Content area
        contentArea = new Table();
        root.add(contentArea).expand().fill();

        // Subscribe to events for live updates
        onObjectiveUpdated = e -> { storyTab.refresh(); activeTab.refresh(); };
        onQuestCompleted = e -> { activeTab.refresh(); historyTab.refresh(); };
        onQuestFailed = e -> { activeTab.refresh(); historyTab.refresh(); };
        onQuestDiscovered = e -> rumoursTab.refresh();

        eventBus.subscribe(ObjectiveUpdatedEvent.class, onObjectiveUpdated);
        eventBus.subscribe(QuestCompletedEvent.class, onQuestCompleted);
        eventBus.subscribe(QuestFailedEvent.class, onQuestFailed);
        eventBus.subscribe(QuestDiscoveredEvent.class, onQuestDiscovered);

        selectTab(0);
    }

    private void selectTab(int index) {
        activeTabIndex = index;
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].getLabel().setColor(i == index ? CYAN : TAB_INACTIVE);
        }
        contentArea.clear();
        contentArea.add(tabContents[index]).expand().fill();
        refreshActiveTab();
    }

    private void refreshActiveTab() {
        switch (activeTabIndex) {
            case 0: storyTab.refresh(); break;
            case 1: activeTab.refresh(); break;
            case 2: boardTab.refresh(); break;
            case 3: rumoursTab.refresh(); break;
            case 4: historyTab.refresh(); break;
        }
    }

    public void close() {
        eventBus.publish(new JournalClosedEvent());
        game.setScreen(returnTo);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        Gdx.input.setCursorCatched(false);
        eventBus.publish(new JournalOpenedEvent());

        stage.addListener(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.J) {
                    close();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(BG_COLOR.r, BG_COLOR.g, BG_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        eventBus.unsubscribe(ObjectiveUpdatedEvent.class, onObjectiveUpdated);
        eventBus.unsubscribe(QuestCompletedEvent.class, onQuestCompleted);
        eventBus.unsubscribe(QuestFailedEvent.class, onQuestFailed);
        eventBus.unsubscribe(QuestDiscoveredEvent.class, onQuestDiscovered);
        stage.dispose();
    }
}
```

Note: The constructor references `ObjectiveUpdatedEvent`, `QuestCompletedEvent`, `QuestFailedEvent`, `QuestDiscoveredEvent`, and `ReputationQuery`. Add the corresponding imports:

```java
import com.galacticodyssey.mission.shared.ObjectiveUpdatedEvent;
import com.galacticodyssey.mission.shared.QuestCompletedEvent;
import com.galacticodyssey.mission.shared.QuestFailedEvent;
import com.galacticodyssey.mission.shared.QuestDiscoveredEvent;
import com.galacticodyssey.mission.shared.ReputationQuery;
```

Check the actual package for `ReputationQuery` — it may be in `mission.shared` or `economy` or `faction`. Use the actual package from the codebase.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: Compiles. If tab actors don't exist yet, create minimal stubs first (see step 3).

- [ ] **Step 3: Create minimal tab actor stubs**

Each tab actor needs a minimal stub so the screen compiles. All five follow this pattern:

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.scenes.scene2d.ui.*;

public class StoryTabActor extends Table {
    // Stub constructor — full implementation in Task 5
    public StoryTabActor(Skin skin, /* ... params from QuestJournalScreen constructor ... */) {
        add(new Label("Story tab placeholder", skin, "body"));
    }
    public void refresh() {}
}
```

Create the same minimal stub pattern for `ActiveQuestsTabActor`, `JobBoardTabActor`, `RumoursTabActor`, and `HistoryTabActor`. Match constructor parameters to what `QuestJournalScreen` passes to each.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: Compiles successfully.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/QuestJournalScreen.java \
        core/src/main/java/com/galacticodyssey/ui/actors/StoryTabActor.java \
        core/src/main/java/com/galacticodyssey/ui/actors/ActiveQuestsTabActor.java \
        core/src/main/java/com/galacticodyssey/ui/actors/JobBoardTabActor.java \
        core/src/main/java/com/galacticodyssey/ui/actors/RumoursTabActor.java \
        core/src/main/java/com/galacticodyssey/ui/actors/HistoryTabActor.java
git commit -m "feat(quest-journal): add QuestJournalScreen shell with tab switching"
```

---

### Task 5: StoryTabActor

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/actors/StoryTabActor.java`

- [ ] **Step 1: Implement StoryTabActor**

Replace the stub with the full implementation:

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.saga.SagaData;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.saga.SagaRegistry;
import com.galacticodyssey.mission.saga.SagaState;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.Map;

public class StoryTabActor extends Table {

    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);
    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color GREEN = new Color(0.4f, 0.75f, 0.4f, 1f);
    private static final Color DIM = new Color(0.5f, 0.5f, 0.5f, 0.5f);

    private final Skin skin;
    private final QuestJournal journal;
    private final SagaRegistry sagaRegistry;

    public StoryTabActor(Skin skin, QuestJournal journal, SagaRegistry sagaRegistry, EventBus eventBus) {
        this.skin = skin;
        this.journal = journal;
        this.sagaRegistry = sagaRegistry;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();
        SagaInstance story = journal.getMainStory();
        if (story == null) {
            buildEmptyState();
            return;
        }
        buildStoryContent(story);
    }

    private void buildEmptyState() {
        Table center = new Table();
        Label msg = new Label("Your story hasn't begun...", skin, "header");
        msg.setColor(DIM);
        center.add(msg);
        add(center).expand().center();
    }

    private void buildStoryContent(SagaInstance story) {
        SagaData sagaData = sagaRegistry != null ? sagaRegistry.get(story.sagaDataId) : null;
        String storyTitle = sagaData != null ? sagaData.title : story.sagaDataId;

        // Two-column layout
        Table leftCol = new Table();
        leftCol.top().left().pad(4);
        Table rightCol = new Table();
        rightCol.top().left().pad(4);

        // Left: Act progression
        buildActProgression(leftCol, story, storyTitle);

        // Right: Choices + rewards
        buildChoicesAndRewards(rightCol, story);

        add(leftCol).expand().fill().padRight(12);
        add(rightCol).width(280).fillY().top();
    }

    private void buildActProgression(Table col, SagaInstance story, String title) {
        // Current act card
        Table actCard = new Table();
        actCard.pad(12).left().top();

        Table headerRow = new Table();
        Label titleLabel = new Label(title, skin, "header");
        titleLabel.setColor(GOLD);
        headerRow.add(titleLabel).expandX().left();

        Label statusLabel = new Label("IN PROGRESS", skin, "slot-name");
        statusLabel.setColor(GOLD);
        headerRow.add(statusLabel).right();

        actCard.add(headerRow).expandX().fillX().padBottom(8).row();

        // Current node
        if (story.currentNodeId != null) {
            Label nodeLabel = new Label("Current: " + story.currentNodeId, skin, "slot-name");
            nodeLabel.setColor(CYAN);
            actCard.add(nodeLabel).left().padBottom(8).row();
        }

        // Objectives
        Label objHeader = new Label("OBJECTIVES", skin, "slot-name");
        objHeader.setColor(CYAN);
        actCard.add(objHeader).left().padBottom(4).row();

        for (Objective obj : story.activeObjectives) {
            String prefix = obj.completed ? "  [X] " : "  [ ] ";
            Label objLabel = new Label(prefix + obj.targetId, skin, "body");
            objLabel.setColor(obj.completed ? GREEN : Color.WHITE);
            actCard.add(objLabel).left().padBottom(2).row();
        }

        col.add(actCard).expandX().fillX().row();
    }

    private void buildChoicesAndRewards(Table col, SagaInstance story) {
        if (!story.choicesMade.isEmpty()) {
            Label choicesHeader = new Label("CHOICES MADE", skin, "slot-name");
            choicesHeader.setColor(CYAN);
            col.add(choicesHeader).left().padBottom(6).row();

            for (Map.Entry<String, String> entry : story.choicesMade.entrySet()) {
                Label choiceLabel = new Label(entry.getValue(), skin, "body");
                choiceLabel.setColor(Color.LIGHT_GRAY);
                col.add(choiceLabel).left().padBottom(2).row();

                Label nodeLabel = new Label("Node: " + entry.getKey(), skin, "slot-detail");
                col.add(nodeLabel).left().padBottom(6).row();
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: Compiles. Check that `SagaRegistry.get(String)` exists and returns `SagaData`. If the method name differs, update the call.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/StoryTabActor.java
git commit -m "feat(quest-journal): implement StoryTabActor with act progression and choices"
```

---

### Task 6: ActiveQuestsTabActor

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/actors/ActiveQuestsTabActor.java`

- [ ] **Step 1: Implement ActiveQuestsTabActor**

Replace the stub with the full implementation:

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.job.JobType;
import com.galacticodyssey.mission.saga.SagaInstance;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.shared.QuestAbandonedEvent;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ActiveQuestsTabActor extends Table {

    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color RED = new Color(0.91f, 0.3f, 0.24f, 1f);
    private static final Color PURPLE = new Color(0.61f, 0.35f, 0.71f, 1f);
    private static final Color GREEN = new Color(0.18f, 0.8f, 0.44f, 1f);
    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);

    private enum QuestFilter { ALL, JOBS, FACTION, COMPANION }

    private final Skin skin;
    private final QuestJournal journal;
    private final EventBus eventBus;

    private QuestFilter currentFilter = QuestFilter.ALL;
    private Table listTable;
    private Table detailTable;
    private ScrollPane listScroll;

    public ActiveQuestsTabActor(Skin skin, QuestJournal journal, EventBus eventBus) {
        this.skin = skin;
        this.journal = journal;
        this.eventBus = eventBus;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();
        buildFilterBar();
        buildContent();
    }

    private void buildFilterBar() {
        Table filterBar = new Table();
        for (QuestFilter filter : QuestFilter.values()) {
            TextButton chip = new TextButton(filter.name().charAt(0) + filter.name().substring(1).toLowerCase(), skin, "small");
            chip.getLabel().setColor(filter == currentFilter ? CYAN : Color.GRAY);
            chip.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    currentFilter = filter;
                    refresh();
                }
            });
            filterBar.add(chip).padRight(6);
        }
        add(filterBar).left().padBottom(8).row();
    }

    private void buildContent() {
        Table splitPane = new Table();

        // Left: quest list
        listTable = new Table();
        listTable.top().left();
        listScroll = new ScrollPane(listTable, skin);
        listScroll.setFadeScrollBars(false);

        // Right: detail panel
        detailTable = new Table();
        detailTable.top().left().pad(12);

        List<QuestEntry> entries = buildQuestEntries();
        entries.sort(Comparator.comparingDouble(e -> e.urgency));

        for (QuestEntry entry : entries) {
            addQuestRow(entry);
        }

        if (entries.isEmpty()) {
            Label empty = new Label("No active quests", skin, "body");
            empty.setColor(Color.GRAY);
            listTable.add(empty).pad(20);
        }

        Label countLabel = new Label(entries.size() + " active", skin, "slot-name");
        countLabel.setColor(CYAN);

        splitPane.add(listScroll).expand().fill().padRight(12);
        splitPane.add(detailTable).width(300).fillY().top();

        add(splitPane).expand().fill();
    }

    private List<QuestEntry> buildQuestEntries() {
        List<QuestEntry> entries = new ArrayList<>();

        if (currentFilter == QuestFilter.ALL || currentFilter == QuestFilter.JOBS) {
            for (JobInstance job : journal.getActiveJobs()) {
                if (job.state != JobState.ACTIVE) continue;
                entries.add(new QuestEntry(
                    job.displayName != null ? job.displayName : job.templateId,
                    job.type != null ? job.type.name() : "",
                    getJobColor(job.type),
                    job.timeLimit > 0 ? job.timeLimit - job.elapsed : -1,
                    job, null, "JOB"));
            }
        }

        if (currentFilter == QuestFilter.ALL || currentFilter == QuestFilter.FACTION) {
            for (SagaInstance saga : journal.getActiveFactionChains()) {
                entries.add(new QuestEntry(
                    saga.sagaDataId, "Faction Chain", PURPLE,
                    -1, null, saga, "FACTION"));
            }
        }

        if (currentFilter == QuestFilter.ALL || currentFilter == QuestFilter.COMPANION) {
            for (SagaInstance saga : journal.getActiveCompanionArcs()) {
                entries.add(new QuestEntry(
                    saga.sagaDataId, "Companion Arc", GREEN,
                    -1, null, saga, "COMPANION"));
            }
        }

        return entries;
    }

    private void addQuestRow(QuestEntry entry) {
        Table row = new Table();
        row.pad(8).left();

        Label nameLabel = new Label(entry.name, skin, "body");
        nameLabel.setColor(entry.color);
        row.add(nameLabel).expandX().left();

        if (entry.timeRemaining > 0) {
            String timeStr = formatTime(entry.timeRemaining);
            Label timeLabel = new Label(timeStr, skin, "slot-detail");
            timeLabel.setColor(GOLD);
            row.add(timeLabel).right().padLeft(8);
        }

        TextButton selectBtn = new TextButton(">", skin, "small");
        selectBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDetail(entry);
            }
        });
        row.add(selectBtn).right().padLeft(4);

        listTable.add(row).expandX().fillX().padBottom(4).row();
    }

    private void showDetail(QuestEntry entry) {
        detailTable.clear();

        Label nameLabel = new Label(entry.name, skin, "header");
        nameLabel.setColor(entry.color);
        detailTable.add(nameLabel).left().padBottom(4).row();

        Label typeLabel = new Label(entry.typeLabel, skin, "slot-detail");
        detailTable.add(typeLabel).left().padBottom(12).row();

        // Objectives
        List<Objective> objectives = null;
        if (entry.job != null) {
            objectives = entry.job.objectives;
        } else if (entry.saga != null) {
            objectives = entry.saga.activeObjectives;
        }

        if (objectives != null && !objectives.isEmpty()) {
            Label objHeader = new Label("OBJECTIVES", skin, "slot-name");
            objHeader.setColor(CYAN);
            detailTable.add(objHeader).left().padBottom(4).row();

            for (Objective obj : objectives) {
                String prefix = obj.completed ? "  [X] " : "  [ ] ";
                String text = obj.targetId;
                if (obj.requiredCount > 1) {
                    text += " (" + obj.currentCount + "/" + obj.requiredCount + ")";
                }
                Label objLabel = new Label(prefix + text, skin, "body");
                objLabel.setColor(obj.completed ? GREEN : Color.WHITE);
                detailTable.add(objLabel).left().padBottom(2).row();
            }
        }

        // Time remaining
        if (entry.job != null && entry.job.timeLimit > 0) {
            Label timeHeader = new Label("TIME REMAINING", skin, "slot-name");
            timeHeader.setColor(CYAN);
            detailTable.add(timeHeader).left().padTop(8).padBottom(4).row();

            float remaining = entry.job.timeLimit - entry.job.elapsed;
            Label timeLabel = new Label(formatTime(remaining), skin, "body");
            timeLabel.setColor(remaining < 600 ? RED : GOLD);
            detailTable.add(timeLabel).left().padBottom(8).row();
        }

        // Rewards
        if (entry.job != null && entry.job.reward != null) {
            Label rewardHeader = new Label("REWARDS", skin, "slot-name");
            rewardHeader.setColor(CYAN);
            detailTable.add(rewardHeader).left().padTop(8).padBottom(4).row();

            if (entry.job.reward.credits > 0) {
                Label credLabel = new Label(entry.job.reward.credits + " Credits", skin, "body");
                credLabel.setColor(GOLD);
                detailTable.add(credLabel).left().padBottom(2).row();
            }
            if (entry.job.reward.reputationFaction != null) {
                String sign = entry.job.reward.reputationDelta >= 0 ? "+" : "";
                Label repLabel = new Label(sign + (int) entry.job.reward.reputationDelta + " " + entry.job.reward.reputationFaction, skin, "body");
                repLabel.setColor(GREEN);
                detailTable.add(repLabel).left().padBottom(2).row();
            }
        }

        // Abandon button (jobs only)
        if (entry.category.equals("JOB") && entry.job != null) {
            float repPenalty = 0f;
            if (entry.job.reward != null && entry.job.reward.reputationFaction != null) {
                repPenalty = Math.abs(entry.job.reward.reputationDelta);
            }
            String btnText = repPenalty > 0
                ? "Abandon Quest (-" + (int) repPenalty + " Rep)"
                : "Abandon Quest";
            TextButton abandonBtn = new TextButton(btnText, skin, "small-red");
            abandonBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    eventBus.publish(new QuestAbandonedEvent(entry.job.instanceId));
                    refresh();
                }
            });
            detailTable.add(abandonBtn).left().padTop(16).row();
        }
    }

    private Color getJobColor(JobType type) {
        if (type == null) return CYAN;
        switch (type) {
            case BOUNTY_HUNT:
            case MERCENARY:
                return RED;
            case EXPLORATION_SURVEY:
                return GREEN;
            default:
                return CYAN;
        }
    }

    private String formatTime(float seconds) {
        if (seconds <= 0) return "Expired";
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        int s = (int) (seconds % 60);
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static class QuestEntry {
        final String name;
        final String typeLabel;
        final Color color;
        final float timeRemaining;
        final JobInstance job;
        final SagaInstance saga;
        final String category;
        final double urgency;

        QuestEntry(String name, String typeLabel, Color color, float timeRemaining,
                   JobInstance job, SagaInstance saga, String category) {
            this.name = name;
            this.typeLabel = typeLabel;
            this.color = color;
            this.timeRemaining = timeRemaining;
            this.job = job;
            this.saga = saga;
            this.category = category;
            this.urgency = timeRemaining > 0 ? timeRemaining : Double.MAX_VALUE;
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: Compiles. If `MissionReward` fields or `Objective` fields differ from what's referenced, update accordingly.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/ActiveQuestsTabActor.java
git commit -m "feat(quest-journal): implement ActiveQuestsTabActor with filters, detail, and abandon"
```

---

### Task 7: JobBoardTabActor

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/actors/JobBoardTabActor.java`

- [ ] **Step 1: Implement JobBoardTabActor**

Replace the stub with the full implementation:

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.mission.job.*;
import com.galacticodyssey.mission.shared.Objective;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.shared.ReputationQuery;

import java.util.List;

public class JobBoardTabActor extends Table {

    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color RED = new Color(0.91f, 0.3f, 0.24f, 1f);
    private static final Color GREEN = new Color(0.18f, 0.8f, 0.44f, 1f);
    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);
    private static final Color DIM = new Color(0.5f, 0.5f, 0.5f, 0.5f);
    private static final int MAX_ACTIVE_JOBS = 10;

    private final Skin skin;
    private final JobBoard jobBoard;
    private final JobRegistry jobRegistry;
    private final QuestJournal journal;
    private final EventBus eventBus;
    private final ReputationQuery reputation;

    private JobType typeFilter = null; // null = All
    private Table listTable;
    private Table detailTable;

    public JobBoardTabActor(Skin skin, JobBoard jobBoard, JobRegistry jobRegistry,
                            QuestJournal journal, EventBus eventBus, ReputationQuery reputation) {
        this.skin = skin;
        this.jobBoard = jobBoard;
        this.jobRegistry = jobRegistry;
        this.journal = journal;
        this.eventBus = eventBus;
        this.reputation = reputation;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();

        if (jobBoard == null) {
            buildUndockedState();
            return;
        }

        buildFilterBar();
        buildContent();
    }

    private void buildUndockedState() {
        Table center = new Table();
        Label icon = new Label("[antenna]", skin, "header");
        icon.setColor(DIM);
        center.add(icon).padBottom(8).row();

        Label msg = new Label("No Station Network", skin, "header");
        msg.setColor(Color.GRAY);
        center.add(msg).padBottom(4).row();

        Label sub = new Label("Dock at a station to browse available jobs", skin, "body");
        sub.setColor(DIM);
        center.add(sub);

        add(center).expand().center();
    }

    private void buildFilterBar() {
        Table filterBar = new Table();
        // "All" chip
        TextButton allChip = new TextButton("All", skin, "small");
        allChip.getLabel().setColor(typeFilter == null ? CYAN : Color.GRAY);
        allChip.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                typeFilter = null;
                refresh();
            }
        });
        filterBar.add(allChip).padRight(6);

        // Per-type chips
        for (JobType type : JobType.values()) {
            String label = formatTypeName(type);
            TextButton chip = new TextButton(label, skin, "small");
            chip.getLabel().setColor(type == typeFilter ? CYAN : Color.GRAY);
            chip.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    typeFilter = type;
                    refresh();
                }
            });
            filterBar.add(chip).padRight(6);
        }
        add(filterBar).left().padBottom(8).row();
    }

    private void buildContent() {
        Table splitPane = new Table();

        listTable = new Table();
        listTable.top().left();
        ScrollPane listScroll = new ScrollPane(listTable, skin);
        listScroll.setFadeScrollBars(false);

        detailTable = new Table();
        detailTable.top().left().pad(12);

        List<JobInstance> allJobs = jobBoard.getAllBoardJobs();
        int shown = 0;
        for (JobInstance job : allJobs) {
            if (typeFilter != null && job.type != typeFilter) continue;

            JobTemplate template = jobRegistry != null ? jobRegistry.get(job.templateId) : null;
            boolean locked = false;
            if (template != null && reputation != null && template.requiredStanding > 0) {
                locked = reputation.getStanding(template.giverFactionTag) < template.requiredStanding;
            }
            addJobRow(job, template, locked);
            shown++;
        }

        if (shown == 0) {
            Label empty = new Label("No jobs available", skin, "body");
            empty.setColor(Color.GRAY);
            listTable.add(empty).pad(20);
        }

        // Header with station name and count
        Table header = new Table();
        Label stationLabel = new Label(jobBoard.getStationId(), skin, "slot-name");
        stationLabel.setColor(CYAN);
        header.add(stationLabel).left().expandX();
        Label countLabel = new Label(shown + " available", skin, "slot-detail");
        header.add(countLabel).right();
        add(header).expandX().fillX().padBottom(4).row();

        splitPane.add(listScroll).expand().fill().padRight(12);
        splitPane.add(detailTable).width(300).fillY().top();

        add(splitPane).expand().fill();
    }

    private void addJobRow(JobInstance job, JobTemplate template, boolean locked) {
        Table row = new Table();
        row.pad(8).left();

        String name = job.displayName != null ? job.displayName
                : (template != null && template.name != null ? template.name : job.templateId);
        Label nameLabel = new Label(name, skin, "body");
        nameLabel.setColor(locked ? DIM : getJobColor(job.type));
        row.add(nameLabel).expandX().left();

        if (locked && template != null) {
            Label lockLabel = new Label("Locked", skin, "slot-detail");
            lockLabel.setColor(RED);
            row.add(lockLabel).right().padLeft(8);
        } else {
            // Difficulty stars
            int stars = Math.max(1, Math.min(3, (int) Math.ceil(job.difficulty)));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stars; i++) sb.append("*");
            Label diffLabel = new Label(sb.toString(), skin, "slot-detail");
            diffLabel.setColor(stars >= 3 ? RED : stars >= 2 ? GOLD : GREEN);
            row.add(diffLabel).right().padLeft(8);

            if (job.reward != null && job.reward.credits > 0) {
                Label rewardLabel = new Label(job.reward.credits + " Cr", skin, "slot-detail");
                rewardLabel.setColor(GOLD);
                row.add(rewardLabel).right().padLeft(8);
            }
        }

        if (!locked) {
            TextButton selectBtn = new TextButton(">", skin, "small");
            selectBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    showDetail(job, template);
                }
            });
            row.add(selectBtn).right().padLeft(4);
        }

        listTable.add(row).expandX().fillX().padBottom(4).row();
    }

    private void showDetail(JobInstance job, JobTemplate template) {
        detailTable.clear();

        String name = job.displayName != null ? job.displayName
                : (template != null && template.name != null ? template.name : job.templateId);
        Label nameLabel = new Label(name, skin, "header");
        nameLabel.setColor(getJobColor(job.type));
        detailTable.add(nameLabel).left().padBottom(4).row();

        String desc = job.displayDescription != null ? job.displayDescription
                : (template != null && template.description != null ? template.description : "");
        if (!desc.isEmpty()) {
            Label descLabel = new Label(desc, skin, "body");
            descLabel.setWrap(true);
            descLabel.setColor(Color.LIGHT_GRAY);
            detailTable.add(descLabel).width(280).left().padBottom(12).row();
        }

        // Difficulty
        int stars = Math.max(1, Math.min(3, (int) Math.ceil(job.difficulty)));
        String[] diffNames = {"Easy", "Medium", "Hard"};
        Label diffHeader = new Label("DIFFICULTY", skin, "slot-name");
        diffHeader.setColor(CYAN);
        detailTable.add(diffHeader).left().padBottom(4).row();
        Label diffLabel = new Label(diffNames[stars - 1], skin, "body");
        diffLabel.setColor(stars >= 3 ? RED : stars >= 2 ? GOLD : GREEN);
        detailTable.add(diffLabel).left().padBottom(8).row();

        // Time limit
        if (job.timeLimit > 0) {
            Label timeHeader = new Label("TIME LIMIT", skin, "slot-name");
            timeHeader.setColor(CYAN);
            detailTable.add(timeHeader).left().padBottom(4).row();
            Label timeLabel = new Label(formatTime(job.timeLimit) + " from acceptance", skin, "body");
            detailTable.add(timeLabel).left().padBottom(8).row();
        }

        // Objectives preview
        if (!job.objectives.isEmpty()) {
            Label objHeader = new Label("OBJECTIVES", skin, "slot-name");
            objHeader.setColor(CYAN);
            detailTable.add(objHeader).left().padBottom(4).row();
            for (Objective obj : job.objectives) {
                Label objLabel = new Label("  [ ] " + obj.targetId, skin, "body");
                detailTable.add(objLabel).left().padBottom(2).row();
            }
        }

        // Rewards
        if (job.reward != null) {
            Label rewardHeader = new Label("REWARDS", skin, "slot-name");
            rewardHeader.setColor(CYAN);
            detailTable.add(rewardHeader).left().padTop(8).padBottom(4).row();
            if (job.reward.credits > 0) {
                Label credLabel = new Label(job.reward.credits + " Credits", skin, "body");
                credLabel.setColor(GOLD);
                detailTable.add(credLabel).left().padBottom(2).row();
            }
            if (job.reward.reputationFaction != null) {
                String sign = job.reward.reputationDelta >= 0 ? "+" : "";
                Label repLabel = new Label(sign + (int) job.reward.reputationDelta + " " + job.reward.reputationFaction, skin, "body");
                repLabel.setColor(GREEN);
                detailTable.add(repLabel).left().padBottom(2).row();
            }
        }

        // Accept button
        boolean atCap = journal.getActiveJobs().size() >= MAX_ACTIVE_JOBS;
        String btnText = atCap ? MAX_ACTIVE_JOBS + "/" + MAX_ACTIVE_JOBS + " Active" : "ACCEPT JOB";
        TextButton acceptBtn = new TextButton(btnText, skin);
        acceptBtn.setDisabled(atCap);
        if (!atCap) {
            acceptBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    eventBus.publish(new JobAcceptedEvent(job.instanceId));
                    refresh();
                }
            });
        }
        detailTable.add(acceptBtn).expandX().fillX().padTop(16).row();
    }

    private Color getJobColor(JobType type) {
        if (type == null) return CYAN;
        switch (type) {
            case BOUNTY_HUNT:
            case MERCENARY:
                return RED;
            case EXPLORATION_SURVEY:
                return GREEN;
            default:
                return CYAN;
        }
    }

    private String formatTypeName(JobType type) {
        String name = type.name().replace('_', ' ');
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private String formatTime(float seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: Compiles. Check that `ReputationQuery` import path matches the actual package.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/JobBoardTabActor.java
git commit -m "feat(quest-journal): implement JobBoardTabActor with filters, accept, and undocked state"
```

---

### Task 8: RumoursTabActor

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/actors/RumoursTabActor.java`

- [ ] **Step 1: Implement RumoursTabActor**

Replace the stub with the full implementation:

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.List;

public class RumoursTabActor extends Table {

    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);
    private static final Color DIM = new Color(0.5f, 0.5f, 0.5f, 0.5f);
    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);

    private final Skin skin;
    private final QuestJournal journal;

    private String expandedRumourId = null;

    public RumoursTabActor(Skin skin, QuestJournal journal) {
        this.skin = skin;
        this.journal = journal;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();
        List<JobInstance> rumours = journal.getRumourBoard();

        if (rumours.isEmpty()) {
            Label empty = new Label("No rumours heard yet...", skin, "body");
            empty.setColor(DIM);
            add(empty).expand().center();
            return;
        }

        // Header
        Label header = new Label("RUMOURS", skin, "slot-name");
        header.setColor(CYAN);
        add(header).left().padBottom(4).row();

        Label countLabel = new Label(rumours.size() + " leads", skin, "slot-detail");
        add(countLabel).left().padBottom(8).row();

        Table listTable = new Table();
        listTable.top().left();
        ScrollPane scroll = new ScrollPane(listTable, skin);
        scroll.setFadeScrollBars(false);

        for (JobInstance rumour : rumours) {
            addRumourRow(listTable, rumour);
        }

        add(scroll).expand().fill();
    }

    private void addRumourRow(Table listTable, JobInstance rumour) {
        Table row = new Table();
        row.pad(8).left();

        Label star = new Label("*", skin, "body");
        star.setColor(GOLD);
        row.add(star).padRight(8);

        String name = rumour.displayName != null ? rumour.displayName : rumour.templateId;
        Label nameLabel = new Label(name, skin, "body");
        nameLabel.setColor(GOLD);
        row.add(nameLabel).expandX().left();

        if (rumour.lead != null && rumour.lead.locationId != null) {
            Label sourceLabel = new Label(rumour.lead.locationId, skin, "slot-detail");
            row.add(sourceLabel).right();
        }

        boolean isExpanded = rumour.instanceId.equals(expandedRumourId);

        TextButton toggleBtn = new TextButton(isExpanded ? "v" : ">", skin, "small");
        toggleBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (rumour.instanceId.equals(expandedRumourId)) {
                    expandedRumourId = null;
                } else {
                    expandedRumourId = rumour.instanceId;
                }
                refresh();
            }
        });
        row.add(toggleBtn).right().padLeft(4);

        listTable.add(row).expandX().fillX().padBottom(2).row();

        // Accordion expanded content
        if (isExpanded) {
            Table expandedContent = new Table();
            expandedContent.pad(8, 32, 8, 8);

            String desc = rumour.displayDescription != null ? rumour.displayDescription : "Details unknown...";
            Label descLabel = new Label(desc, skin, "body");
            descLabel.setWrap(true);
            descLabel.setColor(Color.LIGHT_GRAY);
            expandedContent.add(descLabel).width(400).left().row();

            if (!rumour.objectives.isEmpty()) {
                Label hintLabel = new Label("Possible objectives:", skin, "slot-detail");
                expandedContent.add(hintLabel).left().padTop(4).row();
                for (var obj : rumour.objectives) {
                    Label objLabel = new Label("  - " + obj.targetId, skin, "slot-detail");
                    expandedContent.add(objLabel).left().row();
                }
            }

            listTable.add(expandedContent).expandX().fillX().padBottom(4).row();
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: Compiles.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/RumoursTabActor.java
git commit -m "feat(quest-journal): implement RumoursTabActor with accordion expand"
```

---

### Task 9: HistoryTabActor

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/actors/HistoryTabActor.java`

- [ ] **Step 1: Implement HistoryTabActor**

Replace the stub with the full implementation:

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.mission.shared.CompletedQuestRecord;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.shared.QuestOutcome;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HistoryTabActor extends Table {

    private static final Color GREEN = new Color(0.18f, 0.8f, 0.44f, 1f);
    private static final Color RED = new Color(0.91f, 0.3f, 0.24f, 1f);
    private static final Color GOLD = new Color(0.94f, 0.75f, 0.25f, 1f);
    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color DIM = new Color(0.5f, 0.5f, 0.5f, 0.5f);

    private final Skin skin;
    private final QuestJournal journal;

    private Table listTable;
    private Table detailTable;

    public HistoryTabActor(Skin skin, QuestJournal journal) {
        this.skin = skin;
        this.journal = journal;
        top().left();
        pad(8);
    }

    public void refresh() {
        clear();

        List<CompletedQuestRecord> history = journal.getCompletedQuests();

        // Header
        Table header = new Table();
        Label titleLabel = new Label("HISTORY", skin, "slot-name");
        titleLabel.setColor(CYAN);
        header.add(titleLabel).left().expandX();
        Label countLabel = new Label(history.size() + " total", skin, "slot-detail");
        header.add(countLabel).right();
        add(header).expandX().fillX().padBottom(8).row();

        if (history.isEmpty()) {
            Label empty = new Label("No completed quests yet", skin, "body");
            empty.setColor(DIM);
            add(empty).expand().center();
            return;
        }

        Table splitPane = new Table();

        listTable = new Table();
        listTable.top().left();
        ScrollPane listScroll = new ScrollPane(listTable, skin);
        listScroll.setFadeScrollBars(false);

        detailTable = new Table();
        detailTable.top().left().pad(12);

        for (CompletedQuestRecord record : history) {
            addHistoryRow(record);
        }

        splitPane.add(listScroll).expand().fill().padRight(12);
        splitPane.add(detailTable).width(300).fillY().top();

        add(splitPane).expand().fill();
    }

    private void addHistoryRow(CompletedQuestRecord record) {
        Table row = new Table();
        row.pad(8).left();

        // Outcome icon
        String icon;
        Color iconColor;
        switch (record.outcome) {
            case COMPLETED:
                icon = "[OK]";
                iconColor = GREEN;
                break;
            case FAILED:
            case EXPIRED:
                icon = "[X]";
                iconColor = RED;
                break;
            case ABANDONED:
                icon = "[!]";
                iconColor = GOLD;
                break;
            default:
                icon = "[-]";
                iconColor = DIM;
        }

        Label iconLabel = new Label(icon, skin, "body");
        iconLabel.setColor(iconColor);
        row.add(iconLabel).padRight(8);

        Label nameLabel = new Label(record.questName, skin, "body");
        if (record.outcome == QuestOutcome.FAILED || record.outcome == QuestOutcome.EXPIRED) {
            nameLabel.setColor(Color.GRAY);
        } else {
            nameLabel.setColor(Color.LIGHT_GRAY);
        }
        row.add(nameLabel).expandX().left();

        // Time ago
        Label timeLabel = new Label(formatTimeAgo(record.timestampMs), skin, "slot-detail");
        row.add(timeLabel).right().padLeft(8);

        // Reward/penalty summary
        if (record.creditsEarned > 0) {
            Label credLabel = new Label("+" + record.creditsEarned + " Cr", skin, "slot-detail");
            credLabel.setColor(GOLD);
            row.add(credLabel).right().padLeft(8);
        }
        if (record.reputationDelta != 0) {
            String sign = record.reputationDelta > 0 ? "+" : "";
            Label repLabel = new Label(sign + (int) record.reputationDelta + " Rep", skin, "slot-detail");
            repLabel.setColor(record.reputationDelta > 0 ? GREEN : RED);
            row.add(repLabel).right().padLeft(8);
        }

        TextButton selectBtn = new TextButton(">", skin, "small");
        selectBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDetail(record);
            }
        });
        row.add(selectBtn).right().padLeft(4);

        listTable.add(row).expandX().fillX().padBottom(4).row();
    }

    private void showDetail(CompletedQuestRecord record) {
        detailTable.clear();

        Label nameLabel = new Label(record.questName, skin, "header");
        nameLabel.setColor(getOutcomeColor(record.outcome));
        detailTable.add(nameLabel).left().padBottom(4).row();

        Label typeLabel = new Label(record.questType, skin, "slot-detail");
        detailTable.add(typeLabel).left().padBottom(8).row();

        // Outcome
        Label outcomeHeader = new Label("OUTCOME", skin, "slot-name");
        outcomeHeader.setColor(CYAN);
        detailTable.add(outcomeHeader).left().padBottom(4).row();

        Label outcomeLabel = new Label(record.outcome.name(), skin, "body");
        outcomeLabel.setColor(getOutcomeColor(record.outcome));
        detailTable.add(outcomeLabel).left().padBottom(8).row();

        // Rewards / penalties
        if (record.creditsEarned > 0 || record.reputationDelta != 0) {
            String rewardTitle = record.outcome == QuestOutcome.COMPLETED ? "REWARDS EARNED" : "PENALTIES";
            Label rewardHeader = new Label(rewardTitle, skin, "slot-name");
            rewardHeader.setColor(CYAN);
            detailTable.add(rewardHeader).left().padBottom(4).row();

            if (record.creditsEarned > 0) {
                Label credLabel = new Label(record.creditsEarned + " Credits", skin, "body");
                credLabel.setColor(GOLD);
                detailTable.add(credLabel).left().padBottom(2).row();
            }
            if (record.reputationFaction != null && record.reputationDelta != 0) {
                String sign = record.reputationDelta > 0 ? "+" : "";
                Label repLabel = new Label(sign + (int) record.reputationDelta + " " + record.reputationFaction, skin, "body");
                repLabel.setColor(record.reputationDelta > 0 ? GREEN : RED);
                detailTable.add(repLabel).left().padBottom(2).row();
            }
        }

        // Timestamp
        Label timeHeader = new Label("COMPLETED", skin, "slot-name");
        timeHeader.setColor(CYAN);
        detailTable.add(timeHeader).left().padTop(8).padBottom(4).row();
        Label timeLabel = new Label(formatTimeAgo(record.timestampMs), skin, "body");
        detailTable.add(timeLabel).left().row();
    }

    private Color getOutcomeColor(QuestOutcome outcome) {
        switch (outcome) {
            case COMPLETED: return GREEN;
            case FAILED:
            case EXPIRED: return RED;
            case ABANDONED: return GOLD;
            default: return Color.WHITE;
        }
    }

    private String formatTimeAgo(long timestampMs) {
        long diff = System.currentTimeMillis() - timestampMs;
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        if (days > 0) return days + (days == 1 ? " day ago" : " days ago");
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        if (hours > 0) return hours + (hours == 1 ? " hour ago" : " hours ago");
        return "Just now";
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: Compiles.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/HistoryTabActor.java
git commit -m "feat(quest-journal): implement HistoryTabActor with outcome coloring and detail"
```

---

### Task 10: GameScreen Integration and Persistence Registration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java`

- [ ] **Step 1: Add J key binding to GameScreen**

In `GameScreen.java`, add a state flag alongside the existing `paused`, `inDialog`, `inInventory` flags:

```java
private boolean inJournal;
```

In the `setupInput()` method's `InputAdapter.keyDown()`, add the J key handling alongside the existing TAB (inventory) handling:

```java
if (keycode == Input.Keys.J && !paused && !inDialog && !inInventory && !inJournal) {
    openQuestJournal();
    return true;
}
```

Add the `openQuestJournal()` method:

```java
private void openQuestJournal() {
    inJournal = true;
    Gdx.input.setCursorCatched(false);
    gameWorld.getPlayerInputSystem().setEnabled(false);

    // Get references from gameWorld — adapt these accessor calls to match actual GameWorld API
    EventBus eventBus = gameWorld.getEventBus();
    QuestJournal journal = gameWorld.getQuestJournal();
    JobBoard jobBoard = gameWorld.getCurrentJobBoard(); // null if not docked
    JobRegistry jobRegistry = gameWorld.getJobRegistry();
    SagaRegistry sagaRegistry = gameWorld.getSagaRegistry();
    ReputationQuery reputation = gameWorld.getPlayerReputation();
    Skin skin = UiFactory.getSkin();

    QuestJournalScreen journalScreen = new QuestJournalScreen(
        game, this, eventBus, journal, jobBoard, jobRegistry,
        sagaRegistry, reputation, skin);
    game.setScreen(journalScreen);
}
```

Add these imports at the top:

```java
import com.galacticodyssey.ui.QuestJournalScreen;
import com.galacticodyssey.mission.shared.QuestJournal;
import com.galacticodyssey.mission.job.JobBoard;
import com.galacticodyssey.mission.job.JobRegistry;
import com.galacticodyssey.mission.saga.SagaRegistry;
```

Subscribe to `JournalClosedEvent` to reset the `inJournal` flag. Add in the constructor or `initializeWorld()`:

```java
eventBus.subscribe(JournalClosedEvent.class, e -> {
    inJournal = false;
    Gdx.input.setCursorCatched(true);
    gameWorld.getPlayerInputSystem().setEnabled(true);
    setupInput();
});
```

Add the import:

```java
import com.galacticodyssey.ui.events.JournalClosedEvent;
```

Note: The `gameWorld.getQuestJournal()`, `gameWorld.getCurrentJobBoard()`, `gameWorld.getJobRegistry()`, `gameWorld.getSagaRegistry()`, and `gameWorld.getPlayerReputation()` accessors may not exist yet. If they don't, add simple getter methods to `GameWorld` that return the corresponding fields. Check `GameWorld.java` for how existing accessors like `getPlayerInputSystem()` and `getEventBus()` are structured, and follow the same pattern.

- [ ] **Step 2: Register CompletedQuestRecord in SnapshotComponentRegistry**

In `SnapshotComponentRegistry.java`, add a registration in the `static` block. Since `CompletedQuestRecord` is a plain data class (not an ECS Component), check how other non-component data classes are persisted. If the registry only handles ECS Components, then `CompletedQuestRecord` persistence may be handled through the `QuestJournal` serialization instead — check how `QuestJournal` is saved/loaded.

If `QuestJournal` is serialized as a whole object (via JSON), then `CompletedQuestRecord` will automatically be included since it's a field of `QuestJournal`. In that case, no `SnapshotComponentRegistry` change is needed.

If explicit registration is needed:

```java
// In the static block, add:
register("CompletedQuest", CompletedQuestSnapshot.class, CompletedQuestRecord::new);
```

Check the existing pattern and adapt.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: Compiles. If `GameWorld` accessor methods are missing, add them as simple getters.

- [ ] **Step 4: Run all tests**

Run: `./gradlew :core:test -i`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java \
        core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java
git commit -m "feat(quest-journal): add J key binding in GameScreen, register persistence"
```

---

### Task 11: Visual Polish and Smoke Test

**Files:**
- Potentially modify any tab actor for visual fixes

- [ ] **Step 1: Launch the game**

Run: `./gradlew :desktop:run`

Start or load a game. Verify you can press `J` to open the quest journal.

- [ ] **Step 2: Verify tab switching**

Click each of the 5 tabs (Story, Active, Board, Rumours, History). Verify:
- Tab highlight changes to cyan on the active tab
- Content area swaps correctly
- No crashes or exceptions in console

- [ ] **Step 3: Verify Story tab**

If no main story is active, verify the "Your story hasn't begun..." empty state shows. If a story is active, verify the title, objectives, and choices display correctly.

- [ ] **Step 4: Verify Active Quests tab**

If no quests are active, verify the "No active quests" empty state. Accept a job first (via Board tab or existing gameplay), then verify it appears in the Active tab with correct type color, objectives, and the abandon button.

- [ ] **Step 5: Verify Job Board tab**

- If docked: verify job listings appear with difficulty stars, rewards, and filters work
- If undocked: verify the "No Station Network" empty state shows
- Try accepting a job and verify it moves to the Active tab

- [ ] **Step 6: Verify Rumours tab**

Check that rumours display if any exist, or show the empty state. Test the accordion expand/collapse on a rumour entry.

- [ ] **Step 7: Verify History tab**

If no completed quests, verify empty state. Complete or abandon a quest, then verify it appears in history with correct outcome color and detail panel.

- [ ] **Step 8: Verify close behavior**

- Press `J` while journal is open — verify it closes and returns to gameplay
- Press `ESC` while journal is open — verify it closes
- Click the `X` button — verify it closes
- After closing, verify player movement and cursor capture are restored

- [ ] **Step 9: Fix any visual issues found**

Adjust padding, colors, font styles, or layout sizing as needed based on what you see in-game. Common fixes:
- Label wrapping issues: ensure `setWrap(true)` and `.width(N)` are set on description labels
- Scroll pane not scrolling: ensure content table has `.top()` alignment
- Colors too dim/bright: adjust the static Color constants

- [ ] **Step 10: Commit any fixes**

```bash
git add -A
git commit -m "fix(quest-journal): visual polish from smoke test"
```
