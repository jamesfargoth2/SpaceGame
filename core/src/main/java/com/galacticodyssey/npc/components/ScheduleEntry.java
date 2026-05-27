package com.galacticodyssey.npc.components;

public class ScheduleEntry {
    public float hourOfDay;
    public String locationId;
    public String activity;

    public ScheduleEntry() {}

    public ScheduleEntry(float hourOfDay, String locationId, String activity) {
        this.hourOfDay = hourOfDay;
        this.locationId = locationId;
        this.activity = activity;
    }
}
