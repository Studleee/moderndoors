package com.moderndoors.client;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.moderndoors.ModernDoors;
import com.moderndoors.block.ModernDoorBlock;
import com.moderndoors.block.ModernDoorBlockEntity;
import com.moderndoors.door.DoorKind;
import com.moderndoors.door.DoorMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a whole door from its main block, moving the panels by how far open it is.
 * <p>
 * Everything is placed in "door space": x runs across the door from its left edge (as seen from the front) in
 * blocks, y is up, and z runs from the back of the blocks (0) to the front (1), with the door's middle line at
 * z = 0.5. Turning a panel by a positive angle swings its far end toward the back. A door hinged on the right
 * is worked out as if hinged on the left, then mirrored.
 * <p>
 * Doors can be any size, so a panel is built from one tile per block, with frame bars stretched around its edges.
 * The bars are a single color, so stretching them doesn't show.
 */
public class ModernDoorRenderer implements BlockEntityRenderer<ModernDoorBlockEntity, ModernDoorRenderer.State> {
	/** How far the folding panels turn when fully open. Just short of 90 so the stacked panels don't overlap. */
	private static final float FOLDED_ANGLE = 84.0F;
	private static final float PIXEL = 1.0F / 16.0F;
	private static final float BAR = 2.0F * PIXEL;
	/** Frame bars reach a hair past the tiles so their faces never sit exactly on top of each other. */
	private static final float OVERLAP = 0.002F;

	/** One block of door, from its hinge-side bottom corner, centered on z = 0. */
	private static final ModelPart SOLID_TILE = part(box -> box.addBox(0.0F, 0.0F, -1.5F, 16.0F, 16.0F, 3.0F));
	private static final ModelPart GLASS_TILE = part(box -> box.addBox(0.0F, 0.0F, -0.5F, 16.0F, 16.0F, 1.0F));
	/** A one-block cube, scaled into frame bars and handles. */
	private static final ModelPart CUBE = part(box -> box.addBox(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F));

	public ModernDoorRenderer(BlockEntityRendererProvider.Context context) {
	}

	private static ModelPart part(Consumer<CubeListBuilder> boxes) {
		MeshDefinition mesh = new MeshDefinition();
		CubeListBuilder builder = CubeListBuilder.create().texOffs(0, 0);
		boxes.accept(builder);
		mesh.getRoot().addOrReplaceChild("part", builder, PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32).bakeRoot();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(
		ModernDoorBlockEntity door, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(door, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = door.getBlockState();
		if (blockState.getBlock() instanceof ModernDoorBlock block) {
			state.kind = block.kind();
			state.material = block.material();
		}
		state.facing = blockState.getValue(ModernDoorBlock.FACING);
		state.mirrored = blockState.getValue(ModernDoorBlock.HINGE) == DoorHingeSide.RIGHT;
		state.reversed = blockState.getValue(ModernDoorBlock.REVERSED);
		state.width = door.width();
		state.height = door.height();
		state.openness = door.openness(partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float side = state.reversed ? -1.0F : 1.0F;
		int width = state.width;
		float handleHeight = Math.min(1.5F, state.height / 2.0F);

		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.facing.toYRot());
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		if (state.mirrored) {
			poseStack.translate(1 - width, 0.0F, 0.0F);
		}

		switch (state.kind) {
			// Pivots in the middle of its hinge-side block, so it starts half a block before the pivot.
			case PIVOT -> panel(state, poseStack, collector, 0.5F, 0.5F, 90.0F * state.openness * side, -0.5F, width,
				width - 5.0F * PIXEL, state.height / 2.0F, state.height / 2.0F);
			case FOLDING -> {
				// An accordion: each panel turns the opposite way from the one before, so every other hinge juts out.
				float angle = FOLDED_ANGLE * state.openness;
				float radians = angle * (float) Math.PI / 180.0F;
				for (int i = 0; i < width; i++) {
					float x = i * (float) Math.cos(radians);
					float z = 0.5F - (i % 2 == 1 ? side * (float) Math.sin(radians) : 0.0F);
					float turn = (i % 2 == 0 ? side : -side) * angle;
					float handleX = i == width - 1 ? 13.0F * PIXEL : -1.0F;
					panel(state, poseStack, collector, x, z, turn, 0.0F, 1, handleX, handleHeight, 0.75F);
				}
			}
			case SLIDING -> {
				int fixed = width / 2;
				int sliding = width - fixed;
				panel(state, poseStack, collector, 0.0F, 9.0F / 16.0F, 0.0F, 0.0F, fixed, -1.0F, 0.0F, 0.0F);
				panel(state, poseStack, collector, fixed * (1.0F - state.openness), 7.0F / 16.0F, 0.0F, 0.0F, sliding,
					sliding - 3.0F * PIXEL, handleHeight, 1.0F);
			}
		}
		poseStack.popPose();
	}

	/**
	 * One panel, turned by the angle about (x, z) in door space, with its hinge edge {@code start} blocks along from
	 * there. It has a handle bar on both faces at handleX along the panel, unless handleX is negative.
	 */
	private static void panel(
		State state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		float x,
		float z,
		float angle,
		float start,
		int panelWidth,
		float handleX,
		float handleY,
		float handleLength
	) {
		poseStack.pushPose();
		if (state.mirrored) {
			poseStack.translate(state.width - x, 0.0F, z);
			poseStack.rotateDegrees(Axis.YP, 180.0F - angle);
		} else {
			poseStack.translate(x, 0.0F, z);
			poseStack.rotateDegrees(Axis.YP, angle);
		}
		poseStack.translate(start, 0.0F, 0.0F);

		boolean glass = state.material.isGlass(state.kind);
		String look = glass ? "glass" : "solid";
		String folder = "textures/entity/door/";
		Identifier tileTexture = ModernDoors.id(folder + "tile/" + state.material.id() + "_" + look + ".png");
		RenderType tile = glass ? RenderTypes.entityTranslucent(tileTexture) : RenderTypes.entityCutout(tileTexture);
		RenderType frame = RenderTypes.entityCutout(ModernDoors.id(folder + "frame/" + state.material.id() + "_" + look + ".png"));
		int light = state.lightCoords;
		int height = state.height;

		for (int column = 0; column < panelWidth; column++) {
			for (int row = 0; row < height; row++) {
				cube(collector, poseStack, glass ? GLASS_TILE : SOLID_TILE, tile, light, column, row, 0.0F, 1.0F, 1.0F, 1.0F);
			}
		}

		float thickness = (glass ? (state.kind == DoorKind.PIVOT ? 3.0F : 2.0F) : 3.4F) * PIXEL;
		float back = -thickness / 2.0F;
		float tall = height + 2 * OVERLAP;
		cube(collector, poseStack, CUBE, frame, light, -OVERLAP, -OVERLAP, back, BAR + OVERLAP, tall, thickness);
		cube(collector, poseStack, CUBE, frame, light, panelWidth - BAR, -OVERLAP, back, BAR + OVERLAP, tall, thickness);
		cube(collector, poseStack, CUBE, frame, light, BAR, -OVERLAP, back, panelWidth - 2 * BAR, BAR + OVERLAP, thickness);
		cube(collector, poseStack, CUBE, frame, light, BAR, height - BAR, back, panelWidth - 2 * BAR, BAR + OVERLAP, thickness);

		if (handleX >= 0.0F) {
			RenderType handle = RenderTypes.entityCutout(ModernDoors.id(folder + "handle_" + handleColor(state.material) + ".png"));
			float bottom = handleY - handleLength / 2.0F;
			cube(collector, poseStack, CUBE, handle, light, handleX, bottom, thickness / 2.0F, PIXEL, handleLength, PIXEL);
			cube(collector, poseStack, CUBE, handle, light, handleX, bottom, back - PIXEL, PIXEL, handleLength, PIXEL);
		}
		poseStack.popPose();
	}

	/** A model drawn at (x, y, z), stretched by the given amounts. */
	private static void cube(
		SubmitNodeCollector collector,
		PoseStack poseStack,
		ModelPart model,
		RenderType type,
		int light,
		float x,
		float y,
		float z,
		float scaleX,
		float scaleY,
		float scaleZ
	) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.scale(scaleX, scaleY, scaleZ);
		collector.submitModelPart(model, poseStack, type, light, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}

	private static String handleColor(DoorMaterial material) {
		return switch (material.style()) {
			case CONCRETE -> "brass";
			case METAL -> material == DoorMaterial.WHITE_ALUMINUM ? "silver" : "black";
			case WOOD -> "black";
		};
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	public static class State extends BlockEntityRenderState {
		public DoorKind kind = DoorKind.PIVOT;
		public DoorMaterial material = DoorMaterial.OAK;
		public Direction facing = Direction.NORTH;
		public boolean mirrored;
		public boolean reversed;
		public int width = 2;
		public int height = DoorKind.OLD_HEIGHT;
		public float openness;
	}
}
