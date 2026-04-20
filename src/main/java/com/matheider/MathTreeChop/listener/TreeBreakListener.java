package com.matheider.MathTreeChop.listener;

import com.matheider.MathTreeChop.MathTreeChopPlugin;
import com.matheider.MathTreeChop.model.TreeStructure;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TreeBreakListener implements Listener {

    private final MathTreeChopPlugin plugin;

    public TreeBreakListener(MathTreeChopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Optional<TreeStructure> structure = this.plugin.treeDetectionService().detect(event.getBlock(), player);
        if (structure.isEmpty()) {
            return;
        }
        TreeStructure tree = structure.get();
        List<ItemStack> drops = collectDrops(tree, player);
        event.setCancelled(true);
        removeBlocks(tree);
        applyDurabilityDamage(player, tree);
        this.plugin.treeDetectionService().invalidate(tree);
        this.plugin.fallingAnimationService().spawn(tree, player, drops);
    }

    private List<ItemStack> collectDrops(TreeStructure structure, Player player) {
        List<ItemStack> drops = new ArrayList<>();
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (var snapshot : structure.blocks()) {
            Block block = structure.world().getBlockAt(snapshot.key().x(), snapshot.key().y(), snapshot.key().z());
            drops.addAll(block.getDrops(tool, player));
        }
        return drops;
    }

    private void removeBlocks(TreeStructure structure) {
        for (var snapshot : structure.blocks()) {
            Block block = structure.world().getBlockAt(snapshot.key().x(), snapshot.key().y(), snapshot.key().z());
            block.setType(Material.AIR, false);
        }
    }

    private void applyDurabilityDamage(Player player, TreeStructure structure) {
        var settings = this.plugin.configManager().settings();
        if (!settings.tools().durabilityDamageEnabled() || player.getGameMode() == GameMode.CREATIVE || player.hasPermission(settings.permissions().bypassDurability())) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR || item.getType().getMaxDurability() <= 0) {
            return;
        }
        int damage = settings.tools().countOnlyLogsForDamage() ? structure.logCount() : structure.totalBlocks();
        if (damage <= 0) {
            return;
        }
        ItemStack damaged = item.damage(damage, player);
        player.getInventory().setItemInMainHand(damaged);
    }
}
