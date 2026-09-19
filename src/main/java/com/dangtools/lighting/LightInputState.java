package com.dangtools.lighting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 服务端保存的「最近的驾驶输入」，以及客户端的只读副本。
 * <p>
 * <b>为什么两边都要有：</b>灯是方块、亮不亮由服务端决定（服务端权威）；
 * 客户端也要立刻跟手，所以客户端也存一份（本地采样 + 服务端广播，取其一）。
 * <p>
 * <b>关键：服务端只保存"已通过前置条件校验"的输入。</b>
 * 校验在 {@code ModNetwork.handleKeyInput} 里做（载具已物理化 + 玩家坐着 +
 * 手上正在控制遥控器/高级打字机，见 {@link LightingConditions}），
 * 不通过就写入 {@link LightInputs#NONE} —— 于是所有按键驱动的灯自然不动作，
 * 而拉杆（方块状态）的开关状态不受影响。
 * <p>
 * 本类<b>不引用任何客户端类</b>，双端都能安全加载。
 */
public final class LightInputState {

    private static volatile LightInputs serverInputs = LightInputs.NONE;
    private static volatile Player serverOwner;
    private static volatile boolean serverControllerActive;

    private static volatile LightInputs clientInputs = LightInputs.NONE;

    private LightInputState() {}

    // ---------------- 服务端 ----------------

    /** 写入服务端输入（{@code inputs} 必须是已通过前置条件校验的；不通过请传 {@code NONE}）。 */
    public static void setServer(Player player, LightInputs inputs) {
        setServer(player, inputs, false);
    }

    public static void setServer(Player player, LightInputs inputs, boolean controllerActive) {
        serverOwner = player;
        serverInputs = inputs == null ? LightInputs.NONE : inputs;
        serverControllerActive = controllerActive;
    }

    public static LightInputs serverInputs() {
        return serverInputs;
    }

    public static boolean serverControllerActive() {
        return serverControllerActive;
    }

    public static Player serverOwner() {
        return serverOwner;
    }

    public static void clearServer() {
        serverOwner = null;
        serverInputs = LightInputs.NONE;
        serverControllerActive = false;
    }

    // ---------------- 客户端 ----------------

    /** 直接写入客户端本地采样值（本地玩家自己按的键）。 */
    public static void setClientLocal(LightInputs inputs) {
        clientInputs = inputs == null ? LightInputs.NONE : inputs;
    }

    /** 写入服务端广播过来的值。 */
    public static void setClientFromServer(LightInputs inputs) {
        clientInputs = inputs == null ? LightInputs.NONE : inputs;
    }

    public static LightInputs clientInputs() {
        return clientInputs;
    }

    /**
     * 载具（或任意方块）附近的驾驶输入。
     * <p>
     * 规则：取当前端保存的那份输入，并检查「输入的所有者是不是就是离这里最近的玩家」。
     * 是才生效，否则视为没按任何键 —— 这样远处玩家按键不会影响本地载具，多个载具也不会串。
     * 用 {@link Level#getNearestPlayer} 找最近玩家，避免任何客户端类引用。
     */
    public static LightInputs inputsFor(Level level, double x, double y, double z) {
        if (level == null) {
            return LightInputs.NONE;
        }
        boolean client = level.isClientSide;
        LightInputs inputs = client ? clientInputs : serverInputs;
        if (!inputs.any()) {
            return LightInputs.NONE;
        }
        Player owner = client ? null : serverOwner;
        if (owner == null || owner.level() != level) {
            return client ? (withinRange(level, x, y, z) ? inputs : LightInputs.NONE) : LightInputs.NONE;
        }
        Player nearest = level.getNearestPlayer(x, y, z, 64.0D, false);
        return nearest != null && nearest.getUUID().equals(owner.getUUID()) ? inputs : LightInputs.NONE;
    }

    /** 客户端没有「所有者」概念，退化为「附近有玩家就生效」。 */
    private static boolean withinRange(Level level, double x, double y, double z) {
        return level.getNearestPlayer(x, y, z, 64.0D, false) != null;
    }
}
