package com.dangtools.block;

import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 双轮悬架（单方块方案，机主拍板）。
 * <p>
 * <b>不再往旁边生成第二个 wheel_mount</b>：整个「双轮」由一个方块承载 ——
 * 两个轮子共用同一个方块实体、同一个转速，所以 <b>旋转天然完全同步</b>，
 * 不存在上一版"外轮不在动力学网络里、没有动力，反而比单轮还差"的问题。
 * <p>
 * 物理上按「两个轮胎等效」处理，实现见 {@link DualWheelSuspensionBlockEntity}。
 * 外观/碰撞箱/转向逻辑全部继承 offroad 原版车轮悬架。
 */
public class DualWheelSuspensionBlock extends WheelMountBlock {

    public DualWheelSuspensionBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity> getBlockEntityType() {
        return com.dangtools.Registration.DUAL_WHEEL_SUSPENSION_BE.get();
    }
}
