package com.odmtitan;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: start the visible 360 degree camera spin for this many ticks. */
public record SpinPayload(int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SpinPayload> TYPE =
            new CustomPacketPayload.Type<>(ModRegistry.id("spin"));
    public static final StreamCodec<ByteBuf, SpinPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SpinPayload::ticks, SpinPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
