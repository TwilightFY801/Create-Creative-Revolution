package com.dangtools.client;

import com.dangtools.DangTools;
import com.dangtools.lighting.LightInputState;
import com.dangtools.lighting.LightInputs;
import com.dangtools.lighting.LightingConditions;
import com.dangtools.network.KeyInputC2SPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端接线（三件事）：
 * <ol>
 *   <li><b>注册按键</b>：把 {@link DangKeybinds} 的四个绑定注册进原版按键系统
 *       （不注册的话 {@link KeyMapping#isDown()} 永远返回 false，也不会出现在「按键设置」里）；</li>
 *   <li><b>每 tick 采样并只在变化时发包</b>：读取四个绑定的按下状态，翻译成逻辑输入，
 *       与上一次发送的值比较，<b>只有变了才发</b> {@link KeyInputC2SPayload}；</li>
 *   <li><b>本地镜像</b>：把采样值写进 {@link LightInputState} 的客户端副本，
 *       让客户端自己的渲染立刻跟手。</li>
 * </ol>
 *
 * <h2>灯光响应按键的前置条件（机主要求）</h2>
 * 三条：① 载具已物理化 ② 玩家坐着（坐垫/座位） ③ 手上正在控制遥控器/高级打字机。
 * 客户端能判 ② 和 ③，判不了 ①（不知道灯在哪），所以：
 * <ul>
 *   <li>客户端：② 或 ③ 不满足就发 {@code NONE}（全松开），连按键状态都不上报；</li>
 *   <li>服务端：再用灯的实际位置校验 ①（见 {@code ModNetwork.handleKeyInput}
 *       与 {@code LightingConditions.serverAccepts}）。</li>
 * </ul>
 * 任一条不满足时，所有<b>按键驱动</b>的灯（转向灯/刹车灯/车内灯）立刻回位；
 * 拉杆的开关状态（方块状态）不受影响，大灯/刹车灯的"常亮"档也不受影响。
 *
 * <h2>「遥控器已激活」怎么判</h2>
 * Create 遥控器的激活态是 {@code LinkedControllerClientHandler.MODE != IDLE}，
 * 只有客户端有这个字段（服务端没有）。所以这里读它，并放进
 * {@link KeyInputC2SPayload#controllerActive()} 上报，再由服务端与它自己的
 * {@code receivedInputs} 兜底取或。详见 {@code LightingConditions} 的注释。
 */
@EventBusSubscriber(modid = DangTools.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DangClientEvents {

    /** 上一次发给服务端的逻辑输入，用来做「只在变化时发包」。 */
    private static LightInputs lastSent = LightInputs.NONE;
    /** 上一次上报的「遥控器已激活」。 */
    private static boolean lastControllerActive;

    private DangClientEvents() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : DangKeybinds.ALL) {
            event.register(mapping);
        }
        DangTools.LOGGER.info("[dangtools] 已注册 {} 个灯光按键绑定（默认 W/S/A/D）。", DangKeybinds.ALL.length);
    }

    /** 每客户端 tick 采样一次。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        LocalPlayer player = minecraft.player;

        boolean controllerActive = isControllerActive();
        LightInputs current;
        if (minecraft.screen != null) {
            // 有界面打开时不采样（否则在界面里按 WASD 会误触发灯光）
            current = LightInputs.NONE;
        } else if (!conditionsMetLocally(player, controllerActive)) {
            // 前置条件不满足 -> 一律"没按键"
            current = LightInputs.NONE;
        } else {
            current = new LightInputs(
                    rawDown(DangKeybinds.FORWARD),
                    rawDown(DangKeybinds.BACK),
                    rawDown(DangKeybinds.LEFT),
                    rawDown(DangKeybinds.RIGHT));
        }

        // 本地镜像：无论有没有变化都写，保证客户端渲染立刻跟手
        LightInputState.setClientLocal(current);

        if (!current.same(lastSent) || controllerActive != lastControllerActive) {
            lastSent = current;
            lastControllerActive = controllerActive;
            PacketDistributor.sendToServer(new KeyInputC2SPayload(
                    current.forward(), current.back(), current.left(), current.right(),
                    controllerActive, player.getUUID()));
        }
    }

    /**
     * 客户端能判的前置条件：<b>只要坐着就行</b>。
     * <p>
     * 机主最新判定："人坐下之后，玩家本身按 WASD 就无法另外移动了，所以说只要是坐下的状态，
     * 无论手持什么物品、有没有启动，都会被视为可以激活灯光系统。"
     * <br/>→ 所以不再要求"手持并正在控制遥控器/打字机"。
     * （「载具已物理化」只有服务端知道灯在哪，由服务端校验。）
     */
    private static boolean conditionsMetLocally(LocalPlayer player, boolean controllerActive) {
        return player.isPassenger();
    }

    /**
     * Create 红石遥控器的激活态：{@code LinkedControllerClientHandler.MODE != IDLE}。
     * <p>
     * 拿不到（Create 缺失/类名变化）时返回 false —— 服务端还有
     * {@code receivedInputs} 兜底，不会因此完全失效。
     */
    private static boolean isControllerActive() {
        try {
            Object mode = com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler.MODE;
            return mode != null && !"IDLE".equals(((Enum<?>) mode).name());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 直接读【物理按键状态】，不走 {@link KeyMapping#isDown()}。
     * <p>
     * 机主实测 + 日志证据：{@code fwd/left/right} 都能收到，唯独 {@code back}(S) 永远 false，
     * 而 options.txt 里绑定是正确的。根因是 MC 的 {@code KeyMapping.MAP} 是
     * <b>「一个按键只对应一个映射」</b>——多个映射共用同一个键时只有一个能收到按下事件，
     * S 被原版 keyDown 占住了，所以我们的 BACK 永远收不到。
     * <p>
     * 这里改用 {@code InputConstants.isKeyDown(窗口句柄, 键码)} 直接问 GLFW，
     * 鼠标键则查 GLFW 鼠标按钮状态，彻底绕开那套映射冲突。
     */
    private static boolean rawDown(KeyMapping mapping) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return false;
            }
            long window = mc.getWindow().getWindow();
            InputConstants.Key key = mapping.getKey();
            if (key == null || key == InputConstants.UNKNOWN) {
                return false;
            }
            if (key.getType() == InputConstants.Type.MOUSE) {
                return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
            return InputConstants.isKeyDown(window, key.getValue());
        } catch (Throwable ignored) {
            return false;
        }
    }
}