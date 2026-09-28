package com.moderndoors.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A black aluminum glass panel, one block of a folding panel door. Panels stack into columns, and columns in a straight
 * line from a {@link DoorBaseBlock} slide back into it when the door is used. While folded away a panel is hidden:
 * it stays in place but can't be seen or bumped into, and the base draws it stacked up instead.
 * <p>
 * TOP and BOTTOM say whether the panel has a frame bar along that edge, which it does unless another panel continues
 * the column that way.
 */
public class DoorPanelBlock extends Block {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
	public static final BooleanProperty HIDDEN = BooleanProperty.create("hidden");

	private static final VoxelShape ALONG_X = Block.box(0, 0, 7.25, 16, 16, 8.75);
	private static final VoxelShape ALONG_Z = Block.box(7.25, 0, 0, 8.75, 16, 16);

	public DoorPanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
			.setValue(AXIS, Direction.Axis.X)
			.setValue(TOP, true)
			.setValue(BOTTOM, true)
			.setValue(HIDDEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, TOP, BOTTOM, HIDDEN);
	}

	/** Runs across the player's view, or lines up with a panel or base it's placed against. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction.Axis axis = context.getHorizontalDirection().getClockWise().getAxis();
		BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
		if (against.hasProperty(AXIS) && (against.getBlock() instanceof DoorPanelBlock || against.getBlock() instanceof DoorBaseBlock)) {
			axis = against.getValue(AXIS);
		}
		BlockState state = defaultBlockState().setValue(AXIS, axis);
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		return state
			.setValue(TOP, !continues(state, level.getBlockState(pos.above())))
			.setValue(BOTTOM, !continues(state, level.getBlockState(pos.below())));
	}

	private boolean continues(BlockState state, BlockState neighbour) {
		return neighbour.is(this) && neighbour.getValue(AXIS) == state.getValue(AXIS);
	}

	@Override
	protected BlockState updateShape(
		BlockState state,
		LevelReader level,
		ScheduledTickAccess ticks,
		BlockPos pos,
		Direction directionToNeighbour,
		BlockPos neighbourPos,
		BlockState neighbourState,
		RandomSource random
	) {
		if (directionToNeighbour == Direction.UP) {
			return state.setValue(TOP, !continues(state, neighbourState));
		}
		if (directionToNeighbour == Direction.DOWN) {
			return state.setValue(BOTTOM, !continues(state, neighbourState));
		}
		return state;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		Direction.Axis axis = state.getValue(AXIS);
		// Look along the row both ways for the base this panel belongs to.
		for (Direction toward : new Direction[] {Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
			Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)}) {
			BlockPos check = pos;
			for (int i = 0; i <= DoorBaseBlock.MAX_PANELS; i++) {
				check = check.relative(toward);
				BlockState checked = level.getBlockState(check);
				if (checked.getBlock() instanceof DoorBaseBlock base && checked.getValue(DoorBaseBlock.AXIS) == axis) {
					if (!level.isClientSide()) {
						base.toggle(level, check, checked, toward.getOpposite(), player);
					}
					return InteractionResult.SUCCESS;
				}
				if (!continues(state, checked)) {
					break;
				}
			}
		}
		return InteractionResult.PASS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(HIDDEN)) {
			return Shapes.empty();
		}
		return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(HIDDEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
			default -> state;
		};
	}
}
