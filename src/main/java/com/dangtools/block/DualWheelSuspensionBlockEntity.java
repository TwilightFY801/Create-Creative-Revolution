package com.dangtools.block;

import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 双轮悬架的方块实体（单方块方案）�? * <p>
 * <b>旋转同步</b>：整个双轮只有这一个方块实体、一个转速，所以两个轮子的转动
 * <b>天然完全一�?/b>，不需要任何跨方块同步�? * <p>
 * <b>物理 = 与原版车轮悬架完全一�?/b>（机主实测反馈后修订）：
 * 之前这里重写�?{@code sable$physicsTick} 并把父类的物理计�?b>跑两�?/b>�? * 想让双轮等效于两个轮�?—�?结果轮胎�?摩擦�?b>翻�?/b>�? * 车辆放在地面上会<b>弹起来、乱�?/b>（机主描述为"明显的碰撞箱问题"）�? * <p>
 * 现在<b>完全不再重写物理回调</b>，一切属性（碰撞箱、接触、力、转速、悬挂行程）
 * 都由父类 {@link WheelMountBlockEntity} 原样执行 —�?也就�?复制原版的车轮悬架属性，
 * 只是在这基础上多显示一个轮�?。多出来的那个轮�?b>纯外�?/b>（由
 * {@code DualWheelSuspensionRenderer} 多画一遍渲染），不参与物理�? */
public class DualWheelSuspensionBlockEntity extends WheelMountBlockEntity {

    public DualWheelSuspensionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 对外暴露父类�?{@code protected getLerpedYaw(double)}�?     * <p>
     * 渲染器（{@code DualWheelSuspensionRenderer}）不�?WheelMountBlockEntity 的子类，
     * 拿不到这�?protected 方法，但第二个轮胎的偏航必须与第一个完全同步，所以在这里开一个口子�?     */
    public double dangtools$getLerpedYaw(double partialTick) {
        return super.getLerpedYaw(partialTick);
    }
}
