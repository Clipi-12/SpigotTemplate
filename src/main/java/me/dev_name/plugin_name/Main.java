package me.dev_name.plugin_name;

import me.dev_name.plugin_name.command.DoWorkCMD;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
	@Override
	public void onEnable() {
		getLogger().info("The plugin is being started...");
		new DoWorkCMD(this);
		getLogger().info("Hello World!");
	}

	@Override
	public void onDisable() {
		getLogger().info("The plugin is is being disabled!");
	}
}
