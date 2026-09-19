package com.dangtools.network;

import com.dangtools.DangTools;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -&gt; 服务端：当前四个逻辑输入（前进/后退/左/右）的按下状态。
 * <p>
 * 客户端每 tick 采样一次自己那套「灯光按键绑定」（默认 W/S/A/D，可在照明拉杆的配置界面改），
 * <b>只在状态变化时发包</b>（见 {@code com.dangtools.client.DangClientEvents}）。
 * <p>
 * <b>字段是「逻辑输入」而不是具体键码</b>：客户端负责把物理键翻译成逻辑值，
 * 服务端只认逻辑值 —— 这样改按键绑定不需要通知服务端。
 * <p>
 * <b>为什么还要带 {@code owner} 与 {@code controllerActive}：</b>
 * 灯光响应按键有<b>前置条件</b>（载具已物理化 + 玩家处于骑乘/坐下状态 +
 * 手上正在控制遥控器/高级打字机）。服务端需要知道「这份输入是谁的」才能独立校验；
 * 而 Create 遥控器的「已激活」状态只有客户端知道（{@code LinkedControllerClientHandler.MODE}），
 * 服务端判不了，所以由客户端把 {@code controllerActive} 一起报上来。
 */
public record KeyInputC2SPayload(boolean forward, boolean back, boolean left, boolean right,
                                 boolean controllerActive, java.util.UUID owner)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KeyInputC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "key_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeyInputC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, KeyInputC2SPayload::forward,
            ByteBufCodecs.BOOL, KeyInputC2SPayload::back,
            ByteBufCodecs.BOOL, KeyInputC2SPayload::left,
            ByteBufCodecs.BOOL, KeyInputC2SPayload::right,
            ByteBufCodecs.BOOL, KeyInputC2SPayload::controllerActive,
            UUIDUtil.STREAM_CODEC, KeyInputC2SPayload::owner,
            KeyInputC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
