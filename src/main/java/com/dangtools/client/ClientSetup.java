package com.dangtools.client;

import com.dangtools.DangTools;
import com.dangtools.Registration;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.motor.CreativeMotorRenderer;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 客户端渲染接线：让「高级马达」的传动杆/旋转与创造马达完全一致。
 * <p>
 * 关键点：转动的部件是 <b>Flywheel 可视化器(Visualizer)</b> 渲染的，它按「方块实体类型」注册。
 * 我们自己新建了方块实体类型，所以要把原类型的可视化器复制到我们的类型上，外观才会出现并转动。
 * <p>
 * <b>双轮悬架不再走这条路</b>：反编译确认 offroad 的 {@code WHEEL_MOUNT} <b>根本没有 Flywheel 可视化器</b>
 * （{@code offroad.jar} 里只有 {@code BoreheadBearingVisual} 和 {@code RockCuttingWheelActorVisual}），
 * 它的轮胎与悬挂件全部由 BER {@code WheelMountRenderer} 绘制。
 * 所以双轮悬架改为在 {@link DualWheelSuspensionRenderer} 里自己多画一个轮胎，见那个类的注释。
 * <p>
 * 本类只在客户端加载（由 DangTools 构造器里的 Dist 判断保护）。
 */
public class ClientSetup {

    /** 后备：注册普通方块实体渲染器。 */
    /** 注册大灯光锥的核心着色器（照 HandheldMoon 的 beam_cone 抄的）。 */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onRegisterShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
        DangHeadlightShaders.onRegisterShaders(event);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Registration.PARTY_POWER_BE.get(), CreativeMotorRenderer::new);
        event.registerBlockEntityRenderer(Registration.DUAL_WHEEL_SUSPENSION_BE.get(), DualWheelSuspensionRenderer::new);
        // 大灯：纯渲染丁达尔光束（不做光照计算，避免区块光照重建导致移动卡顿）
        event.registerBlockEntityRenderer(Registration.LIGHTING_LIGHT_BE.get(), DangHeadlightBeamRenderer::new);
        // 灯具不需要额外渲染器：亮度由方块状态属性驱动，外观由普通方块模型绘制
    }

    /** 把原类型的 Flywheel 可视化器复制到我们的类型上。 */
    public static void copyMotorVisual(FMLClientSetupEvent event) {
        copyVisual(safeType(() -> AllBlockEntityTypes.MOTOR.get()), Registration.PARTY_POWER_BE.get(), "创造马达");
    }

    private static BlockEntityType<?> safeType(java.util.function.Supplier<BlockEntityType<?>> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyVisual(BlockEntityType<?> from, BlockEntityType<?> to, String label) {
        if (from == null) {
            DangTools.LOGGER.warn("[dangtools] 找不到 {} 的方块实体类型，跳过可视化器复制。", label);
            return;
        }
        try {
            BlockEntityVisualizer<?> visualizer = VisualizerRegistry.getVisualizer((BlockEntityType) from);
            if (visualizer != null) {
                VisualizerRegistry.setVisualizer(to, (BlockEntityVisualizer) visualizer);
                DangTools.LOGGER.info("[dangtools] 已把 {} 的 Flywheel 可视化器复制给本模组方块。", label);
            } else {
                DangTools.LOGGER.warn("[dangtools] {} 没有可复制的 Flywheel 可视化器。", label);
            }
        } catch (Throwable t) {
            DangTools.LOGGER.warn("[dangtools] 复制 {} 的 Flywheel 可视化器失败: {}", label, t.toString());
        }
    }
}
