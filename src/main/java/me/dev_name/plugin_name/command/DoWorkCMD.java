package me.dev_name.plugin_name.command;

import me.dev_name.plugin_name.config.CompilationInfo;
import me.dev_name.plugin_name.config.ConfigKeys;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DoWorkCMD implements CommandExecutor {
	public DoWorkCMD(JavaPlugin plugin) {
		Objects.requireNonNull(plugin.getCommand(ConfigKeys.doWork_Name)).setExecutor(this);
	}

	@Override
	public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command,
							 @NotNull String label, @NotNull String[] strings) {
		commandSender.sendMessage("Working right now!! (Bukkit API Version = " + CompilationInfo.apiVersion + ")");
		return true;
	}
}
