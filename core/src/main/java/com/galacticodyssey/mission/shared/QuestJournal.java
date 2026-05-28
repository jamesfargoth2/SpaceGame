package com.galacticodyssey.mission.shared;

import com.galacticodyssey.mission.job.JobInstance;
import com.galacticodyssey.mission.saga.SagaInstance;

import java.util.ArrayList;
import java.util.List;

public class QuestJournal {

    private static final int JOB_CAP = 10;

    private SagaInstance activeMainStory;
    private final List<CompletedQuestRecord> completedQuests = new ArrayList<>();
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

    public void addCompleted(CompletedQuestRecord record) {
        completedQuests.add(0, record);
    }

    public List<CompletedQuestRecord> getCompletedQuests() {
        return completedQuests;
    }
}
