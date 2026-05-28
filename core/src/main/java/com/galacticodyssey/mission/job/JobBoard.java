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
    private float timeSinceRefresh = REFRESH_INTERVAL;

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
