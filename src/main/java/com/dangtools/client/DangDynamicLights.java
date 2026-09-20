package com.dangtools.client;

import com.dangtools.block.LightPlatformBlockEntity;
import com.dangtools.lighting.DangLightBlock;
import com.dangtools.lighting.DangLightBlockEntity;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端侧：把本模组的"大灯"喂给 <b>LambDynamicLights</b>。
 *
 * <p>这里全部用<b>反射</b>调用 LDL（配合 try/catch），所以：
 * <ul>
 *   <li>LDL 不在时（可选依赖）本模组照常运行，只是没有动态光柱；</li>
 *   <li>不会因为类缺失抛 {@code NoClassDefFoundError} 把游戏搞崩。</li>
 * </ul>
 *
 * <p>亮度直接取方块状态里已同步的 {@code LEVEL}(0~15)：服务端算好、客户端只负责显示，
 * 所以不需要额外的网络包。
 */
public final class DangDynamicLights {

    /** 客户端当前存在的所有大灯方块实体。 */
    private static final Set<DangLightBlockEntity> HEADLIGHTS =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 每个大灯对应的 LDL behavior 实例。 */
    private static final Map<DangLightBlockEntity, Object> BEHAVIORS = new ConcurrentHashMap<>();

    /** 反射缓存：DynamicLightBehaviorManager.add / remove。 */
    private static Method addMethod;
    private static Method removeMethod;
    private static boolean ldlLookupFailed;

    private DangDynamicLights() {}

    /** 由 {@link DangLightBlockEntity} 在客户端 onLoad 时调用。 */
    public static void register(DangLightBlockEntity be) {
        if (be != null && be.getBlockState().getBlock() instanceof DangLightBlock light
                && light.kind() == DangLightBlock.Kind.HEADLIGHT) {
            HEADLIGHTS.add(be);
        }
    }

    /** 由 {@link DangLightBlockEntity} 在 setRemoved 时调用。 */
    public static void unregister(DangLightBlockEntity be) {
        if (be == null) {
            return;
        }
        HEADLIGHTS.remove(be);
        Object behavior = BEHAVIORS.remove(be);
        if (behavior != null) {
            invoke(false, behavior);
        }
    }

    /** 每客户端 tick 调一次。 */
    public static void clientTick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        for (DangLightBlockEntity be : new LinkedHashSet<>(HEADLIGHTS)) {
            if (be.isRemoved()) {
                unregister(be);
                continue;
            }
            if (!(be instanceof LightPlatformBlockEntity platform)) {
                continue;
            }
            // 亮度：客户端拿到的是服务端同步过来的方块状态，0 = 灭
            int luminance = be.lightLevelForRender();
            if (luminance <= 0) {
                Object removed = BEHAVIORS.remove(be);
                if (removed != null) {
                    invoke(false, removed);
                }
                continue;
            }

            // 世界坐标：把结构(plot)坐标经 logicalPose 变换到世界
            Vector3d origin = platform.worldPosition(be.getBlockPos().getX() + 0.5D,
                    be.getBlockPos().getY() + 0.5D, be.getBlockPos().getZ() + 0.5D);
            // 朝向：取"原点"和"原点+局部朝向"两点变换后相减 —— 这样不需要法线变换接口
            Vector3d localDir = beamLocal(be);
            Vector3d tip = platform.worldPosition(
                    be.getBlockPos().getX() + 0.5D + localDir.x,
                    be.getBlockPos().getY() + 0.5D + localDir.y,
                    be.getBlockPos().getZ() + 0.5D + localDir.z);
            Vector3d dir = new Vector3d(tip).sub(origin);

            Object behavior = BEHAVIORS.get(be);
            if (behavior == null) {
                behavior = new DangHeadlightBehavior(origin, dir, luminance);
                BEHAVIORS.put(be, behavior);
                invoke(true, behavior);
            } else {
                try {
                    behavior.getClass().getMethod("update", Vector3d.class, Vector3d.class, int.class)
                            .invoke(behavior, origin, dir, luminance);
                } catch (Throwable ignored) {
                    // 更新失败就下一 tick 重新注册
                    BEHAVIORS.remove(be);
                }
            }
        }
    }

    /** 局部的光束方向（与 DangLightBlockEntity.localBeamDirection 一致）。 */
    private static Vector3d beamLocal(DangLightBlockEntity be) {
        try {
            net.minecraft.core.Direction facing = be.getBlockState()
                    .getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            return new Vector3d(-facing.getStepX(), -facing.getStepY(), -facing.getStepZ());
        } catch (Throwable ignored) {
            return new Vector3d(0.0D, 0.0D, -1.0D);
        }
    }

    /** 反射拿 LDL 的 DynamicLightBehaviorManager，再 add / remove。 */
    private static void invoke(boolean add, Object behavior) {
        try {
            if (addMethod == null || removeMethod == null) {
                if (ldlLookupFailed) {
                    return;
                }
                Class<?> modClass = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
                Object instance = modClass.getField("INSTANCE").get(null);
                Object manager = modClass.getMethod("dynamicLightBehaviorManager").invoke(instance);
                Class<?> managerClass = Class.forName("dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager");
                addMethod = managerClass.getMethod("add",
                        Class.forName("dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior"));
                removeMethod = managerClass.getMethod("remove",
                        Class.forName("dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior"));
                if (add) {
                    addMethod.invoke(manager, behavior);
                } else {
                    removeMethod.invoke(manager, behavior);
                }
                return;
            }
            // 每次都重新取 manager（实例是单例，但取一次也无妨）
            Class<?> modClass = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
            Object instance = modClass.getField("INSTANCE").get(null);
            Object manager = modClass.getMethod("dynamicLightBehaviorManager").invoke(instance);
            (add ? addMethod : removeMethod).invoke(manager, behavior);
        } catch (Throwable t) {
            if (addMethod == null) {
                ldlLookupFailed = true;   // LDL 不在：以后不再尝试
            }
        }
    }
}
