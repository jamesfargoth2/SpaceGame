package com.galacticodyssey.mission.job;

public final class JobAcceptedEvent {
    public final String jobInstanceId;

    public JobAcceptedEvent(String jobInstanceId) {
        this.jobInstanceId = jobInstanceId;
    }
}
