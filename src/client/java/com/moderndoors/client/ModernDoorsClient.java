package com.moderndoors.client;

import com.moderndoors.registry.ModBlockEntities;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class ModernDoorsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlockEntities.MODERN_DOOR, ModernDoorRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.DOOR_BASE, DoorBaseRenderer::new);
	}
}
