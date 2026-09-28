package com.moderndoors.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.moderndoors.door.DoorKind;
import com.moderndoors.door.DoorMaterial;
import com.moderndoors.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A big modern door, several blocks wide and tall. Every block is a part of the same door, numbered by
 * column (0 at the hinge side) and row (0 at the bottom). The hinge-side bottom block is the main part: it drops the
 * door, and its block entity remembers the door's size and draws the whole moving door. The blocks themselves are
 * invisible and only give the door its collision.
 * <p>
 * FACING is the way the player looked when placing it. "Front" is the side they stood on.
 */
public class ModernDoorBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** Which way it opens: false swings or folds away from the front, true toward it. Set to open away from whoever opened it. */
	public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");
	public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, DoorKind.MAX_WIDTH - 1);
	public static final IntegerProperty ROW = IntegerProperty.create("row", 0, DoorKind.MAX_HEIGHT - 1);

	private record ShapeKey(BlockState state, int width) {
	}

	private final DoorKind kind;
	private final DoorMaterial material;
	private final Map<ShapeKey, VoxelShape> shapes = new ConcurrentHashMap<>();

	public ModernDoorBlock(DoorKind kind, DoorMaterial material, Properties properties) {
		super(properties);
		this.kind = kind;
		this.material = material;
		registerDefaultState(stateDefinition.any()
			.setValue(FACING, Direction.NORTH)
			.setValue(HINGE, DoorHingeSide.LEFT)
			.setValue(OPEN, false)
			.setValue(POWERED, false)
			.setValue(REVERSED, false)
			.setValue(COLUMN, 0)
			.setValue(ROW, 0));
	}

	public DoorKind kind() {
		return kind;
	}

	public DoorMaterial material() {
		return material;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HINGE, OPEN, POWERED, REVERSED, COLUMN, ROW);
	}

	// ---- Where the parts are ----

	public static boolean isMain(BlockState state) {
		return state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0;
	}

	/** The direction the door extends in from its hinge side. */
	public static Direction across(Direction facing, DoorHingeSide hinge) {
		return hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
	}

	private static Direction across(BlockState state) {
		return across(state.getValue(FACING), state.getValue(HINGE));
	}

	public static BlockPos mainPos(BlockState state, BlockPos pos) {
		return pos.relative(across(state), -state.getValue(COLUMN)).below(state.getValue(ROW));
	}

	/** Whether the block at pos is the given column and row of the same door that {@code part} belongs to. */
	private boolean isPartAt(BlockGetter level, BlockPos pos, BlockState part, int column, int row) {
		BlockState state = level.getBlockState(pos);
		return state.is(this)
			&& state.getValue(FACING) == part.getValue(FACING)
			&& state.getValue(HINGE) == part.getValue(HINGE)
			&& state.getValue(COLUMN) == column
			&& state.getValue(ROW) == row;
	}

	/**
	 * Every block still standing of the door that the part at pos belongs to. Each block knows its own column and
	 * row, so a neighboring door of the same kind can never be mistaken for part of this one.
	 */
	private List<BlockPos> parts(BlockGetter level, BlockState state, BlockPos pos) {
		BlockPos main = mainPos(state, pos);
		Direction across = across(state);
		List<BlockPos> parts = new ArrayList<>();
		for (int row = 0; row < DoorKind.MAX_HEIGHT; row++) {
			for (int column = 0; column < DoorKind.MAX_WIDTH; column++) {
				BlockPos part = main.relative(across, column).above(row);
				if (isPartAt(level, part, state, column, row)) {
					parts.add(part);
				}
			}
		}
		return parts;
	}

	/** Width and height of the door, from its main block. Doors from before sizes were saved use their old size. */
	private int width(BlockGetter level, BlockState state, BlockPos pos) {
		return level.getBlockEntity(mainPos(state, pos)) instanceof ModernDoorBlockEntity door ? door.width() : kind.oldWidth();
	}

	// ---- Placing ----

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return null;
	}

	/** Builds the door out from its hinge-side bottom corner. */
	public void fill(Level level, BlockPos main, Direction facing, DoorHingeSide hinge, int width, int height) {
		Direction across = across(facing, hinge);
		boolean powered = false;
		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				powered |= level.hasNeighborSignal(main.relative(across, column).above(row));
			}
		}
		BlockState state = defaultBlockState()
			.setValue(FACING, facing)
			.setValue(HINGE, hinge)
			.setValue(POWERED, powered)
			.setValue(OPEN, powered);
		level.setBlock(main, state, Block.UPDATE_ALL);
		if (level.getBlockEntity(main) instanceof ModernDoorBlockEntity door) {
			door.setSize(width, height);
		}
		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				if (column != 0 || row != 0) {
					level.setBlock(main.relative(across, column).above(row), state.setValue(COLUMN, column).setValue(ROW, row), Block.UPDATE_ALL);
				}
			}
		}
	}

	// ---- Breaking: the whole door goes at once and drops once ----

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// In creative, take out the main part first so nothing drops the door.
		if (!level.isClientSide() && player.preventsBlockDrops() && !isMain(state)) {
			BlockPos main = mainPos(state, pos);
			BlockState mainState = level.getBlockState(main);
			if (isPartAt(level, main, state, 0, 0)) {
				level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				level.levelEvent(player, 2001, main, Block.getId(mainState));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * However a part goes (mined, blown up, popped by a piston), the rest of the door goes with it. Only the main
	 * part's loot table drops the door, so if the main part is still there it's broken properly, which drops the
	 * door and brings us back here to clear the rest without drops.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		BlockPos main = mainPos(state, pos);
		if (!main.equals(pos) && isPartAt(level, main, state, 0, 0)) {
			level.destroyBlock(main, true);
			return;
		}
		for (BlockPos part : parts(level, state, pos)) {
			level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	// ---- Opening ----

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		boolean open = !state.getValue(OPEN);
		boolean reversed = state.getValue(REVERSED);
		if (open) {
			// Open away from the player: if they're on the front side, swing toward the back.
			Direction front = state.getValue(FACING).getOpposite();
			Vec3 toPlayer = player.position().subtract(Vec3.atCenterOf(pos));
			reversed = toPlayer.x * front.getStepX() + toPlayer.z * front.getStepZ() < 0;
		}
		setOpen(player, level, state, pos, open, reversed, state.getValue(POWERED));
		return InteractionResult.SUCCESS;
	}

	private void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open, boolean reversed, boolean powered) {
		if (state.getValue(OPEN) != open) {
			BlockPos main = mainPos(state, pos);
			level.playSound(entity, main.above(), open ? material.openSound() : material.closeSound(), SoundSource.BLOCKS, 1.0F,
				material.soundPitch() + level.getRandom().nextFloat() * 0.1F);
			level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, main);
		}
		for (BlockPos part : parts(level, state, pos)) {
			BlockState partState = level.getBlockState(part);
			level.setBlock(part, partState.setValue(OPEN, open).setValue(REVERSED, reversed).setValue(POWERED, powered), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (level.isClientSide() || defaultBlockState().is(block)) {
			return;
		}
		boolean signal = parts(level, state, pos).stream().anyMatch(level::hasNeighborSignal);
		if (signal != state.getValue(POWERED)) {
			setOpen(null, level, state, pos, signal, state.getValue(REVERSED), signal);
		}
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return switch (type) {
			case LAND, AIR -> state.getValue(OPEN);
			case WATER -> false;
		};
	}

	// ---- Shape ----

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int width = kind == DoorKind.PIVOT ? 0 : width(level, state, pos);
		return shapes.computeIfAbsent(new ShapeKey(state, width), key -> makeShape(key.state(), key.width()));
	}

	/**
	 * The collision for one part, worked out in "door space" (16 pixels to a block): x runs from the hinge side
	 * of this block (0) across the door (16), z from the back (0) to the front (16). An open door only collides
	 * inside its own blocks, where it's gathered at the hinge side.
	 */
	private VoxelShape makeShape(BlockState state, int width) {
		int column = state.getValue(COLUMN);
		boolean open = state.getValue(OPEN);
		boolean reversed = state.getValue(REVERSED);
		List<double[]> boxes = new ArrayList<>();
		switch (kind) {
			case PIVOT -> {
				if (!open) {
					boxes.add(new double[] {0, 16, 6.5, 9.5});
				} else if (column == 0) {
					boxes.add(new double[] {6.5, 9.5, 0, 16});
				}
			}
			case FOLDING -> {
				if (!open) {
					boxes.add(new double[] {0, 16, 7, 9});
				} else if (column == 0) {
					double stack = Math.min(16, 2 + width * 1.7);
					boxes.add(reversed ? new double[] {0, stack, 8, 16} : new double[] {0, stack, 0, 8});
				}
			}
			case SLIDING -> {
				// The fixed panel covers the hinge-side columns; the sliding one covers the rest, and slides back over
				// the fixed one when open.
				int fixed = width / 2;
				int sliding = width - fixed;
				if (column < fixed) {
					boxes.add(open ? new double[] {0, 16, 6, 10} : new double[] {0, 16, 8, 10});
				} else if (!open || column < sliding) {
					boxes.add(new double[] {0, 16, 6, 8});
				}
			}
		}
		VoxelShape shape = Shapes.empty();
		for (double[] box : boxes) {
			shape = Shapes.or(shape, toWorld(state, box[0], box[1], box[2], box[3]));
		}
		return shape;
	}

	private static VoxelShape toWorld(BlockState state, double x0, double x1, double z0, double z1) {
		Direction facing = state.getValue(FACING);
		// Measured toward the player's right instead of from the hinge side.
		double r0 = x0;
		double r1 = x1;
		if (state.getValue(HINGE) == DoorHingeSide.RIGHT) {
			r0 = 16 - x1;
			r1 = 16 - x0;
		}
		double[] a = toWorld(facing, r0, z0);
		double[] b = toWorld(facing, r1, z1);
		return Block.box(Math.min(a[0], b[0]), 0, Math.min(a[1], b[1]), Math.max(a[0], b[0]), 16, Math.max(a[1], b[1]));
	}

	/** Door space (toward the player's right, toward the front) to world x and z within the block. */
	private static double[] toWorld(Direction facing, double right, double front) {
		return switch (facing) {
			case SOUTH -> new double[] {16 - right, 16 - front};
			case EAST -> new double[] {16 - front, right};
			case WEST -> new double[] {front, 16 - right};
			default -> new double[] {right, front};
		};
	}

	// ---- Rotating and mirroring (structure blocks) ----

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return mirror == Mirror.NONE ? state : state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(HINGE);
	}

	// ---- The block entity that remembers the size and draws the door ----

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return isMain(state) ? new ModernDoorBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() && isMain(state)
			? createTickerHelper(type, ModBlockEntities.MODERN_DOOR, ModernDoorBlockEntity::clientTick)
			: null;
	}
}
