package com.odmtitan.client;

import com.odmtitan.ActionPayload;
import com.odmtitan.ModRegistry;
import com.odmtitan.SpinPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public class OdmTitanClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ModRegistry.id("main"));

    // GLFW key codes (Z, X, V, C, K)
    public static final KeyMapping HOOK_L = key("hook_left", 90);
    public static final KeyMapping HOOK_R = key("hook_right", 88);
    public static final KeyMapping BOOST = key("boost", 86);
    public static final KeyMapping SPIN = key("spin", 67);
    public static final KeyMapping UNEQUIP = key("unequip", 75);

    private static final boolean[] WAS = new boolean[3];
    private static int spinLeft;
    private static int spinTotal;

    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.odmtitan." + name, code, CATEGORY));
    }

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModRegistry.BEAST_TITAN, BeastTitanRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(SpinPayload.TYPE, (payload, context) -> {
            spinLeft = payload.ticks();
            spinTotal = Math.max(1, payload.ticks());
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean active = mc.player != null && mc.mouseHandler.isMouseGrabbed();

            hold(0, HOOK_L, 1, 2, active);
            hold(1, HOOK_R, 3, 4, active);
            hold(2, BOOST, 5, 6, active);

            while (SPIN.consumeClick()) {
                if (active) send(7);
            }
            while (UNEQUIP.consumeClick()) {
                if (active) send(8);
            }

            // Visible 360 degree spin while the server runs the Levi strike
            if (spinLeft > 0 && mc.player != null) {
                mc.player.setYRot(mc.player.getYRot() + 360.0F / spinTotal);
                spinLeft--;
            }
        });
    }

    private static void hold(int idx, KeyMapping key, int downId, int upId, boolean active) {
        boolean down = active && key.isDown();
        if (down != WAS[idx]) {
            WAS[idx] = down;
            send(down ? downId : upId);
        }
    }

    private static void send(int action) {
        ClientPlayNetworking.send(new ActionPayload(action));
    }
}
