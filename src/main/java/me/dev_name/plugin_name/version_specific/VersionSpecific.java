package me.dev_name.plugin_name.version_specific;

import org.bukkit.Material;

public interface VersionSpecific {
	VersionSpecific INSTANCE = VersionSpecificImpl.INSTANCE;

	Material exoticItem();
}
