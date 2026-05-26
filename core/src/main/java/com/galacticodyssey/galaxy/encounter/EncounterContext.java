package com.galacticodyssey.galaxy.encounter;

/**
 * Input context for building a weighted encounter table. Describes the
 * conditions under which encounters are generated.
 */
public final class EncounterContext {

    private final long systemId;
    private final String owningFactionId;
    private final float securityLevel;
    private final float distanceFromCoreLY;
    private final float playerReputation;
    private final boolean isContested;
    private final boolean isAtWar;

    public EncounterContext(long systemId, String owningFactionId,
                            float securityLevel, float distanceFromCoreLY,
                            float playerReputation,
                            boolean isContested, boolean isAtWar) {
        this.systemId = systemId;
        this.owningFactionId = owningFactionId;
        this.securityLevel = securityLevel;
        this.distanceFromCoreLY = distanceFromCoreLY;
        this.playerReputation = playerReputation;
        this.isContested = isContested;
        this.isAtWar = isAtWar;
    }

    public long getSystemId() { return systemId; }
    public String getOwningFactionId() { return owningFactionId; }
    public float getSecurityLevel() { return securityLevel; }
    public float getDistanceFromCoreLY() { return distanceFromCoreLY; }
    public float getPlayerReputation() { return playerReputation; }
    public boolean isContested() { return isContested; }
    public boolean isAtWar() { return isAtWar; }
}
