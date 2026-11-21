package com.odmtitan;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Reads/writes the dedicated ODM slot + gas tank stored on the player. */
public final class OdmData {
    /** Full tank. Hooks cost 1/tick each, boost 2/tick -> several minutes of continuous flight. */
    public static final int MAX_GAS = 20000;
    /** One Gas Canister refills 25 %. */
    public static final int CANISTER_GAS = MAX_GAS / 4;

    private OdmData() {}

    public static boolean isEquipped(ServerPlayer p) {
        return p.getAttachedOrElse(ModRegistry.GEAR_EQUIPPED, false);
    }

    public static void setEquipped(ServerPlayer p, boolean v) {
        p.setAttached(ModRegistry.GEAR_EQUIPPED, v);
    }

    public static int gas(ServerPlayer p) {
        return p.getAttachedOrElse(ModRegistry.GAS, MAX_GAS);
    }

    public static void setGas(ServerPlayer p, int v) {
        p.setAttached(ModRegistry.GAS, Math.max(0, Math.min(MAX_GAS, v)));
    }

    public static int percent(ServerPlayer p) {
        return Math.round(gas(p) * 100f / MAX_GAS);
    }

    /** Removes gas; returns false (and removes nothing) when the tank is empty. */
    public static boolean drain(ServerPlayer p, int amount) {
        int g = gas(p);
        if (g <= 0) return false;
        setGas(p, g - amount);
        return true;
    }

    /** "ODM GAS [||||||||||||......] 63%" on the action bar, coloured by how much is left. */
    public static void showGas(ServerPlayer p) {
        int pct = percent(p);
        int filled = Math.round(pct / 5f);
        String bar = "|".repeat(filled) + ".".repeat(20 - filled);
        ChatFormatting colour = pct > 50 ? ChatFormatting.GREEN : pct > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        String text = pct <= 0 ? "ODM GAS [EMPTY] 0%" : "ODM GAS [" + bar + "] " + pct + "%";
        p.sendSystemMessage(Component.literal(text).withStyle(colour), true);
    }
}
