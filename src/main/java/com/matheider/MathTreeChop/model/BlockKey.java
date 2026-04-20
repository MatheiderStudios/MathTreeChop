package com.matheider.MathTreeChop.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public record BlockKey(UUID worldId, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public Location toLocation(World world) {
        return new Location(world, this.x, this.y, this.z);
    }
}
