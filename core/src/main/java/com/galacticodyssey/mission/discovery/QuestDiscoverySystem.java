package com.galacticodyssey.mission.discovery;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.LocationEnteredEvent;
import com.galacticodyssey.core.events.NpcDialogueEvent;
import com.galacticodyssey.core.events.ScanCompleteEvent;
import com.galacticodyssey.mission.events.QuestDiscoveredEvent;
import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.job.JobState;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.ArrayList;

public class QuestDiscoverySystem extends EntitySystem {

    private final EventBus eventBus;
    private final QuestJournal journal;

    public QuestDiscoverySystem(EventBus eventBus, QuestJournal journal) {
        this.eventBus = eventBus;
        this.journal = journal;
        subscribeAll();
    }

    public void registerLead(JobInstance job, DiscoveryLead lead) {
        // Activation logic reads directly from journal.getRumourBoard(); no registration needed.
    }

    private void subscribeAll() {
        eventBus.subscribe(NpcDialogueEvent.class, e -> {
            if (!"RUMOUR".equals(e.topic)) return;
            for (JobInstance job : new ArrayList<>(journal.getRumourBoard())) {
                if (job.lead == null) continue;
                if (job.lead.rumourNpcIds.contains(e.npcId)) {
                    job.lead.rumourHeard = true;
                    activateJob(job);
                    return;
                }
            }
        });
        eventBus.subscribe(LocationEnteredEvent.class, e -> activateByLocation(e.locationId));
        eventBus.subscribe(ScanCompleteEvent.class, e -> activateByLocation(e.targetId));
    }

    private void activateByLocation(String locationId) {
        for (JobInstance job : new ArrayList<>(journal.getRumourBoard())) {
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
