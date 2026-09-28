package com.moderndoors.block;

import com.moderndoors.door.DoorKind;
import com.moderndoors.registry.ModBlockEntities;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.slf4j.Logger;

/**
 * Lives in a door's main block and remembers how big the door is. On the client it also eases the door between
 * closed (0) and open (1) for drawing.
 */
public class ModernDoorBlockEntity extends BlockEntity {
	private static final Logger LOGGER = LogUtils.getLogger();

	private int width;
	private int height;
	private float progress;
	private float previousProgress;
	private boolean started;

	public ModernDoorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MODERN_DOOR, pos, state);
		width = state.getBlock() instanceof ModernDoorBlock block ? block.kind().oldWidth() : 2;
		height = DoorKind.OLD_HEIGHT;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public void setSize(int width, int height) {
		this.width = width;
		this.height = height;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		width = Mth.clamp(input.getIntOr("width", width), 1, DoorKind.MAX_WIDTH);
		height = Mth.clamp(input.getIntOr("height", height), 1, DoorKind.MAX_HEIGHT);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("width", width);
		output.putInt("height", height);
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

	public static void clientTick(Level level, BlockPos pos, BlockState state, ModernDoorBlockEntity door) {
		float target = state.getValue(ModernDoorBlock.OPEN) ? 1.0F : 0.0F;
		if (!door.started) {
			// A door that was already open when it came into view doesn't swing open again.
			door.progress = target;
			door.previousProgress = target;
			door.started = true;
			return;
		}
		float speed = state.getBlock() instanceof ModernDoorBlock block ? block.kind().speed() : 0.05F;
		door.previousProgress = door.progress;
		door.progress = target > door.progress ? Math.min(target, door.progress + speed) : Math.max(target, door.progress - speed);
	}

	/** How far open the door looks, eased so it starts and stops gently. */
	public float openness(float partialTicks) {
		float t = Mth.lerp(partialTicks, previousProgress, progress);
		return t * t * (3.0F - 2.0F * t);
	}
}
