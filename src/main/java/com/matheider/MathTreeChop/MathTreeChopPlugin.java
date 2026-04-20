package com.matheider.MathTreeChop;

import com.matheider.MathTreeChop.command.MathTreeChopCommand;
import com.matheider.MathTreeChop.config.ConfigManager;
import com.matheider.MathTreeChop.config.LanguageManager;
import com.matheider.MathTreeChop.listener.TreeBreakListener;
import com.matheider.MathTreeChop.service.FallingAnimationService;
import com.matheider.MathTreeChop.service.MiningSlowdownService;
import com.matheider.MathTreeChop.service.TreeDetectionService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MathTreeChopPlugin extends JavaPlugin {

    private final HEX hex = new HEX();
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private TreeDetectionService treeDetectionService;
    private FallingAnimationService fallingAnimationService;
    private MiningSlowdownService miningSlowdownService;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this, this.hex);
        loadPluginState();
        this.treeDetectionService = new TreeDetectionService(this);
        this.fallingAnimationService = new FallingAnimationService(this);
        this.miningSlowdownService = new MiningSlowdownService(this);
        getServer().getPluginManager().registerEvents(new TreeBreakListener(this), this);
        PluginCommand command = getCommand("mathtreechop");
        if (command != null) {
            MathTreeChopCommand executor = new MathTreeChopCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        this.miningSlowdownService.start();
        this.fallingAnimationService.start();
    }

    @Override
    public void onDisable() {
        if (this.miningSlowdownService != null) {
            this.miningSlowdownService.shutdown();
        }
        if (this.fallingAnimationService != null) {
            this.fallingAnimationService.shutdown();
        }
    }

    public void loadPluginState() {
        this.configManager.load();
        this.languageManager.load(this.configManager.settings().language());
        if (this.treeDetectionService != null) {
            this.treeDetectionService.clearCache();
        }
        if (this.miningSlowdownService != null) {
            this.miningSlowdownService.reload();
        }
        if (this.fallingAnimationService != null) {
            this.fallingAnimationService.reload();
        }
    }

    public HEX hex() {
        return this.hex;
    }

    public ConfigManager configManager() {
        return this.configManager;
    }

    public LanguageManager languageManager() {
        return this.languageManager;
    }

    public TreeDetectionService treeDetectionService() {
        return this.treeDetectionService;
    }

    public FallingAnimationService fallingAnimationService() {
        return this.fallingAnimationService;
    }

    public MiningSlowdownService miningSlowdownService() {
        return this.miningSlowdownService;
    }
}
