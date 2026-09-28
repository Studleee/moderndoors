package com.moderndoors.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.moderndoors.block.ModernDoorBlock;
import com.moderndoors.door.DoorKind;
import com.moderndoors.door.DoorMaterial;
import com.moderndoors.item.ModernDoorItem;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/**
 * Every door, each kind in each material it comes in, named like {@code oak_pivot_door}, and a door frame block in
 * every material, named like {@code oak_door_frame}. Doors are fitted into a rectangle of frames.
 */
public final class ModBlocks {
	private static final Map<DoorKind, Map<DoorMaterial, ModernDoorBlock>> DOORS = new EnumMap<>(DoorKind.class);
	private static final List<ModernDoorBlock> ALL = new ArrayList<>();
	private static final Set<Block> FRAMES = new HashSet<>();

	static {
		for (DoorKind kind : DoorKind.values()) {
			Map<DoorMaterial, ModernDoorBlock> byMaterial = new EnumMap<>(DoorMaterial.class);
			for (DoorMaterial material : DoorMaterial.values()) {
				if (material.availableFor(kind)) {
					ModernDoorBlock door = (ModernDoorBlock) Register.block(
						material.id() + "_" + kind.id() + "_door",
						properties -> new ModernDoorBlock(kind, material, properties),
						BlockBehaviour.Properties.of()
							.mapColor(material.mapColor())
							.strength(material.strength())
							.sound(material.soundType())
							.noOcclusion()
							.dynamicShape()
							.pushReaction(PushReaction.POPPED),
						ModernDoorItem::new
					);
					byMaterial.put(material, door);
					ALL.add(door);
				}
			}
			DOORS.put(kind, byMaterial);
		}
		for (DoorMaterial material : DoorMaterial.values()) {
			FRAMES.add(Register.block(
				material.id() + "_door_frame",
				BlockBehaviour.Properties.of()
					.mapColor(material.mapColor())
					.strength(material.frameStrength())
					.sound(material.soundType())
			));
		}
	}

	private ModBlocks() {
	}

	public static @Nullable ModernDoorBlock get(DoorKind kind, DoorMaterial material) {
		return DOORS.get(kind).get(material);
	}

	public static List<ModernDoorBlock> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static boolean isFrame(BlockState state) {
		return FRAMES.contains(state.getBlock());
	}

	public static void initialize() {
	}
}
