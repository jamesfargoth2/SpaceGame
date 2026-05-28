package com.galacticodyssey.shipbuilder;

public class BlueprintData {
    public enum BlueprintType { ROOM, MODULE, APPENDAGE }
    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC }

    public String blueprintId;
    public BlueprintType type;
    public String unlocks;
    public Rarity rarity;
    public int shopPrice;
    public String description;

    public BlueprintData() {}
}
