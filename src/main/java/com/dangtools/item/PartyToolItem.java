package com.dangtools.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 来自伟大中国共产党的组合工具（镰刀 + 锤子的结合体）。
 * 攻击伤害 9999，不掉耐久，挖掘方块秒挖，矿石掉落 x3，农作物掉落 x3。
 */
public class PartyToolItem extends Item {

    public PartyToolItem(Properties properties) {
        super(properties);
    }

    /** 秒挖：极高的挖掘速度，配合正确的采集工具判定满足即可瞬间破坏一切。 */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 64.0F;
    }

    /** 视为对任何方块都是“正确工具”，保证掉落物不被扣除。 */
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }

    /** 不掉耐久。 */
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        return true;
    }

    /** 攻击不掉耐久。 */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    /** 控制台防误用提醒（可选）。 */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }
}
