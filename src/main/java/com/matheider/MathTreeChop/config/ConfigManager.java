package com.matheider.MathTreeChop.config;

import com.matheider.MathTreeChop.MathTreeChopPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigManager {

    private final MathTreeChopPlugin plugin;
    private PluginSettings settings;

    public ConfigManager(MathTreeChopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        FileConfiguration config = this.plugin.getConfig();
        this.settings = new PluginSettings(
            config.getString("language", "pt-BR"),
            new PermissionSettings(
                config.getString("permissions.use", "math.treechop.use"),
                config.getString("permissions.command", "math.treechop.command"),
                config.getString("permissions.reload", "math.treechop.command.reload"),
                config.getString("permissions.bypass-slowdown", "math.treechop.bypass.slowdown"),
                config.getString("permissions.bypass-durability", "math.treechop.bypass.durability"),
                config.getString("permissions.bypass-tool", "math.treechop.bypass.tool")
            ),
            new GeneralSettings(
                config.getBoolean("general.enabled", true),
                config.getBoolean("general.allow-creative", false),
                config.getBoolean("general.disable-while-sneaking", false),
                config.getInt("general.player-target-range", 6),
                Math.max(1, config.getInt("general.cache-ttl-ticks", 10))
            ),
            new ToolSettings(
                config.getBoolean("tools.require-tool", true),
                parseMaterials(config.getStringList("tools.allowed-materials")),
                config.getBoolean("tools.damage.enabled", true),
                config.getBoolean("tools.damage.count-only-logs", true)
            ),
            new MiningSlowdownSettings(
                config.getBoolean("mining-slowdown.enabled", true),
                Math.max(1, config.getInt("mining-slowdown.refresh-ticks", 2)),
                Math.max(0D, config.getDouble("mining-slowdown.per-tree-block-percent", 0.5D)) / 100D,
                Math.max(0D, config.getDouble("mining-slowdown.max-percent", 95D)) / 100D,
                config.getBoolean("mining-slowdown.count-logs", true),
                config.getBoolean("mining-slowdown.count-leaves", true)
            ),
            new TreeDetectionSettings(
                Math.max(1, config.getInt("tree-detection.min-logs", 1)),
                Math.max(1, config.getInt("tree-detection.min-leaves", 4)),
                Math.max(1, config.getInt("tree-detection.max-logs", 512)),
                Math.max(1, config.getInt("tree-detection.search-radius-horizontal", 1)),
                Math.max(1, config.getInt("tree-detection.search-radius-upward", 1)),
                config.getBoolean("tree-detection.require-leaves", true),
                Math.max(1, config.getInt("tree-detection.leaf-search-radius", 6)),
                config.getBoolean("tree-detection.ignore-persistent-leaves", true),
                new ExtraBlockSettings(
                    config.getBoolean("tree-detection.extra-blocks.vines", true),
                    config.getBoolean("tree-detection.extra-blocks.cocoa", true),
                    config.getBoolean("tree-detection.extra-blocks.bee-nests", true)
                )
            ),
            new AnimationSettings(
                config.getBoolean("animation.enabled", true),
                Math.max(1, config.getInt("animation.fall-duration-ticks", 54)),
                Math.max(0, config.getInt("animation.bounce-duration-ticks", 18)),
                Math.max(0D, config.getDouble("animation.bounce-angle", 7D)),
                Math.max(0D, config.getDouble("animation.duration.per-log-percent", 1D)) / 100D,
                Math.max(0D, config.getDouble("animation.duration.max-additional-percent", 150D)) / 100D,
                (float) config.getDouble("animation.view-range", 64D),
                Math.max(0, config.getInt("animation.interpolation-duration", 3)),
                new BrightnessSettings(
                    config.getInt("animation.brightness.sky", -1),
                    config.getInt("animation.brightness.block", -1)
                ),
                new SoundSettings(
                    config.getBoolean("animation.sounds.enabled", true),
                    parseSound(config.getString("animation.sounds.start-sound"), Sound.BLOCK_WOOD_BREAK),
                    (float) config.getDouble("animation.sounds.start-volume", 1D),
                    (float) config.getDouble("animation.sounds.start-pitch", 0.8D),
                    parseSound(config.getString("animation.sounds.impact-sound"), Sound.ENTITY_GENERIC_BIG_FALL),
                    (float) config.getDouble("animation.sounds.impact-volume", 1D),
                    (float) config.getDouble("animation.sounds.impact-pitch", 0.75D)
                ),
                new DropSettings(
                    config.getBoolean("animation.drops.spawn-on-impact", true),
                    config.getDouble("animation.drops.spread-horizontal", 0.2D),
                    config.getDouble("animation.drops.velocity-y", 0.25D)
                )
            )
        );
    }

    public PluginSettings settings() {
        return this.settings;
    }

    private Set<Material> parseMaterials(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            Material material = Material.matchMaterial(value.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                materials.add(material);
            }
        }
        return Collections.unmodifiableSet(materials);
    }

    private Sound parseSound(String value, Sound fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public record PluginSettings(
        String language,
        PermissionSettings permissions,
        GeneralSettings general,
        ToolSettings tools,
        MiningSlowdownSettings miningSlowdown,
        TreeDetectionSettings treeDetection,
        AnimationSettings animation
    ) {
    }

    public record PermissionSettings(
        String use,
        String command,
        String reload,
        String bypassSlowdown,
        String bypassDurability,
        String bypassTool
    ) {
    }

    public record GeneralSettings(
        boolean enabled,
        boolean allowCreative,
        boolean disableWhileSneaking,
        int playerTargetRange,
        int cacheTtlTicks
    ) {
    }

    public record ToolSettings(
        boolean requireTool,
        Set<Material> allowedTools,
        boolean durabilityDamageEnabled,
        boolean countOnlyLogsForDamage
    ) {
    }

    public record MiningSlowdownSettings(
        boolean enabled,
        int refreshTicks,
        double perTreeBlockPercent,
        double maxPercent,
        boolean countLogs,
        boolean countLeaves
    ) {
    }

    public record TreeDetectionSettings(
        int minLogs,
        int minLeaves,
        int maxLogs,
        int searchRadiusHorizontal,
        int searchRadiusUpward,
        boolean requireLeaves,
        int leafSearchRadius,
        boolean ignorePersistentLeaves,
        ExtraBlockSettings extraBlocks
    ) {
    }

    public record ExtraBlockSettings(
        boolean includeVines,
        boolean includeCocoa,
        boolean includeBeeNests
    ) {
    }

    public record AnimationSettings(
        boolean enabled,
        int fallDurationTicks,
        int bounceDurationTicks,
        double bounceAngle,
        double perLogDurationPercent,
        double maxAdditionalDurationPercent,
        float viewRange,
        int interpolationDuration,
        BrightnessSettings brightness,
        SoundSettings sounds,
        DropSettings drops
    ) {
    }

    public record BrightnessSettings(
        int sky,
        int block
    ) {
        public boolean enabled() {
            return this.sky >= 0 && this.block >= 0;
        }
    }

    public record SoundSettings(
        boolean enabled,
        Sound startSound,
        float startVolume,
        float startPitch,
        Sound impactSound,
        float impactVolume,
        float impactPitch
    ) {
    }

    public record DropSettings(
        boolean spawnOnImpact,
        double spreadHorizontal,
        double velocityY
    ) {
    }
}
