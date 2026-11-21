package com.odmtitan;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OdmTitanMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("odmtitan");

    @Override
    public void onInitialize() {
        ModRegistry.init();

        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpinPayload.TYPE, SpinPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE,
                (payload, context) -> OdmManager.handle(context.player(), payload.action()));

        FabricDefaultAttributeRegistry.register(ModRegistry.BEAST_TITAN, BeastTitanEntity.createAttributes());

        ServerTickEvents.END_SERVER_TICK.register(OdmManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(ThrownBlocks::tick);

        LOGGER.info("ODM Gear & Beast Titan loaded");
    }
}
