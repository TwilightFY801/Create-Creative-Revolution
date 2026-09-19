package com.dangtools.block;

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 党动力方块实体：沿用创造马达的方块实体（同样的滚动调速、应力与动力学）。
 * 两处重写：
 * 1) 创造马达的 getGeneratedSpeed() 会硬性判断方块必须是 create:creative_motor，这里去掉该判断；
 * 2) 应力容量在 Create 里是按“方块”查表得到的，我们的方块查不到会得到 0（于是到处过载），
 *    所以直接给一个极大的固定容量：4194304 * 2 = 8388608。
 */
public class PartyPowerBlockEntity extends CreativeMotorBlockEntity {

    /** 应力容量：4194304 * 2 = 8388608（机械动力应力几乎无上限）。 */
    public static final float STRESS_CAPACITY = 4194304.0F * 2.0F;

    public PartyPowerBlockEntity(BlockEntityType<PartyPowerBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public float getGeneratedSpeed() {
        // 与创造马达一致：按滚动面板的值输出转速，并转换为朝向。
        return convertToDirection((float) generatedSpeed.getValue(),
                getBlockState().getValue(PartyPowerBlock.FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = STRESS_CAPACITY;
        return STRESS_CAPACITY;
    }
}