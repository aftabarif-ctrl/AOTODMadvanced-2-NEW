package com.odmtitan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Summons a Beast Titan about 14 blocks in front of you. (You can also use /summon odmtitan:beast_titan) */
public class BeastHornItem extends Item {
    public BeastHornItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel sl)) {
            return InteractionResult.SUCCESS;
        }
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        BlockPos top = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(sp.getX() + flat.x * 14, sp.getY(), sp.getZ() + flat.z * 14));

        BeastTitanEntity titan = ModRegistry.BEAST_TITAN.create(sl, EntitySpawnReason.SPAWN_ITEM_USE);
        if (titan == null) return InteractionResult.FAIL;
        titan.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
        titan.setYRot(sp.getYRot() + 180F);
        sl.addFreshEntity(titan);

        sl.playSound(null, top.getX(), top.getY(), top.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.5F);
        ItemStack stack = player.getItemInHand(hand);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
