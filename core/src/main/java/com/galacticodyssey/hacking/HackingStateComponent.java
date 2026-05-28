package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class HackingStateComponent implements Component {
    public Entity currentTarget = null;
    public Object controller = null;  // HackingController (Task 8) — placeholder for now
    public boolean isRemoteHack = false;
}
