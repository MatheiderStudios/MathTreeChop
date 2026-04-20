package com.matheider.MathTreeChop.service;

import com.matheider.MathTreeChop.MathTreeChopPlugin;
import com.matheider.MathTreeChop.config.ConfigManager;
import com.matheider.MathTreeChop.model.TreeStructure;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
public final class MiningSlowdownService {

    private final MathTreeChopPlugin plugin;
    private final Map<UUID, Double> appliedValues = new HashMap<>();
    private final NamespacedKey modifierKey;
    private BukkitTask task;

    public MiningSlowdownService(MathTreeChopPlugin plugin) {
        this.plugin = plugin;
        this.modifierKey = new NamespacedKey(plugin, "mining-slowdown");
    }

    public void start() {
        reload();
    }

    public void reload() {
        shutdownTask();
        removeAll();
        ConfigManager.MiningSlowdownSettings settings = this.plugin.configManager().settings().miningSlowdown();
        if (!settings.enabled()) {
            return;
        }
        this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 1L, settings.refreshTicks());
    }

    public void shutdown() {
        shutdownTask();
        removeAll();
    }

    private void tick() {
        ConfigManager.PluginSettings settings = this.plugin.configManager().settings();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (!settings.general().enabled() || player.getGameMode() == GameMode.SPECTATOR) {
                remove(player);
                continue;
            }
            if (!settings.general().allowCreative() && player.getGameMode() == GameMode.CREATIVE) {
                remove(player);
                continue;
            }
            if (player.hasPermission(settings.permissions().bypassSlowdown())) {
                remove(player);
                continue;
            }
            var target = player.getTargetBlockExact(settings.general().playerTargetRange());
            if (target == null) {
                remove(player);
                continue;
            }
            Optional<TreeStructure> structure = this.plugin.treeDetectionService().detect(target, player);
            if (structure.isEmpty()) {
                remove(player);
                continue;
            }
            double slowdown = structure.get().slowdownCount() * settings.miningSlowdown().perTreeBlockPercent();
            slowdown = Math.min(settings.miningSlowdown().maxPercent(), slowdown);
            if (slowdown <= 0D) {
                remove(player);
                continue;
            }
            apply(player, slowdown);
        }
    }

    private void apply(Player player, double slowdown) {
        Double current = this.appliedValues.get(player.getUniqueId());
        if (current != null && Double.compare(current, slowdown) == 0) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute == null) {
            return;
        }
        removeModifier(attribute);
        attribute.addModifier(new AttributeModifier(this.modifierKey, -slowdown, AttributeModifier.Operation.ADD_SCALAR));
        this.appliedValues.put(player.getUniqueId(), slowdown);
    }

    private void remove(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute != null) {
            removeModifier(attribute);
        }
        this.appliedValues.remove(player.getUniqueId());
    }

    private void removeAll() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            remove(player);
        }
    }

    private void removeModifier(AttributeInstance attribute) {
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getKey().equals(this.modifierKey)) {
                attribute.removeModifier(modifier);
                return;
            }
        }
    }

    private void shutdownTask() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }
}
