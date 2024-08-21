package me.dev_name.plugin_name.command;

import me.dev_name.plugin_name.config.ConfigKeys;
import me.dev_name.plugin_name.version_specific.VersionSpecific;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class DoWorkCMD implements CommandExecutor {
	public DoWorkCMD(JavaPlugin plugin) {
		Objects.requireNonNull(plugin.getCommand(ConfigKeys.doWork_Name)).setExecutor(this);
	}

	@Override
	public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command,
							 @NotNull String label, @NotNull String[] strings) {
		commandSender.sendMessage("Working right now!!");
		ItemStack item = new ItemStack(VersionSpecific.INSTANCE.exoticItem());
		ItemMeta meta = item.getItemMeta();
		assert meta != null;
		meta.setDisplayName(commandSender.getName());
		item.setItemMeta(meta);
		if (commandSender instanceof Player p) {
			p.getInventory().addItem(item);
		} else {
			commandSender.sendMessage("The exotic item of this version is " + item);
		}
		return true;
	}
}
