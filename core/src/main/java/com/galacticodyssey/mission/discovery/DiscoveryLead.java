package com.galacticodyssey.mission.discovery;

import java.util.List;

public class DiscoveryLead {
    public String jobInstanceId;
    public List<String> rumourNpcIds;
    public String locationId;
    public boolean rumourHeard;
    public boolean locationDiscovered;
    public String triggeringEventId;

    public boolean isActivated() { return rumourHeard || locationDiscovered; }
}
