package com.moderndoors.item;

import com.moderndoors.block.ModernDoorBlock;
import com.moderndoors.door.DoorKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * A door, placed at its kind's standard size ({@link DoorKind#OLD_HEIGHT} tall) starting at the clicked spot. Clicking
 * the left half of a block builds the door out to the right, and the other way around; if it doesn't fit that way, it
 * tries the other.
 */
public class ModernDoorItem extends BlockItem {
	public ModernDoorItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext useContext) {
		BlockPlaceContext context = new BlockPlaceContext(useContext);
		ModernDoorBlock door = (ModernDoorBlock) getBlock();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction facing = context.getHorizontalDirection();
		int width = door.kind().oldWidth();
		int height = DoorKind.OLD_HEIGHT;
		Vec3 offset = context.getClickLocation().subtract(Vec3.atCenterOf(pos));
		Direction right = facing.getClockWise();
		DoorHingeSide preferred = offset.x * right.getStepX() + offset.z * right.getStepZ() <= 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
		DoorHingeSide other = preferred == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;

		for (DoorHingeSide hinge : new DoorHingeSide[] {preferred, other}) {
			Direction across = ModernDoorBlock.across(facing, hinge);
			boolean fits = true;
			for (int row = 0; row < height && fits; row++) {
				for (int column = 0; column < width && fits; column++) {
					fits = fits(context, pos.relative(across, column).above(row));
				}
			}
			if (!fits) {
				continue;
			}
			if (!level.isClientSide()) {
				door.fill(level, pos, facing, hinge, width, height);
				SoundType sound = door.defaultBlockState().getSoundType();
				level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
				level.gameEvent(context.getPlayer(), GameEvent.BLOCK_PLACE, pos);
				context.getItemInHand().consume(1, context.getPlayer());
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.FAIL;
	}

	private static boolean fits(BlockPlaceContext context, BlockPos pos) {
		Level level = context.getLevel();
		return level.isInsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && level.getBlockState(pos).canBeReplaced(context);
	}
}
