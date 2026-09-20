package com.dangtools.client;

import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;

/**
 * 大灯的「动态光源行为」——交给 <b>LambDynamicLights</b> 渲染，<b>不再放置任何光方块</b>。
 *
 * <h2>为什么这么做</h2>
 * 之前两种做法都有硬伤：主世界放光方块 = 移动割裂 + 掉帧；plot 里放光方块 = 照不到主世界。
 * LambDynamicLights 提供了公开接口 {@link DynamicLightBehavior}，它自己在客户端按
 * {@link #lightAtPos} 的返回值动态给世界打光（并渲染丁达尔光柱），所以：
 * <ul>
 *   <li>零方块增删 → 零割裂、不掉帧；</li>
 *   <li>{@code lightAtPos} 收的是<b>世界坐标</b>，我们用 {@code worldPosition()} 把
 *       结构(plot)坐标转成世界坐标喂进来 → <b>物理结构也能照亮主世界</b>；</li>
 *   <li>体积光由它渲染，外观漂亮。</li>
 * </ul>
 *
 * <h2>亮度取舍（机主实测结论）</h2>
 * 「外面看着有光柱就够了，内部不必很亮」—— 所以锥体内侧只给温和的衰减，不追求把内部照白。
 */
public class DangHeadlightBehavior implements DynamicLightBehavior {

    /** 光锥参数（机主要求放大到原来的 2 倍）。 */
    public static final double LENGTH = 100.0D;
    public static final double WIDTH_NEAR = 10.0D;
    public static final double WIDTH_FAR = 100.0D;
    /** 前方从多少格开始衰减。 */
    public static final double DECAY_FORWARD = 80.0D;

    private Vector3d origin;
    private Vector3d direction;
    private int luminance;

    /** 上一次的快照，用于 {@link #hasChanged()} 判断是否需要重算光照。 */
    private final Vector3d prevOrigin = new Vector3d();
    private final Vector3d prevDirection = new Vector3d();
    private int prevLuminance = -1;
    private boolean hasPrev;

    public DangHeadlightBehavior(Vector3d origin, Vector3d direction, int luminance) {
        this.origin = new Vector3d(origin);
        this.direction = new Vector3d(direction).normalize();
        this.luminance = luminance;
    }

    /** 更新位置/朝向/亮度（结构移动时每 tick 调用，成本极低）。 */
    public void update(Vector3d newOrigin, Vector3d newDirection, int newLuminance) {
        this.origin = new Vector3d(newOrigin);
        if (newDirection.lengthSquared() > 1.0E-9D) {
            this.direction = new Vector3d(newDirection).normalize();
        }
        this.luminance = newLuminance;
    }

    @Override
    public double lightAtPos(BlockPos pos, double y) {
        if (luminance <= 0) {
            return 0.0D;
        }
        // 目标点相对锥顶的向量
        double px = pos.getX() + 0.5D - origin.x;
        double py = pos.getY() + 0.5D - origin.y;
        double pz = pos.getZ() + 0.5D - origin.z;

        // 沿光轴的前进距离
        double f = px * direction.x + py * direction.y + pz * direction.z;
        if (f < 0.0D || f > LENGTH) {
            return 0.0D;
        }
        // 到光轴的垂直距离
        double hx = px - f * direction.x;
        double hy = py - f * direction.y;
        double hz = pz - f * direction.z;
        double h = Math.sqrt(hx * hx + hy * hy + hz * hz);

        // 该距离处的锥体半径（直径 10 -> 100，所以半径 5 -> 50）
        double halfWidth = (WIDTH_NEAR + (WIDTH_FAR - WIDTH_NEAR) * (f / LENGTH)) * 0.5D;
        if (h > halfWidth) {
            return 0.0D;
        }

        // 衰减：前方从 DECAY_FORWARD 开始变暗；侧向越靠边越暗（都是温和衰减，见类注释）
        double byLength = 1.0D - 0.85D * clamp01((f - DECAY_FORWARD) / Math.max(1.0D, LENGTH - DECAY_FORWARD));
        double bySide = 1.0D - 0.60D * clamp01(h / Math.max(1.0D, halfWidth));
        return luminance * Math.min(byLength, bySide);
    }

    @Override
    public DynamicLightBehavior.BoundingBox getBoundingBox() {
        // 用光锥长度做一个保守的立方包围盒（LDL 只会在这个范围内查询）
        int r = (int) Math.ceil(LENGTH);
        return new DynamicLightBehavior.BoundingBox(
                (int) Math.floor(origin.x) - r, (int) Math.floor(origin.y) - r, (int) Math.floor(origin.z) - r,
                (int) Math.ceil(origin.x) + r, (int) Math.ceil(origin.y) + r, (int) Math.ceil(origin.z) + r);
    }

    @Override
    public boolean hasChanged() {
        if (!hasPrev
                || prevLuminance != luminance
                || prevOrigin.distanceSquared(origin) > 0.01D
                || prevDirection.dot(direction) < 0.99995D) {
            prevOrigin.set(origin);
            prevDirection.set(direction);
            prevLuminance = luminance;
            hasPrev = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean isRemoved() {
        return luminance <= 0;
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }
}
