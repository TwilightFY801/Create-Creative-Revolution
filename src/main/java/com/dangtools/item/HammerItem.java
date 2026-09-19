package com.dangtools.item;

import com.dangtools.tags.DangTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 锤子：在矿洞发现矿物后右键，敲碎矿石并直接掉落矿物（掉落双份）。
 * 仅用铁锭与木棍合成，仅存在于铁制层级。
 */
public class HammerItem extends Item {

    public static final int DURABILITY = 600;
    public static final int ORE_DROPS = 2;

    public HammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // 只对矿物（标签内的矿石块）生效
        if (!state.is(DangTags.ORE_HAMMERABLE)) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel serverLevel) {
            // 以当前工具身份获取掉落物
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null, player, stack);
            // 让方块消失（掉落双份，由下面手动生成，所以 destroyBlock 的 dropBlock 传 false）
            serverLevel.destroyBlock(pos, false);
            if (!drops.isEmpty()) {
                Vec3 center = pos.getCenter().subtract(0, 0.25, 0);
                for (ItemStack drop : drops) {
                    for (int i = 0; i < ORE_DROPS; i++) {
                        ItemEntity entity = new ItemEntity(serverLevel, center.x, center.y, center.z, drop.copy());
                        entity.setDeltaMovement(
                                (level.random.nextDouble() - 0.5) * 0.2,
                                level.random.nextDouble() * 0.2 + 0.1,
                                (level.random.nextDouble() - 0.5) * 0.2);
                        serverLevel.addFreshEntity(entity);
                    }
                }
                // 消耗耐久
                if (player != null && !player.getAbilities().instabuild) {
                    int dmg = stack.getDamageValue() + 1;
                    if (dmg >= stack.getMaxDamage()) {
                        stack.shrink(1);
                    } else {
                        stack.setDamageValue(dmg);
                    }
                }
            }
        }

        if (player != null) {
            player.swing(context.getHand());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
