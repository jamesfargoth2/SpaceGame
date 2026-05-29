package com.galacticodyssey.fauna.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureDriveSystemTest {

    @Test
    void hungerIncreasesOverTime() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.hunger = 0.5f;
        drives.hungerRate = 0.01f;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        assertTrue(drives.hunger > 0.5f);
    }

    @Test
    void energyDecreasesWhenMoving() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.energy = 0.8f;
        drives.moving = true;
        drives.sprinting = false;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        assertTrue(drives.energy < 0.8f);
    }

    @Test
    void energyRecoversDuringIdle() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.energy = 0.5f;
        drives.moving = false;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        assertTrue(drives.energy > 0.5f);
    }

    @Test
    void fearDecaysOverTime() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.fear = 0.8f;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        assertTrue(drives.fear < 0.8f);
    }

    @Test
    void allDrivesClampToZeroOne() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.hunger = 1.5f;
        drives.energy = -0.5f;
        drives.fear = 2f;
        CreatureDriveSystem.tickDrives(drives, 0f, false);
        assertTrue(drives.hunger <= 1f);
        assertTrue(drives.energy >= 0f);
        assertTrue(drives.fear <= 1f);
    }

    @Test
    void lowActivityReducesHungerRate() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.hunger = 0.1f;
        drives.hungerRate = 0.1f;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        float activeHunger = drives.hunger;

        drives.hunger = 0.1f;
        CreatureDriveSystem.tickDrives(drives, 1f, true);
        float inactiveHunger = drives.hunger;

        assertTrue(inactiveHunger < activeHunger);
    }
}
