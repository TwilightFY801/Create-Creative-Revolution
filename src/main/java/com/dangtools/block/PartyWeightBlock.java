package com.dangtools.block;

import com.dangtools.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 党的分量：封闭黄色方块，可调“重量”。
 * 重量通过方块状态属性 {@link #WEIGHT}（0~100）表达，
 * 再由数据包 data/dangtools/physics_block_properties/party_weight.json 把它映射成
 * Sable（航空学的物理引擎）真正的方块质量：0 ~ 1000（1000 = 基岩的质量）。
 */
public class PartyWeightBlock extends Block implements EntityBlock {

    /** 重量档位 0~1000（数值即质量，1000 = 基岩的质量）。 */
    public static final IntegerProperty WEIGHT = IntegerProperty.create("weight", 0, 1000);

    public PartyWeightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WEIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(WEIGHT, 0);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PartyWeightBlockEntity(Registration.PARTY_WEIGHT_BE.get(), pos, state);
    }

    /** 机主反馈：挖掉会掉成别的方块（继承自 Create 的父类行为），这里直接指定掉自己。 */
    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return java.util.List.of(new net.minecraft.world.item.ItemStack(com.dangtools.Registration.PARTY_WEIGHT_ITEM.get()));
    }
}
