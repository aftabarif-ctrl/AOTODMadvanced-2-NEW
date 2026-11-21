package com.odmtitan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blocks the Beast Titan tears out of the ground are real FallingBlockEntity objects that fly in a
 * straight, very fast line (no gravity). This class tracks them, detects impacts, and applies damage
 * plus a small crater.
 */
public final class ThrownBlocks {
    static final double SPEED = 2.6;           // blocks/tick (~52 blocks/s)
    static final float DIRECT_DAMAGE = 18.0F;
    static final float SPLASH_DAMAGE = 10.0F;

    private static final class Thrown {
        final FallingBlockEntity entity;
        final ServerLevel level;
        final UUID owner;
        final Vec3 vel;
        final BlockState state;
        int age;

        Thrown(FallingBlockEntity e, ServerLevel level, UUID owner, Vec3 vel, BlockState state) {
            this.entity = e;
            this.level = level;
            this.owner = owner;
            this.vel = vel;
            this.state = state;
        }
    }

    private static final List<Thrown> ACTIVE = new ArrayList<>();

    private ThrownBlocks() {}

    /** Rips a block out of the ground near the titan and throws it at the target. */
    public static void launch(ServerLevel sl, BeastTitanEntity titan, LivingEntity target) {
        RandomSource rnd = sl.getRandom();
        BlockPos base = titan.blockPosition();
        BlockPos source = null;
        BlockState state = null;
        for (int tries = 0; tries < 40 && source == null; tries++) {
            BlockPos p = base.offset(rnd.nextInt(11) - 5, -1 - rnd.nextInt(2),
                    rnd.nextInt(11) - 5);
            BlockState s = sl.getBlockState(p);
            if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) continue;
            float hardness = s.getDestroySpeed(sl, p);
            if (hardness < 0 || hardness > 25) continue;
            source = p;
            state = s;
        }
        if (source == null) return;

        FallingBlockEntity fb = FallingBlockEntity.fall(sl, source, state);   // removes the block + spawns entity
        if (fb == null) return;

        Vec3 hand = titan.position().add(0, titan.getBbHeight() * 0.85, 0);
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 dir = aim.subtract(hand).normalize();
        dir = dir.add((rnd.nextDouble() - 0.5) * 0.04, (rnd.nextDouble() - 0.5) * 0.03,
                (rnd.nextDouble() - 0.5) * 0.04).normalize();
        hand = hand.add(dir.scale(titan.getBbWidth() * 0.9));

        Vec3 vel = dir.scale(SPEED);
        fb.setPos(hand.x, hand.y, hand.z);
        fb.setNoGravity(true);
        fb.setDeltaMovement(vel);
        syncMotion(sl, fb);
        ACTIVE.add(new Thrown(fb, sl, titan.getUUID(), vel, state));

        sl.playSound(null, hand.x, hand.y, hand.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 3.0F, 0.5F);
        sl.sendParticles(ParticleTypes.POOF, hand.x, hand.y, hand.z, 12, 0.5, 0.5, 0.5, 0.05);
    }

    private static void syncMotion(ServerLevel sl, Entity e) {
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(e) < 160.0 * 160.0) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(e));
            }
        }
    }

    public static void tick(MinecraftServer server) {
        Iterator<Thrown> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Thrown t = it.next();
            FallingBlockEntity e = t.entity;
            if (e.isRemoved()) {
                it.remove();
                continue;
            }
            t.age++;
            Vec3 from = e.position();
            Vec3 to = from.add(t.vel);

            BlockHitResult bh = t.level.clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            boolean blockHit = bh.getType() != HitResult.Type.MISS;
            Vec3 end = blockHit ? bh.getLocation() : to;

            AABB sweep = e.getBoundingBox().expandTowards(end.subtract(from)).inflate(0.6);
            List<LivingEntity> hit = t.level.getEntitiesOfClass(LivingEntity.class, sweep,
                    x -> x.isAlive() && !x.isSpectator() && !(x instanceof BeastTitanEntity));

            if (blockHit || !hit.isEmpty() || t.age > 80) {
                impact(t, hit, blockHit ? bh.getLocation() : (hit.isEmpty() ? from : end));
                e.discard();
                it.remove();
            } else {
                e.setDeltaMovement(t.vel);   // keep it at full speed
            }
        }
    }

    private static void impact(Thrown t, List<LivingEntity> direct, Vec3 at) {
        ServerLevel sl = t.level;
        Entity ownerEntity = sl.getEntity(t.owner);
        DamageSource src = ownerEntity instanceof LivingEntity ol
                ? sl.damageSources().mobAttack(ol)
                : sl.damageSources().generic();

        // damage
        for (LivingEntity le : direct) {
            le.hurtServer(sl, src, DIRECT_DAMAGE);
        }
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.5),
                x -> x.isAlive() && !(x instanceof BeastTitanEntity) && !direct.contains(x))) {
            le.hurtServer(sl, src, SPLASH_DAMAGE);
        }
        // knockback along the flight direction
        Vec3 push = t.vel.normalize().scale(1.1).add(0, 0.45, 0);
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0),
                x -> x.isAlive() && !(x instanceof BeastTitanEntity))) {
            le.setDeltaMovement(le.getDeltaMovement().add(push));
            if (le instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }

        // crater
        BlockPos c = BlockPos.containing(at.x + t.vel.normalize().x * 0.5, at.y, at.z + t.vel.normalize().z * 0.5);
        int broken = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
            if (p.distSqr(c) > 5.0) continue;
            BlockState s = sl.getBlockState(p);
            if (s.isAir()) continue;
            float hardness = s.getDestroySpeed(sl, p);
            if (hardness < 0 || hardness > 25) continue;
            sl.destroyBlock(p, false, ownerEntity);
            if (++broken >= 24) break;
        }

        // effects
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, t.state),
                at.x, at.y, at.z, 50, 0.7, 0.7, 0.7, 0.2);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0.0);
        sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 20, 0.6, 0.6, 0.6, 0.1);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.5F, 0.6F);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0F, 0.5F);
    }
}
