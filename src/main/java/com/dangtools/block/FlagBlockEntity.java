package com.dangtools.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 国旗方块实体：每 80 刻（4 秒）给 64×64×64 范围内的玩家施加满级信标增益。
 */
public class FlagBlockEntity extends BlockEntity {

    private int tick;

    public FlagBlockEntity(BlockEntityType<FlagBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FlagBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (++be.tick % 80 != 0) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        for (Player player : serverLevel.players()) {
            if (Math.abs(player.getX() - cx) > 32.0
                    || Math.abs(player.getY() - cy) > 32.0
                    || Math.abs(player.getZ() - cz) > 32.0) {
                continue;
            }
            // 满级信标增益：速度/急迫/抗性/跳跃/力量 II，生命恢复 I
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 260, 1, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 260, 1, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 260, 1, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 260, 1, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 260, 1, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 260, 0, true, false, true));
        }
    }
}
