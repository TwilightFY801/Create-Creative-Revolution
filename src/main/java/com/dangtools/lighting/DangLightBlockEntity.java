package com.dangtools.lighting;

import com.dangtools.block.LightPlatformBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 灯具方块实体：按 {@link DangLightBlock.Kind} 决定「这一 tick 该多亮」，
 * 需要时把新亮度写回方块状态属性 {@link DangLightBlock#LEVEL}。
 *
 * <h2>四种灯的判定规则</h2>
 * <pre>
 *   大灯 HEADLIGHT   拉杆开 -> 15，并在物理结构前方生成整片光锥
 *   刹车灯 BRAKE     拉杆开 = 常亮 10（"不明显"）；检测到后退键 -> 15
 *   左/右转向灯      按住对应方向键 -> 15（不需要拉杆，独立生效）
 *   车内灯 INTERIOR  按任意方向键 -> 较快变暗；松开 -> 较快变亮（0~14）
 * </pre>
 * 按键驱动的三种（刹车灯的"加亮"档、转向灯、车内灯）都要先满足
 * {@link LightingConditions} 的三条前置条件 —— 判定与过滤在
 * {@code ModNetwork.handleKeyInput} 里完成，本类只是读
 * {@link LightInputState} 的结果，所以它天然不动作。
 *
 * <h2>性能</h2>
 * {@code setBlock} 只在<b>整数亮度真的变了</b>时才调用（车内灯的连续插值也一样），
 * 所以绝大多数 tick 什么都不做。大灯的光锥只在「操作者移动 / 朝向变化超过阈值」时整体重建，
 * 并且永远<b>整片生成、整片删除</b>，绝不逐块增量刷新。
 */
public class DangLightBlockEntity extends LightPlatformBlockEntity {

    /**
     * 车内灯每 tick 的插值步长。
     * <p>
     * 机主反馈"现在太慢"，所以从 1/20（约 1 秒走完）加快到 <b>1/8（约 0.4 秒走完 14 档）</b> ——
     * 仍然是一档一档地走（14 档 / 8 档每秒 ≈ 每 2.5 tick 变一档），看得出是渐变，不是瞬间跳。
     */
    private static final float INTERIOR_STEP = 1.0F / 8.0F;

    /** 车内灯的亮上限（机主要求 14）。 */
    private static final int INTERIOR_MAX = 14;

    /** 光锥几何参数（与机主规格一一对应）。 */
    private static final int LENGTH = 100;
    private static final int WIDTH_NEAR = 10;
    private static final int WIDTH_FAR = 100;
    private static final int DECAY_DISTANCE = 80;
    private static final int DECAY_SIDE = 80;
    private static final int MAX_LIGHT = 15;
    private static final int MIN_LIGHT = 1;
    private static final int MAX_CONE_BLOCKS = 8192;
    private static final double MAX_STEP_DISTANCE_SQ = 4.0D;
    private static final double TURN_COS_THRESHOLD = 0.995D;
    private static final int CONE_MAX_AGE = 20;

    /** 车内灯的连续亮度（只在服务端推进）。 */
    private float interiorLevel = INTERIOR_MAX;

    /** 大灯已生成的光方块位置（用于整体删除）。 */
    private final java.util.Set<BlockPos> headlightBlocks = new java.util.LinkedHashSet<>();
    private org.joml.Vector3d lastConeApex;
    private org.joml.Vector3d lastConeDirection;
    private int coneAge;

    public DangLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void dangtools$tick() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!(getBlockState().getBlock() instanceof DangLightBlock block)) {
            return;
        }
        LightRegistry.register(worldPosition);
        switch (block.kind()) {
            case HEADLIGHT -> tickHeadlight();
            case BRAKE -> tickBrake();
            case LEFT_TURN -> tickTurn(true);
            case RIGHT_TURN -> tickTurn(false);
            case INTERIOR -> tickInterior();
        }
    }

    // ---------------- 各灯逻辑 ----------------

    private void tickTurn(boolean left) {
        LightInputs inputs = inputs();
        setLevel((left ? inputs.left() : inputs.right()) ? 15 : 0);
    }

    private void tickBrake() {
        // 机主反馈修正：踩后退键（S）时【无论拉杆有没有拉】都要亮到 15
        // （原来"没拉杆就直接 return 0"，导致倒退时刹车灯不亮）。
        // 拉杆拉开时另外常亮一档 2（"看着亮其实很暗"的那档，用于区分常亮与刹车）。
        com.dangtools.lighting.LightInputs dbg = inputs();
        if (level != null && !level.isClientSide && (dbg.any() || coneAge % 40 == 0)) {
            com.dangtools.DangTools.LOGGER.info("[dangtools][brake] fwd={} back={} left={} right={} lever={}",
                    dbg.forward(), dbg.back(), dbg.left(), dbg.right(), leverLit());
        }
        if (dbg.back()) {
            setLevel(15);
            return;
        }
        setLevel(leverLit() ? 2 : 0);
    }

    private void tickInterior() {
        float target = inputs().any() ? 0.0F : INTERIOR_MAX;
        if (interiorLevel < target) {
            interiorLevel = Math.min(target, interiorLevel + INTERIOR_STEP);
        } else if (interiorLevel > target) {
            interiorLevel = Math.max(target, interiorLevel - INTERIOR_STEP);
        }
        setLevel(Math.round(interiorLevel));
    }

    private void tickHeadlight() {
        boolean lit = leverLit();
        setLevel(lit ? 15 : 0);
        if (lit) {
            refreshCone();
        } else {
            clearCone();
        }
    }

    // ---------------- 大灯光锥 ----------------

    /**
     * 大灯光锥：<b>整片一次性生成 / 关闭时整体删除，绝不逐帧或逐块增量刷新</b>。
     * <p>
     * 只有三种情况会重建（其余 tick 直接返回，几乎零开销）：
     * <ol>
     *   <li>还没生成过；</li>
     *   <li>操作者（离灯最近的玩家）移动超过 2 格；</li>
     *   <li>朝向变化超过约 6 度，或者每 20 tick 兜底刷新一次。</li>
     * </ol>
     * 重建时先整体删旧片、再整体生新片，不做「逐块补差」，
     * 所以观感是整片一起动，也不会出现半新半旧的空洞。
     */
    private void refreshCone() {
        // ★ 已改用 LambDynamicLights 的动态光源行为（见 DangHeadlightBehavior / DangDynamicLights）：
        //   不再向世界放置任何 minecraft:light 方块 —— 零割裂、不掉帧，还能拿到丁达尔光柱。
        //   下面这段旧的"放光方块"实现整段停用，保留代码以便回退。
        if (true) {
            return;
        }
        org.joml.Vector3d apex = new org.joml.Vector3d(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D);
        org.joml.Vector3d direction = localBeamDirection();
        if (apex == null || direction == null || direction.lengthSquared() < 1.0E-6D) {
            return;
        }
        direction.normalize();

        coneAge++;
        boolean moved = lastConeApex == null || lastConeApex.distanceSquared(apex) > MAX_STEP_DISTANCE_SQ;
        boolean turned = lastConeDirection == null || lastConeDirection.dot(direction) < TURN_COS_THRESHOLD;
        boolean stale = coneAge >= CONE_MAX_AGE;
        if (!moved && !turned && !stale && !headlightBlocks.isEmpty()) {
            return;
        }
        net.minecraft.world.level.LevelAccessor target = lightTarget();
        if (target == null) {
            return;
        }

        java.util.Set<BlockPos> next = buildCone(apex, direction);

        // 不再增删光方块：把亮度直接注入主世界的光照引擎（性能 + 零割裂）
        java.util.List<com.dangtools.lighting.LightBridge.Entry> entries =
                new java.util.ArrayList<>(next.size());
        for (BlockPos pos : next) {
            entries.add(new com.dangtools.lighting.LightBridge.Entry(pos, levelFor(pos, apex, direction)));
        }
        // 机主实测：灯光桥方案有问题 -> 回到【真的放光方块】的做法（这条能照亮主世界）
        for (BlockPos pos : headlightBlocks) {
            if (!next.contains(pos) && target.getBlockState(pos).is(Blocks.LIGHT)) {
                target.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        int placed = 0;
        for (BlockPos pos : next) {
            if (placed >= MAX_CONE_BLOCKS) {
                break;
            }
            BlockState existing = target.getBlockState(pos);
            int needed = levelFor(pos, apex, direction);
            if (existing.is(Blocks.LIGHT)) {
                if (existing.getValue(LightBlock.LEVEL) != needed) {
                    target.setBlock(pos, existing.setValue(LightBlock.LEVEL, needed), Block.UPDATE_ALL);
                    pokeLight(target, pos);
                }
                placed++;
                continue;
            }
            if (!existing.isAir() && !existing.canBeReplaced()) {
                continue;
            }
            target.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, needed), Block.UPDATE_ALL);
            pokeLight(target, pos);
            placed++;
        }

        // ---- 方案1 的另一半：亮度注入【主世界】光照引擎 ----
        if (level instanceof net.minecraft.server.level.ServerLevel mainLevel) {
            java.util.List<com.dangtools.lighting.LightBridge.Entry> bridgeEntries =
                    new java.util.ArrayList<>(next.size());
            for (BlockPos pos : next) {
                org.joml.Vector3d w = worldPosition(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                bridgeEntries.add(new com.dangtools.lighting.LightBridge.Entry(
                        net.minecraft.core.BlockPos.containing(w.x, w.y, w.z),
                        levelFor(pos, apex, direction)));
            }
            com.dangtools.lighting.LightBridge.apply(mainLevel, bridgeEntries);
        }

        headlightBlocks.addAll(next);
        lastConeApex = apex;
        lastConeDirection = direction;
        coneAge = 0;
    }

    private void clearCone() {
        // 把注入主世界光照引擎的数据全部还原（见 LightBridge 的备份/还原机制）
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            com.dangtools.lighting.LightBridge.clear(sl);
        }
        if (headlightBlocks.isEmpty()) {
            return;
        }
        net.minecraft.world.level.LevelAccessor target = lightTarget(); if (target != null) {
            for (BlockPos pos : headlightBlocks) {
                if (target.getBlockState(pos).is(Blocks.LIGHT)) {
                    target.setBlock(pos, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        headlightBlocks.clear();
        lastConeApex = null;
        lastConeDirection = null;
        coneAge = 0;
    }

    /** 算出光锥覆盖的方块位置集合（只算，不写世界）。 */
    private java.util.Set<BlockPos> buildCone(org.joml.Vector3d apex, org.joml.Vector3d forward) {
        int[] maxForward = occludedDistance(apex, forward);

        org.joml.Vector3d right = perpendicularRight(forward);
        org.joml.Vector3d upPerp = new org.joml.Vector3d(right).cross(forward).normalize();

        int baseX = net.minecraft.util.Mth.floor(apex.x);
        int baseY = net.minecraft.util.Mth.floor(apex.y);
        int baseZ = net.minecraft.util.Mth.floor(apex.z);

        java.util.Set<BlockPos> result = new java.util.LinkedHashSet<>();
        int reach = LENGTH + 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double rx = baseX + dx + 0.5D - apex.x;
                    double ry = baseY + dy + 0.5D - apex.y;
                    double rz = baseZ + dz + 0.5D - apex.z;
                    double f = rx * forward.x + ry * forward.y + rz * forward.z;
                    if (f < 0.0D) {
                        continue;
                    }
                    int slab = net.minecraft.util.Mth.floor(f);
                    if (slab > LENGTH) {
                        continue;
                    }
                    double hx = rx - f * forward.x;
                    double hy = ry - f * forward.y;
                    double hz = rz - f * forward.z;
                    double h = Math.sqrt(hx * hx + hy * hy + hz * hz);
                    // 机主：扇形不搞了，改回最早那版形状（下面这条公式），只保留加长到 50 的距离。
                    double allowed = (maxForward[slab] + 0.5D) * widthRatio(f);
                    if (h > allowed) {
                        continue;
                    }
                    result.add(new BlockPos(baseX + dx, baseY + dy, baseZ + dz));
                    if (result.size() >= MAX_CONE_BLOCKS) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    /** 光锥在前进 f 格处的半宽比（近端 10、30 格处 30）。 */
    private static double widthRatio(double f) {
        return (WIDTH_NEAR + (WIDTH_FAR - WIDTH_NEAR) * (f / (double) LENGTH)) / (double) WIDTH_NEAR;
    }

    /**
     * 某个位置最终的光照等级。
     * <p>
     * 机主的规格：<b>前方从 20 格开始衰减、左右也从 20 格开始衰减，到 30 格降为 1</b>。
     * 所以两段衰减都用「从 20 到 30 的 10 格跨度」把 15 线性降到 1，两个条件取更暗的那个。
     */
    private static int levelFor(BlockPos pos, org.joml.Vector3d apex, org.joml.Vector3d forward) {
        double rx = pos.getX() + 0.5D - apex.x;
        double ry = pos.getY() + 0.5D - apex.y;
        double rz = pos.getZ() + 0.5D - apex.z;
        double f = rx * forward.x + ry * forward.y + rz * forward.z;
        double hx = rx - f * forward.x;
        double hy = ry - f * forward.y;
        double hz = rz - f * forward.z;
        double h = Math.sqrt(hx * hx + hy * hy + hz * hz);

        double decay = 0.0D;
        if (f > DECAY_DISTANCE) {
            decay = (f - DECAY_DISTANCE) / (double) (LENGTH - DECAY_DISTANCE);
        }
        if (h > DECAY_SIDE) {
            decay = Math.max(decay, (h - DECAY_SIDE) / (double) (LENGTH - DECAY_SIDE));
        }
        double level = MAX_LIGHT - (MAX_LIGHT - MIN_LIGHT) * Math.min(1.0D, decay);
        return (int) net.minecraft.util.Mth.clamp((int) Math.round(level), MIN_LIGHT, MAX_LIGHT);
    }

    /**
     * 遮挡判断：从锥顶沿 forward 撒一排平行射线，记录每个前进距离上「未被挡住」的最远距离。
     * <p>
     * 每条射线用 {@code ClipContext.Block.COLLIDER}（只被有碰撞的方块挡住；空气/草/光方块不算），
     * 打到的距离就是这条射线能走多远。最后对相邻值做一次前向限制，
     * 得到一条单调不增的轮廓 —— 于是墙后不会再生成光方块。
     */
    private int[] occludedDistance(org.joml.Vector3d apex, org.joml.Vector3d forward) {
        int[] maxForward = new int[LENGTH + 1];
        java.util.Arrays.fill(maxForward, LENGTH);

        org.joml.Vector3d right = perpendicularRight(forward);
        org.joml.Vector3d upPerp = new org.joml.Vector3d(right).cross(forward).normalize();

        org.joml.Vector3d start = new org.joml.Vector3d();
        org.joml.Vector3d end = new org.joml.Vector3d();
        org.joml.Vector3d hitPoint = new org.joml.Vector3d();

        for (int u = -WIDTH_FAR; u <= WIDTH_FAR; u++) {
            for (int v = -WIDTH_FAR; v <= WIDTH_FAR; v++) {
                double hOffset = Math.sqrt((double) u * u + (double) v * v);
                start.set(apex).fma(u, right).fma(v, upPerp);
                end.set(start).fma(LENGTH, forward);

                net.minecraft.world.phys.BlockHitResult hit = plotClipLevel().clip(new net.minecraft.world.level.ClipContext(
                        new net.minecraft.world.phys.Vec3(start.x, start.y, start.z),
                        new net.minecraft.world.phys.Vec3(end.x, end.y, end.z),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()));

                int last = LENGTH;
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    hitPoint.set(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                    last = net.minecraft.util.Mth.floor(Math.sqrt(start.distanceSquared(hitPoint)));
                }

                for (int slab = 0; slab <= Math.min(LENGTH, last); slab++) {
                    double allowed = WIDTH_NEAR + (WIDTH_FAR - WIDTH_NEAR) * (slab / (double) LENGTH);
                    if (hOffset > allowed) {
                        continue;
                    }
                    if (last < maxForward[slab]) {
                        maxForward[slab] = last;
                    }
                }
            }
        }

        // 前向限制：被挡住之后更远处不可能有光
        for (int i = 1; i <= LENGTH; i++) {
            if (maxForward[i] > maxForward[i - 1]) {
                maxForward[i] = maxForward[i - 1];
            }
        }
        return maxForward;
    }

    private static org.joml.Vector3d perpendicularRight(org.joml.Vector3d forward) {
        org.joml.Vector3d right = new org.joml.Vector3d(forward).cross(0.0D, 1.0D, 0.0D);
        if (right.lengthSquared() < 1.0E-6D) {
            right.set(1.0D, 0.0D, 0.0D);
        }
        return right.normalize();
    }

    // ---------------- 小工具 ----------------

    /** 只在「整数亮度真的变了」时才写方块状态。 */
    private void setLevel(int next) {
        int clamped = net.minecraft.util.Mth.clamp(next, 0, 15);
        BlockState state = getBlockState();
        if (!state.hasProperty(DangLightBlock.LEVEL) || state.getValue(DangLightBlock.LEVEL) == clamped) {
            return;
        }
        if (level != null) {
            level.setBlock(worldPosition, state.setValue(DangLightBlock.LEVEL, clamped), Block.UPDATE_CLIENTS);
        }
    }

    /** 灯所在的物理结构里，拉杆是不是【开着】。 */
    protected boolean leverLit() {
        if (level == null) {
            return false;
        }
        if (LeverRegistry.isLitAt(level, worldPosition)) {
            return true;
        }
        // 登记表里还没有（先放灯、后放拉杆 / 区块重载）：主动找一次并顺手登记。
        // 注意：找到拉杆还不算数，必须【拉杆本身的 LIT = true】才算开灯 ——
        // 之前这里写成 `findLever(...) != null`（只要附近"有"拉杆就返回 true），
        // 所以机主一放下拉杆，大灯与刹车灯就立刻亮了（机主实测反馈的 bug）。
        BlockPos leverPos = LeverRegistry.findLever(level, worldPosition, 8);
        if (leverPos == null) {
            return false;
        }
        BlockState leverState = level.getBlockState(leverPos);
        return leverState.hasProperty(LightingLeverBlock.LIT)
                && leverState.getValue(LightingLeverBlock.LIT);
    }

    /** 离这盏灯最近的玩家当前的方向键输入。 */
    protected LightInputs inputs() {
        if (level == null) {
            return LightInputs.NONE;
        }
        org.joml.Vector3d center = worldCenter();
        return LightInputState.inputsFor(level, center.x, center.y, center.z);
    }

    /** 供客户端动态光源读取当前亮度（方块状态已同步，所以客户端也拿得到）。 */
    public int lightLevelForRender() {
        try {
            return getBlockState().getValue(DangLightBlock.LEVEL);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            com.dangtools.client.DangDynamicLights.register(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            com.dangtools.client.DangDynamicLights.unregister(this);
        }
        clearCone();
        LightRegistry.unregister(worldPosition);
        super.setRemoved();
    }

    /** 供拉杆方块实体调用：整体删除已生成的大灯光方块。 */
    public void releaseGeneratedLights() {
        clearCone();
    }

    // ==========================================================================
    // 光方块生成在【物理结构(plot)】里，而不是实时生成在主世界（机主要求）
    //   这样光方块就是载具本体的一部分、跟着结构一起动，
    //   拖动结构时不会有"割裂感"，碰撞箱也会同步跟着变。
    // ==========================================================================

    /** 生成 / 读写光方块的目标：优先子世界的 plot（结构本体）；不在结构里才退回主世界。 */
    private net.minecraft.world.level.LevelAccessor lightTarget() {
        // 方案1：光方块放 plot（跟着结构走、零割裂），亮度另外注入主世界（见 refreshCone 里的 LightBridge）
        try {
            org.joml.Vector3d wc = worldCenter();
            dev.ryanhcode.sable.sublevel.SubLevel sub = dev.ryanhcode.sable.Sable.HELPER.getContaining(level,
                    net.minecraft.core.BlockPos.containing(wc.x, wc.y, wc.z));
            if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel server) {
                return server.getPlot().getEmbeddedLevelAccessor();
            }
        } catch (Throwable ignored) {
        }
        return level instanceof net.minecraft.world.level.LevelAccessor accessor ? accessor : null;
    }

    /** 遮挡射线用的方块读取器（与生成目标一致，所以判定的是结构内部的方块）。 */
    private net.minecraft.world.level.BlockGetter plotClipLevel() {
        net.minecraft.world.level.LevelAccessor target = lightTarget();
        return target != null ? target : level;
    }

    /** 结构坐标系里的光锥方向 = FACING 的反方向（不做任何姿态变换）。 */
    private org.joml.Vector3d localBeamDirection() {
        try {
            net.minecraft.core.Direction facing = getBlockState()
                    .getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            return new org.joml.Vector3d(-facing.getStepX(), -facing.getStepY(), -facing.getStepZ());
        } catch (Throwable ignored) {
            return new org.joml.Vector3d(0.0D, 0.0D, -1.0D);
        }
    }

    /**
     * 让目标（尤其是子世界的 plot）的光照引擎重算这一格。
     * <p>
     * 机主实测：光方块确实生成在结构里了（F3 能看到），但**完全没有亮度** ——
     * 因为原来 setBlock 只用了 {@code Block.UPDATE_CLIENTS}，光照引擎不会重算。
     * 这里显式调 {@code checkBlock}，plot 自己的光照引擎才会把这个光源算进去。
     */
    private static void pokeLight(net.minecraft.world.level.LevelAccessor target, BlockPos pos) {
        try {
            target.getLightEngine().checkBlock(pos);
        } catch (Throwable ignored) {
            // 拿不到光照引擎就算了，不影响方块本身
        }
    }

    // ======================================================================
    // 大灯瞄准偏角（度）：由「潜行 + 对着方块滚滚轮」调整
    //   ★ 只影响【光束(丁达尔)的朝向】，不参与任何光照计算
    // ======================================================================
    private float aimYaw;
    private float aimPitch;

    public float aimYaw() {
        return aimYaw;
    }

    public float aimPitch() {
        return aimPitch;
    }

    /** 调整偏角（客户端调完发服务端；服务端落 NBT 并同步给所有客户端）。 */
    public void setAim(float yaw, float pitch) {
        this.aimYaw = net.minecraft.util.Mth.clamp(yaw, -80.0F, 80.0F);
        this.aimPitch = net.minecraft.util.Mth.clamp(pitch, -60.0F, 60.0F);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("aimYaw", aimYaw);
        tag.putFloat("aimPitch", aimPitch);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        aimYaw = tag.getFloat("aimYaw");
        aimPitch = tag.getFloat("aimPitch");
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}