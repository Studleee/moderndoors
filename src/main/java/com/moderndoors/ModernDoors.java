package com.moderndoors;

import com.moderndoors.registry.ModBlockEntities;
import com.moderndoors.registry.ModBlocks;
import com.moderndoors.registry.ModCreativeTab;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModernDoors implements ModInitializer {
	public static final String MOD_ID = "moderndoors";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModCreativeTab.initialize();
		LOGGER.info("{} loaded", MOD_ID);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
