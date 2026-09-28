package com.moderndoors.block;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.moderndoors.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A slim black aluminum post that {@link DoorPanelBlock}s fold into. Bases stack into a column as tall as the door
 * (up to {@link #MAX_HEIGHT}); each base handles the panels in its own row, up to {@link #MAX_PANELS} in a straight
 * line from it. Using the base or any of its panels, or powering it, folds every row in the column at once: the
 * panels slide back and stack up in the base's block, and slide back out when used again.
 */
public class DoorBaseBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty FOLDED = BooleanProperty.create("folded");
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final int MAX_PANELS = 8;
	public static final int MAX_HEIGHT = 6;
	/** How long the panels take to slide, in ticks. */
	public static final int SLIDE_TICKS = 20;

	private static final VoxelShape POST = Block.box(6, 0, 6, 10, 16, 10);

	public DoorBaseBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(FOLDED, false).setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, FOLDED, POWERED);
	}

	/** Runs across the player's view, or lines up with a base below or a panel it's placed against. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction.Axis axis = context.getHorizontalDirection().getClockWise().getAxis();
		BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
		if (against.hasProperty(AXIS) && (against.getBlock() instanceof DoorPanelBlock || against.getBlock() instanceof DoorBaseBlock)) {
			axis = against.getValue(AXIS);
		}
		BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
		if (below.is(this)) {
			return defaultBlockState().setValue(AXIS, below.getValue(AXIS)).setValue(FOLDED, below.getValue(FOLDED)).setValue(POWERED, below.getValue(POWERED));
		}
		return defaultBlockState().setValue(AXIS, axis);
	}

	// ---- Folding ----

	/** The bases stacked in the same column as this one, bottom first. */
	private List<BlockPos> column(Level level, BlockPos pos, BlockState state) {
		BlockPos bottom = pos;
		for (int i = 0; i < MAX_HEIGHT && isColumn(level.getBlockState(bottom.below()), state); i++) {
			bottom = bottom.below();
		}
		List<BlockPos> column = new ArrayList<>();
		for (BlockPos check = bottom; column.size() < MAX_HEIGHT && isColumn(level.getBlockState(check), state); check = check.above()) {
			column.add(check);
		}
		return column;
	}

	private boolean isColumn(BlockState other, BlockState state) {
		return other.is(this) && other.getValue(AXIS) == state.getValue(AXIS);
	}

	/** The panels in a straight line from pos toward {@code toward}, nearest first. */
	static List<BlockPos> panels(Level level, BlockPos pos, Direction.Axis axis, Direction toward) {
		List<BlockPos> panels = new ArrayList<>();
		for (int i = 1; i <= MAX_PANELS; i++) {
			BlockPos check = pos.relative(toward, i);
			BlockState state = level.getBlockState(check);
			if (!(state.getBlock() instanceof DoorPanelBlock) || state.getValue(DoorPanelBlock.AXIS) != axis) {
				break;
			}
			panels.add(check);
		}
		return panels;
	}

	/**
	 * Folds or unfolds the whole column. {@code toward} is the side the panels are on, or null to work it out from
	 * whichever side has panels.
	 */
	public void toggle(Level level, BlockPos pos, BlockState state, @Nullable Direction toward, @Nullable Entity entity) {
		setFolded(level, pos, state, !state.getValue(FOLDED), toward, entity, state.getValue(POWERED));
	}

	private void setFolded(Level level, BlockPos pos, BlockState state, boolean folded, @Nullable Direction toward, @Nullable Entity entity, boolean powered) {
		List<BlockPos> column = column(level, pos, state);
		Direction.Axis axis = state.getValue(AXIS);
		if (folded && toward == null) {
			toward = sideWithPanels(level, column, axis);
			if (toward == null) {
				return;
			}
		}
		for (BlockPos basePos : column) {
			BlockState baseState = level.getBlockState(basePos);
			if (!(level.getBlockEntity(basePos) instanceof DoorBaseBlockEntity base)) {
				continue;
			}
			if (folded && !baseState.getValue(FOLDED)) {
				List<BlockPos> panels = panels(level, basePos, axis, toward);
				List<BlockState> states = new ArrayList<>();
				for (BlockPos panel : panels) {
					BlockState panelState = level.getBlockState(panel).setValue(DoorPanelBlock.HIDDEN, false);
					states.add(panelState);
					level.setBlock(panel, panelState.setValue(DoorPanelBlock.HIDDEN, true), Block.UPDATE_ALL);
				}
				base.setPanels(toward, states);
			} else if (!folded && baseState.getValue(FOLDED)) {
				level.scheduleTick(basePos, this, SLIDE_TICKS);
			}
			level.setBlock(basePos, baseState.setValue(FOLDED, folded).setValue(POWERED, powered), Block.UPDATE_ALL);
		}
		if (!column.isEmpty() && folded != state.getValue(FOLDED)) {
			BlockPos middle = column.get(column.size() / 2);
			level.playSound(null, middle, folded ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.2F);
			level.playSound(null, middle, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4F, 1.6F);
			level.gameEvent(entity, folded ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, middle);
		}
	}

	private static @Nullable Direction sideWithPanels(Level level, List<BlockPos> column, Direction.Axis axis) {
		for (Direction toward : new Direction[] {Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
			Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)}) {
			for (BlockPos basePos : column) {
				if (!panels(level, basePos, axis, toward).isEmpty()) {
					return toward;
				}
			}
		}
		return null;
	}

	/** Once the panels have slid back out, they become real blocks again. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(FOLDED) && level.getBlockEntity(pos) instanceof DoorBaseBlockEntity base) {
			base.revealPanels();
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide()) {
			toggle(level, pos, state, null, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level.isClientSide() || block == this) {
			return;
		}
		boolean signal = column(level, pos, state).stream().anyMatch(level::hasNeighborSignal);
		if (signal != state.getValue(POWERED)) {
			setFolded(level, pos, state, signal, null, null, signal);
		}
	}

	// ---- Shape ----

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FOLDED) ? Shapes.block() : POST;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
			default -> state;
		};
	}

	// ---- The block entity that remembers and draws the folded panels ----

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DoorBaseBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, ModBlockEntities.DOOR_BASE, DoorBaseBlockEntity::clientTick) : null;
	}
}
