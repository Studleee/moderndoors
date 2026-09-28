package com.moderndoors.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.moderndoors.ModernDoors;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Shortcuts for adding content. Everything registered here also shows up in the mod's creative tab.
 */
public final class Register {
	private static final List<Item> CREATIVE_TAB_ITEMS = new ArrayList<>();

	private Register() {
	}

	/** A plain item with default properties, like a gem or ingredient. */
	public static Item item(String name) {
		return item(name, Item::new, new Item.Properties());
	}

	/** A plain item with custom properties, e.g. {@code new Item.Properties().stacksTo(16)}. */
	public static Item item(String name, Item.Properties properties) {
		return item(name, Item::new, properties);
	}

	/** An item that uses your own class, e.g. {@code Register.item("wand", MyWandItem::new, new Item.Properties())}. */
	public static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ModernDoors.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		CREATIVE_TAB_ITEMS.add(item);
		return item;
	}

	/**
	 * A food item. Nutrition is half-hunger-bars restored (a carrot is 3, steak is 8).
	 * Saturation controls how long you stay full (0.1 is weak, 0.8 is steak).
	 */
	public static Item food(String name, int nutrition, float saturation) {
		FoodProperties food = new FoodProperties.Builder()
			.nutrition(nutrition)
			.saturationModifier(saturation)
			.build();
		return item(name, new Item.Properties().food(food));
	}

	/** A plain block plus the item you hold to place it. */
	public static Block block(String name, BlockBehaviour.Properties properties) {
		return block(name, Block::new, properties);
	}

	/** A block that uses your own class, plus the item you hold to place it. */
	public static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return block(name, factory, properties, BlockItem::new);
	}

	/** A block that uses your own class, plus an item of your own class to place it. */
	public static Block block(
		String name,
		Function<BlockBehaviour.Properties, Block> factory,
		BlockBehaviour.Properties properties,
		BiFunction<Block, Item.Properties, Item> itemFactory
	) {
		Identifier id = ModernDoors.id(name);
		BlockItemId ids = BlockItemId.create(id, id);

		Block block = Registry.register(BuiltInRegistries.BLOCK, ids.block(), factory.apply(properties.setId(ids.block())));

		Item blockItem = itemFactory.apply(block, new Item.Properties().useBlockDescriptionPrefix().setId(ids.item()));
		Registry.register(BuiltInRegistries.ITEM, ids.item(), blockItem);
		CREATIVE_TAB_ITEMS.add(blockItem);

		return block;
	}

	static List<Item> creativeTabItems() {
		return Collections.unmodifiableList(CREATIVE_TAB_ITEMS);
	}
}
