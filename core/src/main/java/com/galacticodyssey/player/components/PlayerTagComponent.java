package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

/**
 * Marker component that identifies the local player entity.
 * Systems that should only process the player (e.g. input systems)
 * include this in their {@code Family} filter.
 */
public class PlayerTagComponent implements Component {
}
