package me.dev_name.plugin_name.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.FileConfigurationOptions;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
	public ConfigManager(JavaPlugin plugin) {
		FileConfiguration config = plugin.getConfig();
		FileConfigurationOptions options = config.options();
		boolean tmp = options.copyDefaults();
		options.copyDefaults(true);
		plugin.saveConfig();
		options.copyDefaults(tmp);

		workingHours = config.getDouble(ConfigKeys.workingHours_Key);
		isWorkingHardEnough = config.getBoolean(ConfigKeys.isWorkingHardEnough_Key);
	}

	public final double workingHours;
	public final boolean isWorkingHardEnough;
}
