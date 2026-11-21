package com.odmtitan;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Beast Titan: huge, slow, tanky. Smashes through blocks in its way and, from range, rips blocks out
 * of the ground and hurls them at very high speed (see {@link ThrownBlocks}).
 */
public class BeastTitanEntity extends PathfinderMob {
    /** Set to false if you do not want the Beast Titan to break blocks while walking. */
    public static final boolean BREAKS_BLOCKS = true;

    public BeastTitanEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.MOVEMENT_SPEED, 0.16)        // slow (zombie = 0.23)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.SCALE, 4.5);                 // ~8.8 blocks tall
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ThrowBlockGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.9, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 32.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (BREAKS_BLOCKS && level() instanceof ServerLevel sl && isAlive()
                && tickCount % 8 == 0 && getTarget() != null) {
            smashBlocksInWay(sl);
        }
    }

    /** Breaks every (non-bedrock, non-obsidian) block inside the titan's body so it can plough through terrain. */
    private void smashBlocksInWay(ServerLevel sl) {
        AABB bb = getBoundingBox().inflate(0.6, 0.0, 0.6);
        int minY = Mth.floor(bb.minY);
        int maxY = Mth.floor(bb.minY + getBbHeight() * 0.6);
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(bb.minX), minY, Mth.floor(bb.minZ),
                Mth.floor(bb.maxX), maxY, Mth.floor(bb.maxZ))) {
            BlockState st = sl.getBlockState(pos);
            if (st.isAir()) continue;
            float hardness = st.getDestroySpeed(sl, pos);
            if (hardness < 0 || hardness > 30) continue;
            sl.destroyBlock(pos, false, this);
            if (++broken >= 80) break;
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    // =============================================================================================
    //  Goal: wind up, then hurl a volley of ground blocks at the target
    // =============================================================================================
    static class ThrowBlockGoal extends Goal {
        private static final int WINDUP = 30;
        private static final int BETWEEN_THROWS = 9;

        private final BeastTitanEntity titan;
        private int cooldown = 60;
        private int timer;
        private int throwsLeft;

        ThrowBlockGoal(BeastTitanEntity titan) {
            this.titan = titan;
            setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = titan.getTarget();
            if (t == null || !t.isAlive()) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            double d = titan.distanceTo(t);
            return d >= 8.0 && d <= 56.0;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = titan.getTarget();
            return throwsLeft > 0 && t != null && t.isAlive();
        }

        @Override
        public void start() {
            timer = WINDUP;
            throwsLeft = 3;
            titan.playSound(SoundEvents.RAVAGER_AMBIENT, 4.0F, 0.5F);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = titan.getTarget();
            if (t == null) {
                throwsLeft = 0;
                return;
            }
            titan.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (timer > 0) {
                timer--;
                return;
            }
            if (titan.level() instanceof ServerLevel sl) {
                ThrownBlocks.launch(sl, titan, t);
            }
            throwsLeft--;
            timer = BETWEEN_THROWS;
        }

        @Override
        public void stop() {
            throwsLeft = 0;
            cooldown = 80 + titan.random.nextInt(60);
        }
    }
}
