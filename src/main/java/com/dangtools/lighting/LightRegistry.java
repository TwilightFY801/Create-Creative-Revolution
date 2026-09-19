package com.dangtools.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 灯具索引：记录「哪些方块位置上有本模组的灯」。
 * <p>
 * 存在的理由：拉杆一拉动，需要立刻让同一物理结构里的所有灯重新算一次亮度
 * （否则要等它们各自的 tick 周期，手感会有一拍延迟）。
 * 遍历一个 33³ 的立方体去找方块实体太贵，所以改成「灯自己登记位置」，
 * 需要时直接遍历这份小列表。区块卸载/灯被拆时自动移除。
 */
public final class LightRegistry {

    private static final List<BlockPos> POSITIONS = new ArrayList<>();

    private LightRegistry() {}

    public static synchronized void register(BlockPos pos) {
        if (pos == null) {
            return;
        }
        BlockPos immutable = pos.immutable();
        if (!POSITIONS.contains(immutable)) {
            POSITIONS.add(immutable);
        }
    }

    public static synchronized void unregister(BlockPos pos) {
        POSITIONS.remove(pos);
    }

    /** 对每个登记位置尝试刷新一次（找不到方块实体或已卸载就跳过并顺手清理）。 */
    public static void refreshAll(Level level) {
        List<BlockPos> snapshot;
        synchronized (LightRegistry.class) {
            snapshot = new ArrayList<>(POSITIONS);
        }
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos pos : snapshot) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DangLightBlockEntity light) {
                light.dangtools$tick();
            } else {
                stale.add(pos);
            }
        }
        if (!stale.isEmpty()) {
            synchronized (LightRegistry.class) {
                POSITIONS.removeAll(stale);
            }
        }
    }

    /** 让指定位置附近的灯刷新（拉杆就在载具上，所以按距离筛一下足够）。 */
    public static void refreshNear(Level level, BlockPos leverPos, int radius) {
        List<BlockPos> snapshot;
        synchronized (LightRegistry.class) {
            snapshot = new ArrayList<>(POSITIONS);
        }
        List<BlockPos> stale = new ArrayList<>();
        long radiusSq = (long) radius * radius;
        for (BlockPos pos : snapshot) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (pos.distSqr(leverPos) > radiusSq) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DangLightBlockEntity light) {
                light.dangtools$tick();
            } else {
                stale.add(pos);
            }
        }
        if (!stale.isEmpty()) {
            synchronized (LightRegistry.class) {
                POSITIONS.removeAll(stale);
            }
        }
    }

    public static synchronized int size() {
        return POSITIONS.size();
    }

    /** 让指定位置附近的大灯把已生成的光方块整体删掉（拉杆被拆时用）。 */
    public static void releaseGeneratedNear(Level level, BlockPos leverPos, int radius) {
        List<BlockPos> snapshot;
        synchronized (LightRegistry.class) {
            snapshot = new ArrayList<>(POSITIONS);
        }
        long radiusSq = (long) radius * radius;
        for (BlockPos pos : snapshot) {
            if (!level.isLoaded(pos) || pos.distSqr(leverPos) > radiusSq) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DangLightBlockEntity light) {
                light.releaseGeneratedLights();
            }
        }
    }
}
