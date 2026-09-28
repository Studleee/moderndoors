package com.moderndoors.block;

import java.util.List;

import com.moderndoors.registry.ModBlockEntities;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.slf4j.Logger;

/**
 * Lives in each door base and remembers the panels folded into it: which side they came from and what each looked
 * like, nearest first. On the client it also eases the panels between laid out (0) and folded away (1) for drawing.
 */
public class DoorBaseBlockEntity extends BlockEntity {
	private static final Logger LOGGER = LogUtils.getLogger();

	private Direction toward = Direction.EAST;
	private List<BlockState> panels = List.of();
	private float progress;
	private float previousProgress;
	private boolean started;

	public DoorBaseBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DOOR_BASE, pos, state);
	}

	public Direction toward() {
		return toward;
	}

	public List<BlockState> panels() {
		return panels;
	}

	void setPanels(Direction toward, List<BlockState> panels) {
		this.toward = toward;
		this.panels = List.copyOf(panels);
		changed();
	}

	/** Turns the hidden panels back into visible blocks where they were, and forgets them. */
	void revealPanels() {
		if (level == null) {
			return;
		}
		for (int i = 0; i < panels.size(); i++) {
			BlockPos pos = worldPosition.relative(toward, i + 1);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof DoorPanelBlock && state.getValue(DoorPanelBlock.HIDDEN)) {
				level.setBlock(pos, state.setValue(DoorPanelBlock.HIDDEN, false), Block.UPDATE_ALL);
			}
		}
		panels = List.of();
		changed();
	}

	private void changed() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	/** A base broken while folded puts its panels back rather than losing them. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		revealPanels();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		toward = input.read("toward", Direction.CODEC).orElse(Direction.EAST);
		panels = input.read("panels", BlockState.CODEC.listOf()).orElse(List.of());
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("toward", Direction.CODEC, toward);
		output.store("panels", BlockState.CODEC.listOf(), panels);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			saveAdditional(output);
			return output.buildResult();
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, DoorBaseBlockEntity base) {
		float target = state.getValue(DoorBaseBlock.FOLDED) ? 1.0F : 0.0F;
		if (!base.started) {
			base.progress = target;
			base.previousProgress = target;
			base.started = true;
			return;
		}
		float speed = 1.0F / DoorBaseBlock.SLIDE_TICKS;
		base.previousProgress = base.progress;
		base.progress = target > base.progress ? Math.min(target, base.progress + speed) : Math.max(target, base.progress - speed);
	}

	/** How far folded away the panels look, eased so they start and stop gently. */
	public float folded(float partialTicks) {
		float t = Mth.lerp(partialTicks, previousProgress, progress);
		return t * t * (3.0F - 2.0F * t);
	}
}
