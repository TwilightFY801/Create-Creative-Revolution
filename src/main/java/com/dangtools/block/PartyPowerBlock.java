package com.dangtools.block;

import com.dangtools.Registration;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 党的动力：创造马达的复制品，方块模型/渲染/动力学/滚动调速全部与创造马达一致，
 * 唯一区别是机壳贴图由紫色换成了红色。
 */
public class PartyPowerBlock extends CreativeMotorBlock {

    public PartyPowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends PartyPowerBlockEntity> getBlockEntityType() {
        return Registration.PARTY_POWER_BE.get();
    }

    /** 机主反馈：挖掉会掉成别的方块（继承自 Create 的父类行为），这里直接指定掉自己。 */
    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return java.util.List.of(new net.minecraft.world.item.ItemStack(com.dangtools.Registration.PARTY_POWER_ITEM.get()));
    }
}
