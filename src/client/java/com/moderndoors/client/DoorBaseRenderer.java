package com.moderndoors.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.moderndoors.block.DoorBaseBlock;
import com.moderndoors.block.DoorBaseBlockEntity;
import com.moderndoors.block.DoorPanelBlock;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the panels folded into a door base, sliding between where they stand and their stack in the base's block.
 * They telescope: each moves in proportion to how far it has to go, so they all arrive together. First they step
 * off the middle line onto their own tracks, alternating behind and in front of the post, so they can pass each other.
 * Panels that are standing as real blocks again aren't drawn here.
 */
public class DoorBaseRenderer implements BlockEntityRenderer<DoorBaseBlockEntity, DoorBaseRenderer.State> {
	private static final float PIXEL = 1.0F / 16.0F;

	public DoorBaseRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(
		DoorBaseBlockEntity base, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(base, state, partialTicks, cameraPosition, breakProgress);
		state.panels.clear();
		state.offsets.clear();
		if (!(base.getLevel() instanceof ClientLevel level) || base.panels().isEmpty()) {
			return;
		}
		float folded = base.folded(partialTicks);
		boolean foldedAway = base.getBlockState().getValue(DoorBaseBlock.FOLDED);
		Direction toward = base.toward();
		Direction side = toward.getClockWise();
		BlockPos origin = base.getBlockPos();
		for (int i = 0; i < base.panels().size(); i++) {
			int distance = i + 1;
			BlockPos home = origin.relative(toward, distance);
			BlockState standing = level.getBlockState(home);
			if (!foldedAway && standing.getBlock() instanceof DoorPanelBlock && !standing.getValue(DoorPanelBlock.HIDDEN)) {
				continue;
			}
			float along = distance * (1.0F - folded);
			float across = (track(distance) - 8.0F * PIXEL) * Math.min(1.0F, folded * 3.0F);
			BlockPos near = origin.relative(toward, Math.round(along));

			MovingBlockRenderState panel = new MovingBlockRenderState();
			panel.randomSeedPos = home;
			panel.blockPos = near;
			panel.blockState = base.panels().get(i);
			panel.biome = level.getBiome(near);
			panel.cardinalLighting = level.cardinalLighting();
			panel.lightEngine = level.getLightEngine();
			state.panels.add(panel);
			state.offsets.add(new Vec3(
				toward.getStepX() * along + side.getStepX() * across,
				0.0,
				toward.getStepZ() * along + side.getStepZ() * across));
		}
	}

	/**
	 * Where a panel's middle rests across the base block, in blocks: odd panels on tracks behind the post, even ones
	 * in front, 1.5 pixels apart, working outward.
	 */
	private static float track(int distance) {
		int step = (distance - 1) / 2;
		return distance % 2 == 1 ? (6.0F - 0.75F - 1.5F * step) * PIXEL : (10.0F + 0.75F + 1.5F * step) * PIXEL;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < state.panels.size(); i++) {
			Vec3 offset = state.offsets.get(i);
			poseStack.pushPose();
			poseStack.translate(offset.x, offset.y, offset.z);
			collector.submitMovingBlock(poseStack, state.panels.get(i), 0);
			poseStack.popPose();
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	public static class State extends BlockEntityRenderState {
		public final List<MovingBlockRenderState> panels = new ArrayList<>();
		public final List<Vec3> offsets = new ArrayList<>();
	}
}
