package com.dangtools.item;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 质量探测器：手持右键方块 -> 在动作栏显示该方块在当前 BlockState 下的 Sable 物理质量。
 * <p>
 * 质量读取走 {@code PhysicsBlockPropertyHelper.getMass(BlockGetter, BlockPos, BlockState)}，
 * 与数据包 physics_block_properties 里配置的 {@code sable:mass} 完全一致。
 */
public class MassScannerItem extends Item {

    public MassScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        double mass;
        try {
            mass = PhysicsBlockPropertyHelper.getMass(level, context.getClickedPos(), state);
        } catch (Throwable t) {
            context.getPlayer().displayClientMessage(
                    Component.literal("[质量探测器] 读取失败: " + t).withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        context.getPlayer().displayClientMessage(
                Component.literal("[质量探测器] " + state.getBlock().getName().getString()
                        + " 质量 = " + String.format("%.3f", mass) + " kpg").withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }
}
