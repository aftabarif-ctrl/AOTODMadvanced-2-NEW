package com.odmtitan;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a key was pressed / released. See {@link OdmManager#handle}. */
public record ActionPayload(int action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ModRegistry.id("action"));
    public static final StreamCodec<ByteBuf, ActionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ActionPayload::action, ActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
