package com.moderndoors.item;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.moderndoors.block.ModernDoorBlock;
import com.moderndoors.door.DoorKind;
import com.moderndoors.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * A door, ready to fit. Using it on a door frame fills the empty rectangle the frame blocks go around, whatever its
 * size (up to {@link DoorKind#MAX_WIDTH} by {@link DoorKind#MAX_HEIGHT}). The door hinges on whichever side of the
 * opening the player clicked nearer to.
 */
public class ModernDoorItem extends BlockItem {
	public ModernDoorItem(Block block, Properties properties) {
		super(block, properties);
	}

	/** An empty rectangle: its bottom corner toward -right, how big it is, and which way "right" is. */
	private record Opening(BlockPos corner, int width, int height, Direction right) {
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		BlockPos clicked = context.getClickedPos();
		ModernDoorBlock door = (ModernDoorBlock) getBlock();
		if (!ModBlocks.isFrame(level.getBlockState(clicked))) {
			tell(level, player, Component.translatable("message.moderndoors.use_on_frame"));
			return InteractionResult.FAIL;
		}

		// The opening usually runs across the player's view; failing that, try along it.
		Direction look = context.getHorizontalDirection();
		Direction facing = look;
		Opening opening = find(level, clicked, look.getClockWise());
		if (opening == null) {
			facing = look.getClockWise();
			opening = find(level, clicked, facing.getClockWise());
		}
		if (opening == null) {
			tell(level, player, Component.translatable("message.moderndoors.no_opening", DoorKind.MAX_WIDTH, DoorKind.MAX_HEIGHT));
			return InteractionResult.FAIL;
		}
		DoorKind kind = door.kind();
		if (opening.width() < kind.minWidth() || opening.height() < DoorKind.MIN_HEIGHT) {
			tell(level, player, Component.translatable("message.moderndoors.too_small", kind.minWidth(), DoorKind.MIN_HEIGHT));
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide()) {
			Direction right = opening.right();
			double middle = along(Vec3.atCenterOf(opening.corner()), right) + (opening.width() - 1) / 2.0;
			DoorHingeSide hinge = along(context.getClickLocation(), right) <= middle ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
			BlockPos main = hinge == DoorHingeSide.LEFT ? opening.corner() : opening.corner().relative(right, opening.width() - 1);
			door.fill(level, main, facing, hinge, opening.width(), opening.height());

			BlockPos middlePos = opening.corner().relative(right, opening.width() / 2).above(opening.height() / 2);
			SoundType sound = door.defaultBlockState().getSoundType();
			level.playSound(null, middlePos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			level.gameEvent(player, GameEvent.BLOCK_PLACE, main);
			context.getItemInHand().consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	private static double along(Vec3 point, Direction right) {
		return point.x * right.getStepX() + point.z * right.getStepZ();
	}

	private static void tell(Level level, @Nullable Player player, Component message) {
		if (!level.isClientSide() && player != null) {
			player.sendOverlayMessage(message);
		}
	}

	/**
	 * Looks for an empty rectangle next to the frame block, in the upright plane that runs along {@code right}, whose
	 * every edge is lined with frame blocks. The corners don't need frames.
	 */
	private static @Nullable Opening find(Level level, BlockPos frame, Direction right) {
		for (BlockPos start : new BlockPos[] {frame.relative(right), frame.relative(right.getOpposite()), frame.above(), frame.below()}) {
			Opening opening = fill(level, frame, start, right);
			if (opening != null) {
				return opening;
			}
		}
		return null;
	}

	private static @Nullable Opening fill(Level level, BlockPos frame, BlockPos start, Direction right) {
		if (!isEmpty(level, start)) {
			return null;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		int minA = offset(frame, start, right);
		int maxA = minA;
		int minY = start.getY();
		int maxY = minY;
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (BlockPos next : new BlockPos[] {pos.relative(right), pos.relative(right.getOpposite()), pos.above(), pos.below()}) {
				if (seen.contains(next) || !isEmpty(level, next)) {
					continue;
				}
				int a = offset(frame, next, right);
				minA = Math.min(minA, a);
				maxA = Math.max(maxA, a);
				minY = Math.min(minY, next.getY());
				maxY = Math.max(maxY, next.getY());
				if (maxA - minA >= DoorKind.MAX_WIDTH || maxY - minY >= DoorKind.MAX_HEIGHT) {
					return null;
				}
				seen.add(next);
				queue.add(next);
			}
		}
		int width = maxA - minA + 1;
		int height = maxY - minY + 1;
		if (seen.size() != width * height) {
			return null;
		}
		BlockPos corner = new BlockPos(frame.getX(), minY, frame.getZ()).relative(right, minA);
		for (int a = 0; a < width; a++) {
			if (!isFrame(level, corner.relative(right, a).below()) || !isFrame(level, corner.relative(right, a).above(height))) {
				return null;
			}
		}
		for (int y = 0; y < height; y++) {
			if (!isFrame(level, corner.relative(right, -1).above(y)) || !isFrame(level, corner.relative(right, width).above(y))) {
				return null;
			}
		}
		return new Opening(corner, width, height, right);
	}

	/** How many blocks along {@code right} pos is from the frame block. */
	private static int offset(BlockPos frame, BlockPos pos, Direction right) {
		return (pos.getX() - frame.getX()) * right.getStepX() + (pos.getZ() - frame.getZ()) * right.getStepZ();
	}

	private static boolean isEmpty(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return level.isInsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && state.canBeReplaced();
	}

	private static boolean isFrame(Level level, BlockPos pos) {
		return ModBlocks.isFrame(level.getBlockState(pos));
	}
}
