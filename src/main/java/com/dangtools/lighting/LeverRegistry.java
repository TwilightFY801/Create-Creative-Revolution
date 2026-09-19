package com.dangtools.lighting;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「物理结构 -> 照明拉杆」登记表。
 * <p>
 * 灯需要知道「我所在的这个子世界（载具）的总开关拉没拉」。灯是方块、拉杆也是方块，
 * 两者在<b>同一个子世界</b>里，所以只要记下「每个子世界里的拉杆在哪」，灯就能直接读它的方块状态。
 * <p>
 * 登记时机：
 * <ul>
 *   <li>拉杆方块实体每 tick 自登记（自己所在的 level / worldPosition 都是现成的）；</li>
 *   <li>灯找不到拉杆时也会主动去自己所在子世界里找一次（{@link #findLever}），
 *       防止「先放灯、后放拉杆」或区块重载导致拉杆还没来得及登记。</li>
 * </ul>
 * 用 {@link ConcurrentHashMap} 是因为登记/读取可能发生在不同的 tick 阶段。
 */
public final class LeverRegistry {

    /** level -> (子世界 UUID -> 拉杆位置)。 */
    private static final Map<Level, Map<UUID, BlockPos>> LEVERS = new ConcurrentHashMap<>();

    private LeverRegistry() {}

    /** 拉杆方块实体每 tick 调用：把自己登记进去。 */
    public static void register(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        UUID id = subLevelId(level, pos);
        if (id == null) {
            return;
        }
        Map<UUID, BlockPos> byId = LEVERS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        BlockPos previous = byId.put(id, pos.immutable());
        if (previous != null && !previous.equals(pos)) {
            // 同一个子世界里有第二个拉杆：以最后登记的那个为准（不报错，保持简单）
            byId.put(id, pos.immutable());
        }
    }

    /** 拉杆被拆掉时调用。 */
    public static void unregister(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        Map<UUID, BlockPos> byId = LEVERS.get(level);
        if (byId == null) {
            return;
        }
        byId.entrySet().removeIf(e -> e.getValue().equals(pos));
    }

    /** 某个方块所在的物理结构里，拉杆是否处于「开启」状态。没有拉杆 = 关。 */
    public static boolean isLitAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        UUID id = subLevelId(level, pos);
        if (id == null) {
            return false;
        }
        BlockPos lever = leverPos(level, id);
        if (lever == null) {
            return false;
        }
        return isLeverLit(level, lever);
    }

    /** 某个物理结构里拉杆的位置；找不到返回 null。 */
    public static BlockPos leverPos(Level level, UUID subLevelId) {
        Map<UUID, BlockPos> byId = LEVERS.get(level);
        return byId == null ? null : byId.get(subLevelId);
    }

    /** 读拉杆方块的 LIT 状态（用方块状态，不需要方块实体）。 */
    public static boolean isLeverLit(Level level, BlockPos leverPos) {
        BlockState state = level.getBlockState(leverPos);
        return state.getBlock() instanceof LightingLeverBlock && state.getValue(LightingLeverBlock.LIT);
    }

    /** 子世界 UUID；不在任何子世界里返回 null。 */
    public static UUID subLevelId(Level level, BlockPos pos) {
        try {
            SubLevel sub = Sable.HELPER.getContaining(level, pos);
            return sub == null ? null : sub.getUniqueId();
        } catch (Throwable t) {
            return null;
        }
    }

    /** level 卸载时清掉记录，避免内存泄漏。 */
    public static void clearLevel(Level level) {
        LEVERS.remove(level);
    }

    /**
     * 兜底查找：在方块所在子世界里扫一圈找拉杆。
     * 只在「登记表里没有」时调用，而且灯的 tick 间隔不短，所以开销可以接受。
     * 扫描范围取子世界登记位置周围一个有限立方体（拉杆通常装在驾驶位附近）。
     */
    public static BlockPos findLever(Level level, BlockPos around, int radius) {
        if (level == null || around == null) {
            return null;
        }
        Map<UUID, BlockPos> byId = LEVERS.get(level);
        UUID id = subLevelId(level, around);
        if (id != null && byId != null && byId.containsKey(id)) {
            return byId.get(id);
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                    if (level.isLoaded(cursor) && level.getBlockState(cursor).getBlock() instanceof LightingLeverBlock) {
                        BlockPos found = cursor.immutable();
                        register(level, found);
                        return found;
                    }
                }
            }
        }
        return null;
    }

    /** 调试/统计用。 */
    public static Map<UUID, BlockPos> snapshot(Level level) {
        Map<UUID, BlockPos> byId = LEVERS.get(level);
        return byId == null ? Map.of() : new HashMap<>(byId);
    }
}
