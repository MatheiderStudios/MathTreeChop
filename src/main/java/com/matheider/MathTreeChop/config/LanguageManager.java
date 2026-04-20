package com.matheider.MathTreeChop.config;

import com.matheider.MathTreeChop.HEX;
import com.matheider.MathTreeChop.MathTreeChopPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

public final class LanguageManager {

    private final MathTreeChopPlugin plugin;
    private final HEX hex;
    private YamlConfiguration languageFile;

    public LanguageManager(MathTreeChopPlugin plugin, HEX hex) {
        this.plugin = plugin;
        this.hex = hex;
    }

    public void load(String languageName) {
        File folder = new File(this.plugin.getDataFolder(), "language");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        saveResourceIfMissing("language/pt-BR.yml");
        saveResourceIfMissing("language/en-US.yml");
        File file = new File(folder, languageName + ".yml");
        if (!file.exists()) {
            file = new File(folder, "pt-BR.yml");
        }
        this.languageFile = YamlConfiguration.loadConfiguration(file);
    }

    public Component component(String path) {
        return component(path, Map.of());
    }

    public Component component(String path, Map<String, String> replacements) {
        return this.hex.colorizeToComponent(applyReplacements(get(path), replacements));
    }

    public Component prefixed(String path) {
        return prefixed(path, Map.of());
    }

    public Component prefixed(String path, Map<String, String> replacements) {
        return this.hex.colorizeToComponent(get("prefix") + applyReplacements(get(path), replacements));
    }

    public String get(String path) {
        if (this.languageFile == null) {
            return path;
        }
        return this.languageFile.getString(path, path);
    }

    private String applyReplacements(String message, Map<String, String> replacements) {
        String result = message;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return result;
    }

    private void saveResourceIfMissing(String path) {
        File target = new File(this.plugin.getDataFolder(), path);
        if (!target.exists()) {
            this.plugin.saveResource(path, false);
        }
    }
}
