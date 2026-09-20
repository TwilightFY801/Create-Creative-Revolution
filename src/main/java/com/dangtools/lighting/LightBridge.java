package com.dangtools.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 大灯"灯光桥"：把光锥的亮度<b>直接注入主世界的光照引擎</b>，而不是真的去增删上万个
 * {@code minecraft:light} 方块。
 *
 * <h2>为什么这么做（机主的思路）</h2>
 * 之前有两种做法，各有致命问题：
 * <ul>
 *   <li><b>在主世界放光方块</b>：能照亮主世界，但载具一动就要"整片删 3 万个 + 整片放 3 万个"，
 *       既有明显割裂感，帧率也会掉到 30 左右；</li>
 *   <li><b>在 plot（子世界）里放光方块</b>：零割裂、性能好，但<b>子世界的光照不照主世界</b>
 *       （两套 Level 各有各的光照引擎，MC 不会跨 Level 传播）。</li>
 * </ul>
 * 所以干脆绕开"放方块"这条路：光锥只在<b>结构坐标系</b>里算（跟着载具走、天然零割裂），
 * 然后把算出来的亮度用 {@link LevelLightEngine#queueSectionData} 写进<b>主世界</b>对应区块段的
 * 光照数据里（nibble 表），移走时再把备份还原。
 *
 * <h2>安全要点</h2>
 * {@code queueSectionData} 是<b>覆盖式</b>的，所以每个被我们碰过的区块段都必须：
 * <ol>
 *   <li>写入前<b>备份</b>原版数据（否则会污染原版光照）；</li>
 *   <li>写入时与备份<b>逐格取最大值</b>（我们只加光、不挡光）；</li>
 *   <li>移走/关灯时把备份<b>原样写回</b>。</li>
 * </ol>
 */
public final class LightBridge {

    /** 每个主世界 -> 我们改过的区块段（section -> 原版数据备份）。 */
    private static final Map<ServerLevel, Map<Long, DataLayer>> BACKUPS = new HashMap<>();

    private LightBridge() {}

    /**
     * 把一批「世界坐标 + 亮度」写进主世界的光照引擎。
     *
     * @param entries 世界坐标与对应的亮度（0~15）
     */
    public static void apply(ServerLevel level, java.util.List<Entry> entries) {
        if (level == null || entries.isEmpty()) {
            clear(level);
            return;
        }

        LevelLightEngine lightEngine = level.getLightEngine();
        Map<Long, DataLayer> backups = backupsOf(level);

        // 1) 按区块段分组
        Map<Long, DataLayer> pending = new HashMap<>();
        for (Entry e : entries) {
            if (e.level <= 0) {
                continue;
            }
            SectionPos sp = SectionPos.of(e.pos);
            long key = sp.asLong();
            DataLayer layer = pending.computeIfAbsent(key, k -> {
                // 以"原版当前数据（或空表）"为底，逐格取最大值写入
                DataLayer base = readLayer(level, lightEngine, sp);
                return cloneLayer(base);
            });
            int lx = SectionPos.sectionRelative(e.pos.getX());
            int ly = SectionPos.sectionRelative(e.pos.getY());
            int lz = SectionPos.sectionRelative(e.pos.getZ());
            if (e.level > layer.get(lx, ly, lz)) {
                layer.set(lx, ly, lz, e.level);
            }
        }

        // 2) 备份 + 注入
        for (Map.Entry<Long, DataLayer> e : pending.entrySet()) {
            SectionPos sp = SectionPos.of(e.getKey());
            // 备份：只备份第一次碰到的原版数据
            if (!backups.containsKey(e.getKey())) {
                backups.put(e.getKey(), readLayer(level, lightEngine, sp));
            }
            // 关键：必须同时把该区块打开光照，否则 queueSectionData 的数据不会被采纳
            try {
                lightEngine.setLightEnabled(new net.minecraft.world.level.ChunkPos(sp.x(), sp.z()), true);
            } catch (Throwable ignored) {
            }
            lightEngine.queueSectionData(LightLayer.BLOCK, sp, e.getValue());
        }

        // 3) 不再需要的旧区块段：还原
        Set<Long> stale = new LinkedHashSet<>(backups.keySet());
        stale.removeAll(pending.keySet());
        for (Long key : stale) {
            restore(level, lightEngine, backups, key);
        }
    }

    /** 关灯 / 方块被拆：把所有备份原样写回。 */
    public static void clear(ServerLevel level) {
        if (level == null) {
            return;
        }
        Map<Long, DataLayer> backups = BACKUPS.get(level);
        if (backups == null || backups.isEmpty()) {
            return;
        }
        LevelLightEngine lightEngine = level.getLightEngine();
        for (Long key : new LinkedHashSet<>(backups.keySet())) {
            restore(level, lightEngine, backups, key);
        }
    }

    // ---------------- 内部工具 ----------------

    private static Map<Long, DataLayer> backupsOf(ServerLevel level) {
        return BACKUPS.computeIfAbsent(level, k -> new HashMap<>());
    }

    private static void restore(ServerLevel level, LevelLightEngine lightEngine,
                                Map<Long, DataLayer> backups, long sectionKey) {
        DataLayer original = backups.remove(sectionKey);
        SectionPos sp = SectionPos.of(sectionKey);
        try {
            lightEngine.queueSectionData(LightLayer.BLOCK, sp, original);
        } catch (Throwable ignored) {
            // section 可能已卸载：忽略
        }
    }

    /** 读出该区块段当前的原版光照数据（拿不到就返回空表）。 */
    private static DataLayer readLayer(ServerLevel level, LevelLightEngine lightEngine, SectionPos sp) {
        try {
            DataLayer current = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sp);
            return current == null ? new DataLayer(new byte[2048]) : current.copy();
        } catch (Throwable ignored) {
            return new DataLayer(new byte[2048]);
        }
    }

    private static DataLayer cloneLayer(DataLayer src) {
        return src == null ? new DataLayer(new byte[2048]) : src.copy();
    }

    /** 一条待注入的光照记录。 */
    public static final class Entry {
        public final BlockPos pos;
        public final int level;

        public Entry(BlockPos pos, int level) {
            this.pos = pos;
            this.level = level;
        }
    }
}
