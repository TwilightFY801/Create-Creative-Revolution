package com.dangtools.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 党的分量方块实体：一个“重量”调节面板，<b>面板数值就是质量本身</b>（0 ~ 1000，1000 = 基岩）。
 * <p>
 * Sable（航空学的物理引擎）的方块质量按<b>方块状态</b>读取，没有运行时按方块改质量的接口，
 * 所以做法是：面板改数值 → 同步写进方块状态属性 weight →
 * 由数据包 data/dangtools/physics_block_properties/party_weight.json（weight=N → sable:mass N）变成真实质量。
 */
public class PartyWeightBlockEntity extends SmartBlockEntity {

    /** 重量上限（= 基岩的质量）。 */
    public static final int MAX_WEIGHT = 1000;

    public ScrollValueBehaviour weight;

    public PartyWeightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        weight = new WeightScrollBehaviour(
                Component.translatable("dangtools.weight"),
                this,
                new PartyWeightValueBox())
                .between(0, MAX_WEIGHT)
                .withFormatter(value -> value + " kpg")
                .withCallback(this::applyWeight);
        behaviours.add(weight);
    }

    /** 把面板数值写进方块状态；物理质量由数据包按 weight=数值 覆盖（数值即质量）。 */
    private void applyWeight(Integer value) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(PartyWeightBlock.WEIGHT)) {
            return;
        }
        int clamped = Mth.clamp(value == null ? 0 : value, 0, MAX_WEIGHT);
        if (state.getValue(PartyWeightBlock.WEIGHT) != clamped) {
            level.setBlock(worldPosition, state.setValue(PartyWeightBlock.WEIGHT, clamped), 3);
        }
    }
}
