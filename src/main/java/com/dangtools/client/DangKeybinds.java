package com.dangtools.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 灯光系统的四个按键：前进 / 后退 / 左 / 右。
 * <p>
 * <b>默认绑 W / S / A / D</b>（玩家不设置就能直接用）。
 * 这四个绑定是"属于本模组自己的一套"，不去改也不去复用红石控制器的绑定；
 * 照明拉杆的配置界面改的就是这四个（通过 {@code KeyMapping.setKey} + {@code Options.save()}）。
 * <p>
 * 客户端每 tick 把它们的按下状态翻译成「逻辑输入」再发给服务端
 * （见 {@code DangClientEvents} 与 {@code KeyInputC2SPayload}），
 * 所以服务端不需要知道具体绑了哪个键。
 */
public final class DangKeybinds {

    public static final String CATEGORY = "key.categories.dangtools";

    /** 默认键码（配置界面的「恢复默认」要用）。 */
    public static final int DEFAULT_FORWARD = GLFW.GLFW_KEY_W;
    public static final int DEFAULT_BACK = GLFW.GLFW_KEY_S;
    public static final int DEFAULT_LEFT = GLFW.GLFW_KEY_A;
    public static final int DEFAULT_RIGHT = GLFW.GLFW_KEY_D;

    public static final KeyMapping FORWARD = new KeyMapping(
            "key.dangtools.forward", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            DEFAULT_FORWARD, CATEGORY);
    public static final KeyMapping BACK = new KeyMapping(
            "key.dangtools.back", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            DEFAULT_BACK, CATEGORY);
    public static final KeyMapping LEFT = new KeyMapping(
            "key.dangtools.left", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            DEFAULT_LEFT, CATEGORY);
    public static final KeyMapping RIGHT = new KeyMapping(
            "key.dangtools.right", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            DEFAULT_RIGHT, CATEGORY);

    public static final KeyMapping[] ALL = { FORWARD, BACK, LEFT, RIGHT };

    private DangKeybinds() {}
}
