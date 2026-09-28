package com.moderndoors.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.moderndoors.block.DoorBaseBlock;
import com.moderndoors.block.DoorPanelBlock;
import com.moderndoors.block.ModernDoorBlock;
import com.moderndoors.door.DoorKind;
import com.moderndoors.door.DoorMaterial;
import com.moderndoors.item.ModernDoorItem;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Every door, each kind in each material it comes in, named like {@code oak_pivot_door}, and the black aluminum
 * panel and base.
 */
public final class ModBlocks {
	private static final Map<DoorKind, Map<DoorMaterial, ModernDoorBlock>> DOORS = new EnumMap<>(DoorKind.class);
	private static final List<ModernDoorBlock> ALL = new ArrayList<>();
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
	}

	/** Black aluminum folding panel doors: glass panels lined up from a base post, which they all slide back into. */
	public static final Block DOOR_PANEL = Register.block(
		"black_aluminum_door_panel",
		DoorPanelBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(3.0F)
			.sound(SoundType.METAL)
			.noOcclusion()
			.pushReaction(PushReaction.IMMOVEABLE)
	);
	public static final Block DOOR_BASE = Register.block(
		"black_aluminum_door_base",
		DoorBaseBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(3.0F)
			.sound(SoundType.METAL)
			.noOcclusion()
			.pushReaction(PushReaction.IMMOVEABLE)
	);

	private ModBlocks() {
	}

	public static @Nullable ModernDoorBlock get(DoorKind kind, DoorMaterial material) {
		return DOORS.get(kind).get(material);
	}

	public static List<ModernDoorBlock> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static void initialize() {
	}
}
