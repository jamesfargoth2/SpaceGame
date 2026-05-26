package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyRegionClassifierTest {

    private GalaxyRegionClassifier classifier;
    private GalaxyConfig config;

    @BeforeEach
    void setUp() {
        classifier = new GalaxyRegionClassifier();
        config = GalaxyConfig.defaults();
    }

    @Test
    void centreIsCore() {
        assertEquals(GalaxyRegion.CORE, classifier.classify(0, 0, config));
    }

    @Test
    void innerRadiusIsInnerRim() {
        double x = config.radiusLY * 0.2;
        assertEquals(GalaxyRegion.INNER_RIM, classifier.classify(x, 0, config));
    }

    @Test
    void outerRadiusIsOuterRim() {
        double x = config.radiusLY * 0.6;
        assertEquals(GalaxyRegion.OUTER_RIM, classifier.classify(x, 0, config));
    }

    @Test
    void beyondDiskIsVoid() {
        double x = config.radiusLY * 1.5;
        assertEquals(GalaxyRegion.VOID, classifier.classify(x, 0, config));
    }

    @Test
    void classificationUsesRadialDistance() {
        double r = config.radiusLY * 0.2;
        double x = r * Math.cos(Math.PI / 4);
        double y = r * Math.sin(Math.PI / 4);
        assertEquals(GalaxyRegion.INNER_RIM, classifier.classify(x, y, config));
    }
}
