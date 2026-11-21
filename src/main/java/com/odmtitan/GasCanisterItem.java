package com.odmtitan;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-click to refill 25 % of the ODM gas tank. */
public class GasCanisterItem extends Item {
    public GasCanisterItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!OdmData.isEquipped(sp)) {
            sp.sendSystemMessage(Component.translatable("message.odmtitan.need_gear"), true);
            return InteractionResult.FAIL;
        }
        if (OdmData.gas(sp) >= OdmData.MAX_GAS) {
            sp.sendSystemMessage(Component.translatable("message.odmtitan.tank_full"), true);
            return InteractionResult.FAIL;
        }
        OdmData.setGas(sp, OdmData.gas(sp) + OdmData.CANISTER_GAS);
        ItemStack stack = player.getItemInHand(hand);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.7F, 0.6F);
        OdmData.showGas(sp);
        return InteractionResult.SUCCESS;
    }
}
