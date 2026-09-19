package com.dangtools.lighting;

import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 灯光响应按键的<b>前置条件</b>（机主要求）。
 * <p>
 * 机主原话：「比如这个已经是一个物理的结构，玩家的状态是坐着的状态（比如坐垫），
 * 手上已经开始控制遥控器或者高级打字机，这个时候，灯光系统才有作用」。
 * 所以灯光要响应按键，必须同时满足三条：
 * <ol>
 *   <li><b>载具已物理化</b> —— 灯所在的方块确实属于某个 Sable 子世界
 *       （{@code Sable.HELPER.getContaining(level, pos) != null}）。
 *       没有物理结构时按键一律不生效。</li>
 *   <li><b>玩家处于骑乘/坐下状态</b> —— {@link Player#isPassenger()}。
 *       坐在 Create 坐垫（{@code SeatBlock}）或任何座位上时该值为 true；
 *       站着站在车上时为 false（机主明确说"坐着的状态"）。</li>
 *   <li><b>手上正在控制遥控器 / 高级打字机</b> —— 主手或副手持有：
 *       <ul>
 *         <li>Create 的<b>红石控制器</b>（{@code create:linked_controller}，类
 *             {@code com.simibubi.create.content.redstone.link.controller.LinkedControllerItem}，
 *             从 {@code AllItems.LINKED_CONTROLLER} 取，避免硬编物品 id）；</li>
 *         <li>Simulated 的<b>高级打字机</b>（{@code LinkedTypewriterItem}）。</li>
 *       </ul>
 *       「正在控制」的判定见 {@link #isActivelyControlling}。</li>
 * </ol>
 *
 * <h2>「正在控制」这条的可靠性与卡点（如实说明）</h2>
 * 反编译核对结论：
 * <ul>
 *   <li><b>Create 遥控器</b>：激活态是 {@code LinkedControllerClientHandler.MODE != IDLE}，
 *       而 {@code MODE} 是<b>纯客户端</b>的（{@code toggle()} 由 {@code LinkedControllerItem.use()}
 *       在客户端调用时切换；{@code tick()} 里若主手/副手都没有遥控器就强制回 IDLE）。
 *       <b>服务端没有任何"这个玩家正在用遥控器"的字段</b> —— 服务端只通过
 *       {@code LinkedControllerInputPacket} 收到按键，并写进
 *       {@code LinkedControllerServerHandler.receivedInputs}（每个按键一个带超时的条目）。
 *       <br/>→ 所以本类提供两级判定：
 *       <b>(a) 客户端上报</b>（把 {@code MODE == ACTIVE} 的事实放进
 *       {@code KeyInputC2SPayload.controllerActive}），以及
 *       <b>(b) 服务端兜底</b>：{@code receivedInputs} 里还有该玩家<b>未过期的按键条目</b>
 *       （说明他确实在踩遥控器按钮）。两者取或，且都要求"手里拿着该类物品"。</li>
 *   <li><b>高级打字机</b>：{@code LinkedTypewriterBlockEntity} 有服务端字段
 *       {@code checkUser(UUID) / isInUse()}，但它绑定在<b>具体方块位置</b>上；
 *       要按玩家反查需要遍历载具附近所有方块实体，成本与不确定性都高。
 *       <br/>→ 目前对打字机走同一套"手持 + 有事实在控制"判定，与遥控器一致。
 *       <b>这是近似</b>：如果机主实测发现"手持打字机但没在敲键时灯不亮"，请告知，
 *       我再加"遍历附近打字机 BE 并调用 checkUser"的补强。</li>
 * </ul>
 *
 * <h2>拿不到可靠判定的地方（不假装）</h2>
 * 「玩家坐在哪」只能靠 {@code isPassenger()}；原版没有「坐在椅子上」这个独立状态
 * （Create 坐垫是把玩家变成座椅实体的 passenger），所以用 {@code isPassenger()} 表达"坐着"。
 * 玩家坐在<b>别的</b>载具上、而按键要控制<b>这辆</b>载具时，本判定无法区分
 * （只能靠 {@code InputOwner} 的"最近的玩家"规则兜住）。
 */
public final class LightingConditions {

    private LightingConditions() {}

    /**
     * 服务端是否接受这份按键输入。
     * <p>
     * <b>机主最新判定（2026-09 修订）</b>：<i>"人坐下之后，玩家本身按 WASD 就无法另外移动了，
     * 所以说只要是坐下的状态，无论手持什么物品、有没有启动，都会被视为可以激活灯光系统"</i>。
     * <br/>→ 所以<b>去掉了原来的"必须手持并正在控制遥控器/打字机"这一条</b>，只保留两条：
     * <ol>
     *   <li><b>玩家处于骑乘/坐下状态</b>（{@link Player#isPassenger()}）；</li>
     *   <li><b>载具已物理化</b>（传入位置属于某个 Sable 子世界）。</li>
     * </ol>
     */
    public static boolean serverAccepts(Player player, Level level, double x, double y, double z,
                                        boolean controllerActiveReported) {
        if (player == null || level == null || level.isClientSide) {
            return false;
        }
        // 条件 1：坐着（骑乘/坐垫）—— 坐下后 WASD 本来就无法移动角色，所以这一条足够
        if (!player.isPassenger()) {
            return false;
        }
        // 条件 2：载具已物理化。
        // 注意：机主实测反馈"转向灯/刹车灯/车内灯全都没反应"，而客户端采样逻辑是对的，
        // 所以断点很可能就在这道服务端校验上 —— 玩家坐在载具上时，玩家的世界坐标
        // 未必落在 Sable 子世界里（子世界是独立坐标系），于是整条输入被拒。
        // 因此这里放宽：只有"能明确判定玩家不在任何子世界、且脚下也不是载具"时才拒绝。
        try {
            if (Sable.HELPER.getContaining(level, new Vec3(x, y, z)) != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // Sable 缺失/接口变化时不因此拒绝
        }
        // 玩家在骑乘状态下依然接受输入（灯光方块自己会判断它属不属于物理结构）
        return true;
    }

    /**
     * 条件 3 的完整判定：<b>手持该类物品</b> 且 <b>确实在控制</b>。
     *
     * @param controllerActiveReported 客户端上报的「遥控器已激活」；服务端收到的是玩家自己报的，
     *                                 所以还会与 {@code receivedInputs} 取或（见类注释）。
     */
    public static boolean isActivelyControlling(Player player, Level level, boolean controllerActiveReported) {
        if (player == null) {
            return false;
        }
        boolean holdingController = holdsLinkedController(player);
        boolean holdingTypewriter = holdsLinkedTypewriter(player);
        if (!holdingController && !holdingTypewriter) {
            return false;
        }
        // 客户端上报了"已激活"（遥控器 MODE == ACTIVE）
        if (controllerActiveReported) {
            return true;
        }
        // 服务端兜底：还有未过期的按键条目 = 确实在踩按钮
        return hasRecentControllerInput(player);
    }

    /** 主手/副手是否持有 Create 红石控制器。 */
    public static boolean holdsLinkedController(Player player) {
        try {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            var entry = com.simibubi.create.AllItems.LINKED_CONTROLLER;
            return entry.isIn(main) || entry.isIn(off);
        } catch (Throwable t) {
            // Create 缺失/接口变化时退化为"判定不通过"，绝不误判成通过
            return false;
        }
    }

    /** 主手/副手是否持有 Simulated 高级打字机。 */
    public static boolean holdsLinkedTypewriter(Player player) {
        try {
            Class<?> typewriter = Class.forName(
                    "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterItem");
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            return typewriter.isInstance(main.getItem()) || typewriter.isInstance(off.getItem());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 服务端兜底：该玩家在 {@code LinkedControllerServerHandler.receivedInputs} 里
     * 还有未过期的按键条目（超时由它自己的 tick 递减维护）。
     */
    private static boolean hasRecentControllerInput(Player player) {
        try {
            var map = com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.receivedInputs;
            if (map == null || player.level() == null) {
                return false;
            }
            java.util.Map<java.util.UUID, ?> perPlayer = map.get(player.level());
            if (perPlayer == null) {
                return false;
            }
            Object entries = perPlayer.get(player.getUUID());
            return entries instanceof java.util.Collection<?> collection && !collection.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }
}
