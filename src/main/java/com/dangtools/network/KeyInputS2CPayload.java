package com.dangtools.network;

import com.dangtools.DangTools;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -&gt; 客户端：广播「谁按了什么」。
 * <p>
 * 服务端收到 {@link KeyInputC2SPayload} 后会存下来，并把这个包发给同一维度里的其他玩家。
 * 目的：副驾驶/旁观者也能看到转向灯在闪、刹车灯在亮 —— 灯是方块，但"谁在踩油门"只有客户端知道，
 * 而客户端的方块渲染需要知道当前输入才能画出正确亮度。
 * <p>
 * {@code ownerId} 是输入所有者的玩家 UUID（用两个 long 传，避免额外依赖）。
 */
public record KeyInputS2CPayload(boolean forward, boolean back, boolean left, boolean right,
                                 long ownerMost, long ownerLeast)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KeyInputS2CPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "key_input_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeyInputS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, KeyInputS2CPayload::forward,
            ByteBufCodecs.BOOL, KeyInputS2CPayload::back,
            ByteBufCodecs.BOOL, KeyInputS2CPayload::left,
            ByteBufCodecs.BOOL, KeyInputS2CPayload::right,
            ByteBufCodecs.VAR_LONG, KeyInputS2CPayload::ownerMost,
            ByteBufCodecs.VAR_LONG, KeyInputS2CPayload::ownerLeast,
            KeyInputS2CPayload::new);

    public static KeyInputS2CPayload of(boolean forward, boolean back, boolean left, boolean right,
                                        java.util.UUID owner) {
        return new KeyInputS2CPayload(forward, back, left, right,
                owner == null ? 0L : owner.getMostSignificantBits(),
                owner == null ? 0L : owner.getLeastSignificantBits());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
