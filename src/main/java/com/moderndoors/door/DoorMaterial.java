package com.moderndoors.door;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * What a door is made of. Wood pivot doors are solid wood; on folding and sliding doors the wood is the frame
 * around glass. Black steel and white aluminum are always frames around glass, and concrete is only for pivot doors.
 */
public enum DoorMaterial {
	OAK("oak", Style.WOOD, MapColor.WOOD),
	SPRUCE("spruce", Style.WOOD, MapColor.PODZOL),
	BIRCH("birch", Style.WOOD, MapColor.SAND),
	DARK_OAK("dark_oak", Style.WOOD, MapColor.COLOR_BROWN),
	CHERRY("cherry", Style.WOOD, MapColor.TERRACOTTA_WHITE),
	PALE_OAK("pale_oak", Style.WOOD, MapColor.QUARTZ),
	BLACK_STEEL("black_steel", Style.METAL, MapColor.COLOR_BLACK),
	WHITE_ALUMINUM("white_aluminum", Style.METAL, MapColor.SNOW),
	CONCRETE("concrete", Style.CONCRETE, MapColor.COLOR_LIGHT_GRAY),
	DARK_CONCRETE("dark_concrete", Style.CONCRETE, MapColor.COLOR_GRAY);

	public enum Style {
		WOOD, METAL, CONCRETE
	}

	private final String id;
	private final Style style;
	private final MapColor mapColor;

	DoorMaterial(String id, Style style, MapColor mapColor) {
		this.id = id;
		this.style = style;
		this.mapColor = mapColor;
	}

	public String id() {
		return id;
	}

	public Style style() {
		return style;
	}

	public MapColor mapColor() {
		return mapColor;
	}

	public boolean availableFor(DoorKind kind) {
		return style != Style.CONCRETE || kind == DoorKind.PIVOT;
	}

	/** Whether this door is see-through glass in a frame (otherwise it's a solid slab of wood or concrete). */
	public boolean isGlass(DoorKind kind) {
		return style == Style.METAL || style == Style.WOOD && kind != DoorKind.PIVOT;
	}

	public SoundType soundType() {
		return switch (style) {
			case WOOD -> SoundType.WOOD;
			case METAL -> SoundType.METAL;
			case CONCRETE -> SoundType.STONE;
		};
	}

	public float strength() {
		return style == Style.WOOD ? 3.0F : 4.0F;
	}

	/** Door frames break about as fast as the block they're made from. */
	public float frameStrength() {
		return switch (style) {
			case WOOD -> 2.0F;
			case METAL -> 3.0F;
			case CONCRETE -> 1.8F;
		};
	}

	public SoundEvent openSound() {
		return style == Style.WOOD ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.IRON_DOOR_OPEN;
	}

	public SoundEvent closeSound() {
		return style == Style.WOOD ? SoundEvents.WOODEN_DOOR_CLOSE : SoundEvents.IRON_DOOR_CLOSE;
	}

	/** Heavy concrete sounds deeper; glass and metal a little lighter. */
	public float soundPitch() {
		return switch (style) {
			case WOOD -> 0.9F;
			case METAL -> 1.15F;
			case CONCRETE -> 0.65F;
		};
	}
}
