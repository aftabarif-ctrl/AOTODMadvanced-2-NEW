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

/**
 * ODM Gear is NOT armour: right-click to slot it into your dedicated ODM slot
 * (your chest / armour slots stay free). Press the "Unequip" key to take it back out.
 */
public class OdmGearItem extends Item {
    public OdmGearItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (OdmData.isEquipped(sp)) {
            sp.sendSystemMessage(Component.translatable("message.odmtitan.already_equipped"), true);
            return InteractionResult.FAIL;
        }
        OdmData.setEquipped(sp, true);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
        sp.sendSystemMessage(Component.translatable("message.odmtitan.equipped"), false);
        OdmData.showGas(sp);
        return InteractionResult.SUCCESS;
    }
}
