package com.dangtools.block;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本模组「装在载具上的方块」的公共基类，统一解决两件事：
 *
 * <h2>1. 我在哪个物理结构里？</h2>
 * 用 Sable 的 {@code Sable.HELPER.getContaining(level, pos)}。
 * <b>依据</b>：反编译 {@code VelocitySensorBlockEntity.initialize()} 字节码，那里就是
 * <pre>
 *   new WeakReference&amp;lt;&amp;gt;(Sable.HELPER.getContaining(this.getLevel(), this.worldPosition))
 * </pre>
 * 说明这是官方推荐的「方块 -&gt; 子世界」入口。
 * 我们用 {@link WeakReference} 缓存并在每次取用时校验（子世界可能被拆分/合并/移除）。
 *
 * <h2>2. 这个世界坐标是「物理坐标」还是「子世界内部坐标」？</h2>
 * Sable 的方块实际上存在于「plot」网格里的<b>世界坐标</b>上，而它们的物理位置由
 * {@code SubLevel.logicalPose()} 给出。所以：
 * <ul>
 *   <li><b>读数</b>（高度/姿态/速度）直接读 pose，见 {@link #logicalPose()}、{@link #linearVelocity()}；</li>
 *   <li><b>世界位置</b>（要往世界放方块、要算离玩家多远）必须把本地坐标过一遍
 *       {@code pose.transformPosition(...)}，见 {@link #worldPosition(double, double, double)}。</li>
 * </ul>
 * 这两件事混了就会出现「灯生成在 plot 里而不是载具前面」这类错误。
 */
public abstract class LightPlatformBlockEntity extends BlockEntity {

    private java.lang.ref.WeakReference<SubLevel> subLevelRef = new java.lang.ref.WeakReference<>(null);
    private int refreshCooldown;

    protected LightPlatformBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Sable 每 tick 调用（由各方块自己的 ticker 转发）；子类覆盖它写逻辑。 */
    public void dangtools$tick() {
    }

    /** 统一的 ticker 入口：先维护子世界引用，再走子类逻辑。 */
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            LightPlatformBlockEntity be) {
        be.refreshSubLevel();
        be.dangtools$tick();
    }

    /** 给 {@code EntityBlock.getTicker} 用的便捷包装。 */
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> ticker() {
        return (level, pos, state, be) -> tick(level, pos, state, (LightPlatformBlockEntity) be);
    }

    /** 子世界引用可能失效（拆分/合并/卸载），所以定期重新取一次。 */
    protected void refreshSubLevel() {
        SubLevel current = subLevelRef.get();
        if (current != null && !current.isRemoved()) {
            if (--refreshCooldown > 0) {
                return;
            }
        }
        refreshCooldown = 20;
        subLevelRef = new java.lang.ref.WeakReference<>(resolveSubLevel());
    }

    private SubLevel resolveSubLevel() {
        if (level == null) {
            return null;
        }
        try {
            return Sable.HELPER.getContaining(level, worldPosition);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 我所在的物理结构；不在任何物理结构里（直接放在地上）返回 null。 */
    public SubLevel subLevel() {
        SubLevel current = subLevelRef.get();
        if (current == null || current.isRemoved()) {
            current = resolveSubLevel();
            subLevelRef = new java.lang.ref.WeakReference<>(current);
        }
        return current;
    }

    /** 物理姿态（含位置/朝向/缩放）；没有物理结构时返回 null。 */
    public Pose3dc logicalPose() {
        SubLevel sub = subLevel();
        if (sub == null) {
            return null;
        }
        try {
            return sub.logicalPose();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 载具的线速度（世界坐标，格/秒级）。 */
    public org.joml.Vector3dc linearVelocity() {
        if (subLevel() instanceof ServerSubLevel server) {
            return server.latestLinearVelocity;
        }
        // 客户端 / 非服务端子世界：退回物理管线读取
        try {
            SubLevel sub = subLevel();
            if (sub instanceof ServerSubLevel server) {
                RigidBodyHandle handle = RigidBodyHandle.of(server);
                if (handle != null && handle.isValid()) {
                    return handle.getLinearVelocity();
                }
            }
        } catch (Throwable ignored) {
            // 拿不到就是拿不到，返回 null 由调用方退化处理
        }
        return null;
    }

    /** 载具的角速度（世界坐标）。 */
    public org.joml.Vector3dc angularVelocity() {
        if (subLevel() instanceof ServerSubLevel server) {
            return server.latestAngularVelocity;
        }
        return null;
    }

    /** 把我这个方块在世界里的中心坐标算出来（跟随载具移动）。 */
    public org.joml.Vector3d worldCenter() {
        return worldPosition(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D);
    }

    /** 把「我这个方块的局部坐标」变换到世界坐标。没有物理结构时原样返回。 */
    public org.joml.Vector3d worldPosition(double localX, double localY, double localZ) {
        org.joml.Vector3d local = new org.joml.Vector3d(localX, localY, localZ);
        Pose3dc pose = logicalPose();
        if (pose == null) {
            return local;
        }
        return pose.transformPosition(local, new org.joml.Vector3d());
    }

    /** 把世界坐标变换回「我这个方块的局部坐标系」。 */
    public org.joml.Vector3d toLocal(double worldX, double worldY, double worldZ) {
        org.joml.Vector3d world = new org.joml.Vector3d(worldX, worldY, worldZ);
        Pose3dc pose = logicalPose();
        if (pose == null) {
            return world;
        }
        return pose.transformPositionInverse(world, new org.joml.Vector3d());
    }

    /**
     * 方块状态里 FACING 指向的方向，变换到世界坐标后的单位向量。
     * 没装 FACING 属性时返回本地 +Z。
     */
    public org.joml.Vector3d facingWorld() {
        net.minecraft.core.Direction facing = null;
        if (getBlockState().hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            facing = getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        }
        org.joml.Vector3d local = facing == null
                ? new org.joml.Vector3d(0.0D, 0.0D, 1.0D)
                : new org.joml.Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        local.normalize();
        Pose3dc pose = logicalPose();
        return pose == null ? local : pose.transformNormal(local, new org.joml.Vector3d());
    }

    /** 机主用的「这盏灯现在朝哪」——取方块朝向的相反方向（灯照出去的方向）。 */
    public org.joml.Vector3d beamDirection() {
        // 机主：原本"跟随方块朝向"就很好，不要改成跟随准星 —— 所以这里保持原实现。
        return new org.joml.Vector3d(facingWorld()).negate();
    }
}
