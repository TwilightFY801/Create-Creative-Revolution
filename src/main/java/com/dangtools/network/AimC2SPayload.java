package com.dangtools.network;

import com.dangtools.DangTools;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -&gt; 服务端：调整某盏<b>大灯</b>的瞄准偏角（潜行 + 对着方块滚滚轮）。
 *
 * <p>偏角<b>只影响丁达尔光束的朝向</b>，不参与任何光照计算 —— 所以它不会触发
 * 区块光照重建（这正是"移动不卡"的关键）。
 *
 * <p>流程：客户端先本地改（即时反馈）→ 发这个包 → 服务端落到方块实体并
 * {@code sendBlockUpdated} 同步给所有客户端（别人也要看到你把灯打偏了）。
 */
public record AimC2SPayload(BlockPos pos, float yaw, float pitch) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AimC2SPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DangTools.MODID, "aim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AimC2SPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AimC2SPayload::pos,
            ByteBufCodecs.FLOAT, AimC2SPayload::yaw,
            ByteBufCodecs.FLOAT, AimC2SPayload::pitch,
            AimC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
