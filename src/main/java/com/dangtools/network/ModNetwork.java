package com.dangtools.network;

import com.dangtools.DangTools;
import com.dangtools.lighting.LightInputState;
import com.dangtools.lighting.LightInputs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络接线（灯光系统）：
 * <ul>
 *   <li>{@link KeyInputC2SPayload}（客户端 -&gt; 服务端）：四个逻辑输入的按下状态；</li>
 *   <li>{@link KeyInputS2CPayload}（服务端 -&gt; 客户端）：广播「谁按了什么」，让别人的客户端也能画对灯光；</li>
 *   <li>{@link KeyStatePayload}（上一版留下的 key_state 包）：一并接线复用，
 *       处理器顺带写入 {@link KeyStateHandler}，保持那套基础设施可用。</li>
 * </ul>
 */
@EventBusSubscriber(modid = DangTools.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {

    /** 协议版本号；改动包结构时升一下，避免旧客户端连新服务端。 */
    public static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // 上一版留下的 key_state 包，照旧注册
        registrar.playToServer(KeyStatePayload.TYPE, KeyStatePayload.CODEC, ModNetwork::handleKeyState);

        // 新的逻辑输入包
        registrar.playToServer(KeyInputC2SPayload.TYPE, KeyInputC2SPayload.CODEC, ModNetwork::handleKeyInput);
        registrar.playToClient(KeyInputS2CPayload.TYPE, KeyInputS2CPayload.CODEC, ModNetwork::handleKeySync);
        // 大灯瞄准偏角（潜行 + 对着方块滚滚轮）
        registrar.playToServer(AimC2SPayload.TYPE, AimC2SPayload.CODEC, ModNetwork::handleAim);
    }

    // ---------------- 旧的 key_state ----------------

    private static void handleKeyState(KeyStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                KeyStateHandler.update(player, new KeyStateHandler.Keys(
                        payload.forward(), payload.back(), payload.left(), payload.right()));
            }
        });
    }

    // ---------------- 新的逻辑输入 ----------------

    private static void handleKeyInput(KeyInputC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            LightInputs inputs = new LightInputs(payload.forward(), payload.back(), payload.left(), payload.right());

            // ---- 前置条件校验（机主要求的三条）----
            // ① 载具已物理化 ② 玩家坐着 ③ 手上正在控制遥控器/高级打字机。
            // 不满足就把这份输入当成"没按键"；拉杆的开关状态不受影响。
            // 判定位置取玩家自身位置：玩家坐在载具上时，该位置就在载具的子世界里。
            boolean accepted = inputs.any() && com.dangtools.lighting.LightingConditions.serverAccepts(
                    player, player.level(),
                    player.getX(), player.getY(), player.getZ(),
                    payload.controllerActive());

            // 记录「最近一次输入的发送者」与「是否处于遥控状态」，供按键驱动的灯判断
            LightInputState.setServer(player, accepted ? inputs : LightInputs.NONE, payload.controllerActive());

            KeyStateHandler.update(player, new KeyStateHandler.Keys(
                    payload.forward(), payload.back(), payload.left(), payload.right()));

            // 广播给同维度的其他玩家（发给自己也没关系，客户端会覆盖成同样的值）
            if (player.level() instanceof ServerLevel serverLevel) {
                KeyInputS2CPayload out = KeyInputS2CPayload.of(
                        payload.forward(), payload.back(), payload.left(), payload.right(), player.getUUID());
                PacketDistributor.sendToPlayersInDimension(serverLevel, out);
            }
        });
    }

    private static void handleKeySync(KeyInputS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LightInputState.setClientFromServer(new LightInputs(
                payload.forward(), payload.back(), payload.left(), payload.right())));
    }
    /** 大灯瞄准：客户端把偏角发上来，服务端落到 BE 并同步给所有客户端。 */
    private static void handleAim(AimC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos())
                    instanceof com.dangtools.lighting.DangLightBlockEntity be)) {
                return;
            }
            // 距离校验：防止远程改别人的灯
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) > 100.0D) {
                return;
            }
            be.setAim(payload.yaw(), payload.pitch());
        });
    }
}