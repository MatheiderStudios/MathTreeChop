package com.matheider.MathTreeChop.command;

import com.matheider.MathTreeChop.MathTreeChopPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MathTreeChopCommand implements CommandExecutor, TabCompleter {

    private final MathTreeChopPlugin plugin;

    public MathTreeChopCommand(MathTreeChopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(this.plugin.configManager().settings().permissions().command())) {
            sender.sendMessage(this.plugin.languageManager().prefixed("command.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(this.plugin.languageManager().prefixed("command.version", Map.of(
                "plugin", this.plugin.getName(),
                "version", this.plugin.getPluginMeta().getVersion()
            )));
            sender.sendMessage(this.plugin.languageManager().prefixed("command.usage"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(this.plugin.configManager().settings().permissions().reload())) {
                sender.sendMessage(this.plugin.languageManager().prefixed("command.no-permission"));
                return true;
            }
            this.plugin.loadPluginState();
            sender.sendMessage(this.plugin.languageManager().prefixed("command.reloaded"));
            return true;
        }
        sender.sendMessage(this.plugin.languageManager().prefixed("command.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(input) && sender.hasPermission(this.plugin.configManager().settings().permissions().reload())) {
                return List.of("reload");
            }
        }
        return List.of();
    }
}
