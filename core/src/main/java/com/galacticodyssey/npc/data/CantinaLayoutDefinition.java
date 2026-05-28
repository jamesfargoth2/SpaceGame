package com.galacticodyssey.npc.data;

import java.util.ArrayList;
import java.util.List;

public class CantinaLayoutDefinition {
    public String backgroundKey;
    public int capacity;
    public final List<CantinaSeatDefinition> seats = new ArrayList<>();
    public float hiringBoardX;
    public float hiringBoardY;
}
