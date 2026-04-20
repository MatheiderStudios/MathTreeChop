package com.matheider.MathTreeChop.model;

import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

public record TreeBlockSnapshot(BlockKey key, Vector relative, BlockData blockData, TreeBlockKind kind) {
}
