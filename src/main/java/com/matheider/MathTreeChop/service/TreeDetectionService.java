package com.matheider.MathTreeChop.service;

import com.matheider.MathTreeChop.MathTreeChopPlugin;
import com.matheider.MathTreeChop.config.ConfigManager;
import com.matheider.MathTreeChop.model.BlockKey;
import com.matheider.MathTreeChop.model.TreeBlockKind;
import com.matheider.MathTreeChop.model.TreeBlockSnapshot;
import com.matheider.MathTreeChop.model.TreeStructure;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class TreeDetectionService {

    private static final BlockFace[] CARDINAL_FACES = {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN
    };

    private final MathTreeChopPlugin plugin;
    private final Map<BlockKey, CachedTree> cache = new HashMap<>();

    public TreeDetectionService(MathTreeChopPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<TreeStructure> detect(Block start, Player player) {
        ConfigManager.PluginSettings settings = this.plugin.configManager().settings();
        if (!settings.general().enabled()) {
            return Optional.empty();
        }
        if (!settings.general().allowCreative() && player.getGameMode().name().equals("CREATIVE")) {
            return Optional.empty();
        }
        if (settings.general().disableWhileSneaking() && player.isSneaking()) {
            return Optional.empty();
        }
        if (!player.hasPermission(settings.permissions().use())) {
            return Optional.empty();
        }
        if (!isAllowedTool(player.getInventory().getItemInMainHand(), player)) {
            return Optional.empty();
        }
        if (!isLog(start.getType())) {
            return Optional.empty();
        }
        BlockKey key = BlockKey.of(start);
        long now = this.plugin.getServer().getCurrentTick();
        CachedTree cachedTree = this.cache.get(key);
        if (cachedTree != null && cachedTree.expiresAt >= now) {
            return Optional.of(cachedTree.structure);
        }
        TreeStructure structure = buildTree(start, settings.treeDetection(), settings.miningSlowdown());
        if (structure == null) {
            return Optional.empty();
        }
        long expiresAt = now + settings.general().cacheTtlTicks();
        CachedTree newCache = new CachedTree(structure, expiresAt);
        for (BlockKey structureKey : structure.keys()) {
            this.cache.put(structureKey, newCache);
        }
        return Optional.of(structure);
    }

    public void invalidate(TreeStructure structure) {
        for (BlockKey key : structure.keys()) {
            this.cache.remove(key);
        }
    }

    public void clearCache() {
        this.cache.clear();
    }

    public boolean isAllowedTool(ItemStack item, Player player) {
        ConfigManager.ToolSettings toolSettings = this.plugin.configManager().settings().tools();
        if (!toolSettings.requireTool() || player.hasPermission(this.plugin.configManager().settings().permissions().bypassTool())) {
            return true;
        }
        return item != null && toolSettings.allowedTools().contains(item.getType());
    }

    public boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material) || material.name().endsWith("_WOOD") || material.name().endsWith("_HYPHAE") || material.name().endsWith("_STEM");
    }

    public boolean isLeaf(Block block) {
        Material material = block.getType();
        if (!Tag.LEAVES.isTagged(material)) {
            return false;
        }
        if (!this.plugin.configManager().settings().treeDetection().ignorePersistentLeaves()) {
            return true;
        }
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Leaves leaves) {
            return !leaves.isPersistent();
        }
        return true;
    }

    private TreeStructure buildTree(Block start, ConfigManager.TreeDetectionSettings detectionSettings, ConfigManager.MiningSlowdownSettings slowdownSettings) {
        Set<BlockKey> logs = gatherLogs(start, detectionSettings);
        if (logs.size() < detectionSettings.minLogs()) {
            return null;
        }
        Set<BlockKey> leaves = gatherLeaves(start, logs, detectionSettings);
        if (detectionSettings.requireLeaves() && leaves.size() < detectionSettings.minLeaves()) {
            return null;
        }
        Set<BlockKey> extras = gatherExtras(start, logs, leaves, detectionSettings.extraBlocks());
        List<TreeBlockSnapshot> blocks = new ArrayList<>(logs.size() + leaves.size() + extras.size());
        Set<BlockKey> allKeys = new HashSet<>();
        fillSnapshots(start, logs, TreeBlockKind.LOG, blocks, allKeys);
        fillSnapshots(start, leaves, TreeBlockKind.LEAF, blocks, allKeys);
        fillSnapshots(start, extras, TreeBlockKind.EXTRA, blocks, allKeys);
        int slowdownCount = 0;
        if (slowdownSettings.countLogs()) {
            slowdownCount += logs.size();
        }
        if (slowdownSettings.countLeaves()) {
            slowdownCount += leaves.size();
        }
        return new TreeStructure(start.getWorld(), BlockKey.of(start), List.copyOf(blocks), Set.copyOf(allKeys), logs.size(), leaves.size(), extras.size(), slowdownCount);
    }

    private void fillSnapshots(Block start, Set<BlockKey> keys, TreeBlockKind kind, List<TreeBlockSnapshot> blocks, Set<BlockKey> allKeys) {
        for (BlockKey key : keys) {
            Block block = start.getWorld().getBlockAt(key.x(), key.y(), key.z());
            Vector relative = new Vector(
                block.getX() - start.getX(),
                block.getY() - start.getY(),
                block.getZ() - start.getZ()
            );
            blocks.add(new TreeBlockSnapshot(key, relative, block.getBlockData().clone(), kind));
            allKeys.add(key);
        }
    }

    private Set<BlockKey> gatherLogs(Block start, ConfigManager.TreeDetectionSettings detectionSettings) {
        Set<BlockKey> logs = new HashSet<>();
        Set<BlockKey> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(start);
        int startY = start.getY();
        while (!queue.isEmpty()) {
            Block current = queue.poll();
            BlockKey currentKey = BlockKey.of(current);
            if (!visited.add(currentKey)) {
                continue;
            }
            if (current.getY() < startY || !isLog(current.getType())) {
                continue;
            }
            logs.add(currentKey);
            if (logs.size() > detectionSettings.maxLogs()) {
                return Set.of();
            }
            for (int x = -detectionSettings.searchRadiusHorizontal(); x <= detectionSettings.searchRadiusHorizontal(); x++) {
                for (int y = 0; y <= detectionSettings.searchRadiusUpward(); y++) {
                    for (int z = -detectionSettings.searchRadiusHorizontal(); z <= detectionSettings.searchRadiusHorizontal(); z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        Block next = current.getRelative(x, y, z);
                        if (next.getY() >= startY) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return logs;
    }

    private Set<BlockKey> gatherLeaves(Block start, Set<BlockKey> logs, ConfigManager.TreeDetectionSettings detectionSettings) {
        Set<BlockKey> leaves = new HashSet<>();
        Set<BlockKey> visited = new HashSet<>();
        Queue<SearchNode> queue = new ArrayDeque<>();
        for (BlockKey key : logs) {
            Block log = start.getWorld().getBlockAt(key.x(), key.y(), key.z());
            for (BlockFace face : CARDINAL_FACES) {
                queue.add(new SearchNode(log.getRelative(face), 1));
            }
        }
        while (!queue.isEmpty()) {
            SearchNode node = queue.poll();
            Block block = node.block;
            BlockKey key = BlockKey.of(block);
            if (!visited.add(key) || node.distance > detectionSettings.leafSearchRadius()) {
                continue;
            }
            if (!isLeaf(block)) {
                continue;
            }
            leaves.add(key);
            for (BlockFace face : CARDINAL_FACES) {
                queue.add(new SearchNode(block.getRelative(face), node.distance + 1));
            }
        }
        return leaves;
    }

    private Set<BlockKey> gatherExtras(Block start, Set<BlockKey> logs, Set<BlockKey> leaves, ConfigManager.ExtraBlockSettings extraBlockSettings) {
        Set<BlockKey> extras = new HashSet<>();
        Set<BlockKey> treeKeys = new HashSet<>(logs);
        treeKeys.addAll(leaves);
        for (BlockKey key : treeKeys) {
            Block block = start.getWorld().getBlockAt(key.x(), key.y(), key.z());
            for (BlockFace face : CARDINAL_FACES) {
                Block adjacent = block.getRelative(face);
                Material type = adjacent.getType();
                if (extraBlockSettings.includeVines() && type == Material.VINE) {
                    gatherVerticalExtras(adjacent, Material.VINE, extras);
                } else if (extraBlockSettings.includeCocoa() && type == Material.COCOA) {
                    extras.add(BlockKey.of(adjacent));
                } else if (extraBlockSettings.includeBeeNests() && type == Material.BEE_NEST) {
                    extras.add(BlockKey.of(adjacent));
                }
            }
        }
        extras.removeAll(logs);
        extras.removeAll(leaves);
        return extras;
    }

    private void gatherVerticalExtras(Block block, Material material, Set<BlockKey> extras) {
        Block current = block;
        while (current.getType() == material) {
            extras.add(BlockKey.of(current));
            current = current.getRelative(BlockFace.DOWN);
        }
    }

    private record SearchNode(Block block, int distance) {
    }

    private record CachedTree(TreeStructure structure, long expiresAt) {
    }
}
