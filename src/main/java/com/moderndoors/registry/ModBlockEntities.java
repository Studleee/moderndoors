package com.moderndoors.registry;

import java.util.Set;

import com.moderndoors.ModernDoors;
import com.moderndoors.block.ModernDoorBlockEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<ModernDoorBlockEntity> MODERN_DOOR = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		ModernDoors.id("modern_door"),
		new BlockEntityType<>(ModernDoorBlockEntity::new, Set.<Block>copyOf(ModBlocks.all()))
	);

	private ModBlockEntities() {
	}

	public static void initialize() {
	}
}
