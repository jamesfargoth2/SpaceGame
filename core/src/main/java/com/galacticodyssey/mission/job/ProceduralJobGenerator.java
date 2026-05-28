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
            case ESCORT_TARGET -> sector.locationIds.size() > 1 ? sector.locationIds.get(1) : sector.locationIds.isEmpty() ? "unknown" : sector.locationIds.get(0);
            default -> sector.sectorId + "_target";
        };
    }
}
