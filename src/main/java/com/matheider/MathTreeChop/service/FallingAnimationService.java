package com.matheider.MathTreeChop.service;

import com.matheider.MathTreeChop.MathTreeChopPlugin;
import com.matheider.MathTreeChop.config.ConfigManager;
import com.matheider.MathTreeChop.model.TreeBlockKind;
import com.matheider.MathTreeChop.model.TreeBlockSnapshot;
import com.matheider.MathTreeChop.model.TreeStructure;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FallingAnimationService {

    private final MathTreeChopPlugin plugin;
    private final List<ActiveAnimation> animations = new ArrayList<>();
    private BukkitTask task;

    public FallingAnimationService(MathTreeChopPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reload();
    }

    public void reload() {
        shutdownTask();
        this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        shutdownTask();
        for (ActiveAnimation animation : List.copyOf(this.animations)) {
            finish(animation);
        }
        this.animations.clear();
    }

    public void spawn(TreeStructure structure, Player player, List<ItemStack> drops) {
        ConfigManager.AnimationSettings settings = this.plugin.configManager().settings().animation();
        if (!settings.enabled()) {
            dropItems(structure, drops, resolveDropLocation(structure, determineDirection(structure, player)));
            return;
        }
        Location origin = structure.originLocation();
        FallDirection direction = determineDirection(structure, player);
        List<BlockDisplay> displays = new ArrayList<>(structure.blocks().size());
        for (TreeBlockSnapshot snapshot : structure.blocks()) {
            BlockDisplay display = (BlockDisplay) structure.world().spawnEntity(origin, EntityType.BLOCK_DISPLAY);
            display.setBlock(snapshot.blockData());
            display.setViewRange(settings.viewRange());
            display.setInterpolationDuration(settings.interpolationDuration());
            display.setInterpolationDelay(0);
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0F);
            display.setShadowStrength(0F);
            if (settings.brightness().enabled()) {
                display.setBrightness(new Display.Brightness(settings.brightness().block(), settings.brightness().sky()));
            }
            displays.add(display);
        }
        int fallDurationTicks = scaleDuration(settings.fallDurationTicks(), structure.logCount(), settings);
        int bounceDurationTicks = scaleDuration(settings.bounceDurationTicks(), structure.logCount(), settings);
        ActiveAnimation animation = new ActiveAnimation(structure, direction, drops, displays, fallDurationTicks, bounceDurationTicks);
        this.animations.add(animation);
        playStartSound(structure.originLocation());
        updateAnimation(animation);
    }

    private void tick() {
        Iterator<ActiveAnimation> iterator = this.animations.iterator();
        while (iterator.hasNext()) {
            ActiveAnimation animation = iterator.next();
            animation.tick++;
            if (animation.tick == animation.fallDurationTicks()) {
                playImpactSound(animation.structure.originLocation());
            }
            if (animation.tick > animation.totalDuration()) {
                finish(animation);
                iterator.remove();
                continue;
            }
            updateAnimation(animation);
        }
    }

    private void updateAnimation(ActiveAnimation animation) {
        float angle = (float) Math.toRadians(calculateAngle(animation));
        for (int i = 0; i < animation.structure.blocks().size(); i++) {
            TreeBlockSnapshot snapshot = animation.structure.blocks().get(i);
            BlockDisplay display = animation.displays.get(i);
            if (!display.isValid()) {
                continue;
            }
            display.setTransformationMatrix(createMatrix(snapshot, animation.direction, angle));
        }
    }

    private Matrix4f createMatrix(TreeBlockSnapshot snapshot, FallDirection direction, float angle) {
        Vector relative = snapshot.relative();
        return switch (direction) {
            case NORTH -> new Matrix4f()
                .translate(0.5F, 0F, 0.5F)
                .rotateX(-angle)
                .translate((float) relative.getX() - 0.5F, (float) relative.getY(), (float) relative.getZ() - 0.5F);
            case SOUTH -> new Matrix4f()
                .translate(0.5F, 0F, 0.5F)
                .rotateX(angle)
                .translate((float) relative.getX() - 0.5F, (float) relative.getY(), (float) relative.getZ() - 0.5F);
            case EAST -> new Matrix4f()
                .translate(0.5F, 0F, 0.5F)
                .rotateZ(-angle)
                .translate((float) relative.getX() - 0.5F, (float) relative.getY(), (float) relative.getZ() - 0.5F);
            case WEST -> new Matrix4f()
                .translate(0.5F, 0F, 0.5F)
                .rotateZ(angle)
                .translate((float) relative.getX() - 0.5F, (float) relative.getY(), (float) relative.getZ() - 0.5F);
        };
    }

    private double calculateAngle(ActiveAnimation animation) {
        ConfigManager.AnimationSettings settings = this.plugin.configManager().settings().animation();
        int tick = animation.tick;
        int fallTicks = animation.fallDurationTicks();
        int bounceTicks = animation.bounceDurationTicks();
        double impactBaseAngle = 90D;
        if (fallTicks <= 0) {
            return impactBaseAngle;
        }
        if (tick <= fallTicks) {
            double progress = Math.min(1D, tick / (double) fallTicks);
            return lerp(0D, impactBaseAngle, easeInCubic(progress));
        }
        if (bounceTicks <= 0) {
            return impactBaseAngle;
        }
        double progress = Math.min(1D, (tick - fallTicks) / (double) bounceTicks);
        return impactBaseAngle + calculateImpactOffset(progress, settings.bounceAngle());
    }

    private double calculateImpactOffset(double progress, double bounceAngle) {
        if (progress <= 0.5D) {
            double localProgress = progress / 0.5D;
            return -Math.sin(localProgress * Math.PI) * (bounceAngle / 1.5D);
        }
        double localProgress = (progress - 0.5D) / 0.5D;
        return -Math.sin(localProgress * Math.PI) * (bounceAngle / 2.25D);
    }

    private int scaleDuration(int baseDuration, int logCount, ConfigManager.AnimationSettings settings) {
        if (baseDuration <= 0) {
            return 0;
        }
        double extraDuration = Math.min(settings.maxAdditionalDurationPercent(), logCount * settings.perLogDurationPercent());
        double multiplier = 1D + extraDuration;
        return Math.max(1, (int) Math.round(baseDuration * multiplier));
    }

    private double easeInOutSine(double progress) {
        return -(Math.cos(Math.PI * progress) - 1D) / 2D;
    }

    private double easeOutSine(double progress) {
        return Math.sin((progress * Math.PI) / 2D);
    }

    private double easeInOutCubic(double progress) {
        if (progress < 0.5D) {
            return 4D * progress * progress * progress;
        }
        return 1D - Math.pow(-2D * progress + 2D, 3D) / 2D;
    }

    private double easeOutCubic(double progress) {
        return 1D - Math.pow(1D - progress, 3D);
    }

    private double easeInCubic(double progress) {
        return progress * progress * progress;
    }

    private double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private void finish(ActiveAnimation animation) {
        Location dropLocation = resolveDropLocation(animation.structure, animation.direction);
        for (BlockDisplay display : animation.displays) {
            if (display.isValid()) {
                display.remove();
            }
        }
        dropItems(animation.structure, animation.drops, dropLocation);
    }

    private void dropItems(TreeStructure structure, List<ItemStack> drops, Location location) {
        if (!this.plugin.configManager().settings().animation().drops().spawnOnImpact()) {
            return;
        }
        World world = structure.world();
        ConfigManager.DropSettings dropSettings = this.plugin.configManager().settings().animation().drops();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (ItemStack stack : drops) {
            Item item = world.dropItem(location.clone().add(
                random.nextDouble(-dropSettings.spreadHorizontal(), dropSettings.spreadHorizontal()),
                0D,
                random.nextDouble(-dropSettings.spreadHorizontal(), dropSettings.spreadHorizontal())
            ), stack.clone());
            item.setVelocity(new Vector(
                random.nextDouble(-0.1D, 0.1D),
                dropSettings.velocityY(),
                random.nextDouble(-0.1D, 0.1D)
            ));
        }
    }

    private Location resolveDropLocation(TreeStructure structure, FallDirection direction) {
        double distance = 0D;
        for (TreeBlockSnapshot snapshot : structure.blocks()) {
            if (snapshot.kind() != TreeBlockKind.LOG) {
                continue;
            }
            distance = Math.max(distance, direction.project(snapshot.relative()));
        }
        return structure.originLocation().clone().add(0.5D + direction.modX * Math.max(0D, distance * 0.5D), 0.2D, 0.5D + direction.modZ * Math.max(0D, distance * 0.5D));
    }

    private FallDirection determineDirection(TreeStructure structure, Player player) {
        Location origin = structure.originLocation().clone().add(0.5D, 0D, 0.5D);
        double dx = player.getLocation().getX() - origin.getX();
        double dz = player.getLocation().getZ() - origin.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0D ? FallDirection.WEST : FallDirection.EAST;
        }
        return dz > 0D ? FallDirection.NORTH : FallDirection.SOUTH;
    }

    private void playStartSound(Location location) {
        ConfigManager.SoundSettings sounds = this.plugin.configManager().settings().animation().sounds();
        if (!sounds.enabled()) {
            return;
        }
        location.getWorld().playSound(location, sounds.startSound(), SoundCategory.BLOCKS, sounds.startVolume(), sounds.startPitch());
    }

    private void playImpactSound(Location location) {
        ConfigManager.SoundSettings sounds = this.plugin.configManager().settings().animation().sounds();
        if (!sounds.enabled()) {
            return;
        }
        location.getWorld().playSound(location, sounds.impactSound(), SoundCategory.BLOCKS, sounds.impactVolume(), sounds.impactPitch());
    }

    private void shutdownTask() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private enum FallDirection {
        NORTH(0D, -1D),
        SOUTH(0D, 1D),
        EAST(1D, 0D),
        WEST(-1D, 0D);

        private final double modX;
        private final double modZ;

        FallDirection(double modX, double modZ) {
            this.modX = modX;
            this.modZ = modZ;
        }

        private double project(Vector vector) {
            return switch (this) {
                case NORTH -> -vector.getZ();
                case SOUTH -> vector.getZ();
                case EAST -> vector.getX();
                case WEST -> -vector.getX();
            };
        }
    }

    private static final class ActiveAnimation {
        private final TreeStructure structure;
        private final FallDirection direction;
        private final List<ItemStack> drops;
        private final List<BlockDisplay> displays;
        private final int fallDurationTicks;
        private final int bounceDurationTicks;
        private int tick;

        private ActiveAnimation(TreeStructure structure, FallDirection direction, List<ItemStack> drops, List<BlockDisplay> displays, int fallDurationTicks, int bounceDurationTicks) {
            this.structure = structure;
            this.direction = direction;
            this.drops = List.copyOf(drops);
            this.displays = displays;
            this.fallDurationTicks = fallDurationTicks;
            this.bounceDurationTicks = bounceDurationTicks;
        }

        private int fallDurationTicks() {
            return this.fallDurationTicks;
        }

        private int bounceDurationTicks() {
            return this.bounceDurationTicks;
        }

        private int totalDuration() {
            return this.fallDurationTicks + this.bounceDurationTicks;
        }
    }
}
