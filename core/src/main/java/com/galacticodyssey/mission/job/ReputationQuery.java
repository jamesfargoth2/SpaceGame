package com.galacticodyssey.mission.job;

@FunctionalInterface
public interface ReputationQuery {
    float getStanding(String factionTag);
}
