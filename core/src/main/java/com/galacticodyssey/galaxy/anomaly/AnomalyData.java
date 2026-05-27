package com.galacticodyssey.galaxy.anomaly;

/**
 * Data describing a placed space anomaly in the galaxy.
 */
public final class AnomalyData {

    public final AnomalyType type;
    public final long id;
    public final double posX;
    public final double posY;
    public final double posZ;
    public final float coreRadiusLY;
    public final float hazardRadiusLY;
    public final float discoveryDifficulty;
    public final boolean isCharted;
    public final long seed;
    public final AnomalyEffects effects;
    public final long partnerAnomalyId;

    public AnomalyData(AnomalyType type, long id, double posX, double posY, double posZ,
                       float coreRadiusLY, float hazardRadiusLY, float discoveryDifficulty,
                       boolean isCharted, long seed, AnomalyEffects effects, long partnerAnomalyId) {
        this.type = type;
        this.id = id;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.coreRadiusLY = coreRadiusLY;
        this.hazardRadiusLY = hazardRadiusLY;
        this.discoveryDifficulty = discoveryDifficulty;
        this.isCharted = isCharted;
        this.seed = seed;
        this.effects = effects;
        this.partnerAnomalyId = partnerAnomalyId;
    }
}
