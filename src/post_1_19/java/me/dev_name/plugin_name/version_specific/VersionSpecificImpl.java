package me.dev_name.plugin_name.version_specific;

import org.bukkit.Material;

class VersionSpecificImpl implements VersionSpecific {
	public static final VersionSpecificImpl INSTANCE = new VersionSpecificImpl();

	private VersionSpecificImpl() {
	}

	@Override
	public Material exoticItem() {
		return Material.SNIFFER_SPAWN_EGG;
	}
}
