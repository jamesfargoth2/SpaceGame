package com.galacticodyssey.fauna.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GaitControllerFactoryTest {

    @Test
    void walkGaitClassReturnsWalkController() {
        GaitController ctrl = GaitControllerFactory.create("walk");
        assertInstanceOf(WalkGaitController.class, ctrl);
    }

    @Test
    void skitterGaitClassReturnsSkitterController() {
        GaitController ctrl = GaitControllerFactory.create("skitter");
        assertInstanceOf(SkitterGaitController.class, ctrl);
    }

    @Test
    void slitherGaitClassReturnsSlitherController() {
        GaitController ctrl = GaitControllerFactory.create("slither");
        assertInstanceOf(SlitherGaitController.class, ctrl);
    }

    @Test
    void unknownGaitClassDefaultsToWalk() {
        GaitController ctrl = GaitControllerFactory.create("gallop");
        assertInstanceOf(WalkGaitController.class, ctrl);
    }
}
