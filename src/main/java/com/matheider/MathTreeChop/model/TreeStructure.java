package com.matheider.MathTreeChop.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Set;

public record TreeStructure(
    World world,
    BlockKey origin,
    List<TreeBlockSnapshot> blocks,
    Set<BlockKey> keys,
    int logCount,
    int leafCount,
    int extraCount,
    int slowdownCount
) {

    public Location originLocation() {
        return this.origin.toLocation(this.world);
    }

    public int totalBlocks() {
        return this.blocks.size();
    }
}
