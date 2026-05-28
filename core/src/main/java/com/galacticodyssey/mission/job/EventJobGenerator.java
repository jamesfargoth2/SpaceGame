package com.galacticodyssey.mission.job;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.mission.discovery.DiscoveryLead;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class EventJobGenerator {

    private final JobRegistry registry;
    private final ProceduralJobGenerator generator;
    private final List<JobInstance> spawnedJobs = new ArrayList<>();
    private Consumer<JobInstance> jobListener = j -> {};

    public EventJobGenerator(EventBus eventBus, JobRegistry registry, ProceduralJobGenerator generator) {
        this.registry = registry;
        this.generator = generator;
        eventBus.subscribe(FactionWarStartedEvent.class, this::onFactionWarStarted);
        eventBus.subscribe(FactionWarEndedEvent.class, this::onFactionWarEnded);
        eventBus.subscribe(ShipMissingEvent.class, this::onShipMissing);
        eventBus.subscribe(AnomalyDetectedEvent.class, this::onAnomalyDetected);
        eventBus.subscribe(CargoShipAttackedEvent.class, this::onCargoShipAttacked);
    }

    public void setJobListener(Consumer<JobInstance> listener) {
        this.jobListener = listener;
    }

    private void onFactionWarStarted(FactionWarStartedEvent e) {
        JobInstance job = spawnEventJob(JobType.MERCENARY, e.sectorId, e.warId);
        if (job != null) notifySpawned(job);
    }

    private void onFactionWarEnded(FactionWarEndedEvent e) {
        for (JobInstance job : spawnedJobs) {
            if (e.warId.equals(job.triggeringEventId)
                    && (job.state == JobState.RUMOURED || job.state == JobState.AVAILABLE)) {
                job.state = JobState.EXPIRED;
            }
        }
    }

    private void onShipMissing(ShipMissingEvent e) {
        JobInstance job = spawnEventJob(JobType.SALVAGE, e.lastKnownLocationId, e.shipId);
        if (job != null) notifySpawned(job);
    }

    private void onAnomalyDetected(AnomalyDetectedEvent e) {
        JobInstance job = spawnEventJob(JobType.EXPLORATION_SURVEY, e.locationId, e.anomalyId);
        if (job != null) notifySpawned(job);
    }

    private void onCargoShipAttacked(CargoShipAttackedEvent e) {
        JobInstance job = spawnEventJob(JobType.BOUNTY_HUNT, e.locationId, e.attackerId);
        if (job != null) notifySpawned(job);
    }

    private JobInstance spawnEventJob(JobType type, String locationId, String triggeringEventId) {
        JobTemplate template = findTemplateByType(type);
        if (template == null) return null;

        SectorContext sector = new SectorContext();
        sector.sectorId = locationId != null ? locationId : "unknown";
        sector.locationIds = locationId != null ? List.of(locationId) : List.of();
        sector.npcIds = List.of();
        sector.factionTags = List.of();

        JobInstance job = generator.generate(template, sector, 1.0f, faction -> 0f);
        job.state = JobState.RUMOURED;
        job.triggeringEventId = triggeringEventId;

        DiscoveryLead lead = new DiscoveryLead();
        lead.jobInstanceId = job.instanceId;
        lead.locationId = sector.sectorId;
        lead.rumourNpcIds = List.of();
        lead.triggeringEventId = triggeringEventId;
        job.lead = lead;

        return job;
    }

    private void notifySpawned(JobInstance job) {
        spawnedJobs.add(job);
        jobListener.accept(job);
    }

    private JobTemplate findTemplateByType(JobType type) {
        for (JobTemplate t : registry.getAll()) {
            if (t.type == type) return t;
        }
        return null;
    }
}
