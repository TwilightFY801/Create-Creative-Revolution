package com.dangtools.network;

import com.dangtools.DangTools;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端按键状态同步包（客户端 -> 服务端）。
 * <p>
 * 灯光系统全部在服务端判定（灯是方块状态），但"玩家按没按前进/后退/左/右"只有客户端知道，
 * 所以按标准做法：客户端每 tick 采样一次，状态变化时发这个包；服务端存进
 * {@link KeyStateHandler}，供各种灯读取。
 */
public record KeyStatePayload(boolean forward, boolean back, boolean left, boolean right)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KeyStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "key_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeyStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, KeyStatePayload::forward,
            ByteBufCodecs.BOOL, KeyStatePayload::back,
            ByteBufCodecs.BOOL, KeyStatePayload::left,
            ByteBufCodecs.BOOL, KeyStatePayload::right,
            KeyStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
