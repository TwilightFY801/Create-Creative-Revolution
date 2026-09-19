package com.dangtools.block;

import com.dangtools.lighting.LeverRegistry;
import com.dangtools.lighting.LightRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 照明拉杆的方块实体。它本身几乎不做事，只有一个职责：
 * <b>每 tick 把自己（所在的物理结构 + 位置）登记到 {@link LeverRegistry}</b>，
 * 这样同一载具上的所有灯都能直接读到「总开关拉没拉」。
 * <p>
 * 拉动时调用 {@link #broadcastChange}，让同一物理结构里的灯<b>立刻</b>重算亮度
 * （不然要等它们各自的 tick 周期，手感有一拍延迟）。
 */
public class LightingLeverBlockEntity extends LightPlatformBlockEntity {

    public LightingLeverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    @Override
    public void dangtools$tick() {
        if (level == null || level.isClientSide) {
            return;
        }
        LeverRegistry.register(level, worldPosition);
    }

    /** 拉杆状态变化：让附近的灯立刻刷新一次。 */
    public static void broadcastChange(net.minecraft.world.level.Level level, BlockPos leverPos) {
        if (level == null || level.isClientSide || leverPos == null) {
            return;
        }
        LightRegistry.refreshNear(level, leverPos, 48);
    }

    /** 拉杆被拆掉：把它附近已经生成的大灯光方块整体删掉，避免残留。 */
    public static void releaseLights(net.minecraft.world.level.Level level, BlockPos leverPos) {
        if (level == null || level.isClientSide || leverPos == null) {
            return;
        }
        LightRegistry.releaseGeneratedNear(level, leverPos, 48);
    }
}
