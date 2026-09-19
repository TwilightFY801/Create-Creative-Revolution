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
 * 微调配重块方块实体：一个 0~100 的精细重量面板。
 * <p>
 * <b>为什么不复用 {@code WeightScrollBehaviour}</b>：那个类是为了把 1000 档压到 300 格面板才写的
 * （1 格 ≈ 3.33）。这里最大值只有 100，Create <b>原生</b>的 {@link ScrollValueBehaviour}
 * 直接就能给出 101 格、<b>1 格 = 1 kpg</b>，语义最准确，所以用原生实现。
 * <p>
 * 面板改数值 → 写进方块状态属性 weight(0~100) → 数据包按 weight=N 给出质量 N。
 */
public class TrimBallastBlockEntity extends SmartBlockEntity {

    /** 重量上限（1 格 = 1 kpg，所以面板 101 格）。 */
    public static final int MAX_WEIGHT = 100;

    public ScrollValueBehaviour weight;

    public TrimBallastBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        weight = new ScrollValueBehaviour(
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
        if (!state.hasProperty(TrimBallastBlock.WEIGHT)) {
            return;
        }
        int clamped = Mth.clamp(value == null ? 0 : value, 0, MAX_WEIGHT);
        if (state.getValue(TrimBallastBlock.WEIGHT) != clamped) {
            level.setBlock(worldPosition, state.setValue(TrimBallastBlock.WEIGHT, clamped), 3);
        }
    }
}
