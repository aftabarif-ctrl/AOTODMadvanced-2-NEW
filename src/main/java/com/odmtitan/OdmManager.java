package com.odmtitan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side ODM gear: grappling hooks, gas boost, gas drain, and the Levi-style spinning strike.
 *
 * Action ids sent by the client (see ActionPayload):
 *   1/2 left hook down/up   3/4 right hook down/up   5/6 boost on/off   7 Levi spin   8 unequip gear
 */
public final class OdmManager {
    // ---- tuning --------------------------------------------------------------------------------
    static final double HOOK_RANGE = 48.0;
    static final double REEL_ACCEL = 0.11;      // blocks/tick^2 per hook
    static final double BOOST_ACCEL = 0.085;
    static final double MAX_SPEED = 1.9;        // blocks/tick (~38 blocks/s)
    static final int HOOK_GAS_PER_TICK = 1;     // per attached hook while reeling
    static final int BOOST_GAS_PER_TICK = 2;
    static final int SPIN_GAS = 800;            // 4 % of the tank
    static final int SPIN_TICKS = 16;
    static final int SPIN_COOLDOWN = 50;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class Hook {
        boolean active;
        Vec3 point = Vec3.ZERO;
        int entityId = -1;
        Vec3 offset = Vec3.ZERO;
    }

    private static final class State {
        final Hook[] hooks = {new Hook(), new Hook()};
        boolean boosting;
        Vec3 lastPos;
        int tick;
        int safeTicks;      // no fall damage for a moment after using ODM
        int spinLeft;
        int spinTotal;
        int targetId = -1;
        int cooldown;
        boolean spinFlip;
    }

    private OdmManager() {}

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    // =============================================================================================
    //  Input
    // =============================================================================================
    public static void handle(ServerPlayer p, int action) {
        State st = state(p);
        switch (action) {
            case 1 -> setHook(p, st, 0, true);
            case 2 -> st.hooks[0].active = false;
            case 3 -> setHook(p, st, 1, true);
            case 4 -> st.hooks[1].active = false;
            case 5 -> {
                if (OdmData.isEquipped(p)) st.boosting = true;
                else p.sendSystemMessage(Component.translatable("message.odmtitan.need_gear"), true);
            }
            case 6 -> st.boosting = false;
            case 7 -> startSpin(p, st);
            case 8 -> unequip(p, st);
            default -> { }
        }
    }

    private static void release(State st) {
        st.hooks[0].active = false;
        st.hooks[1].active = false;
        st.boosting = false;
    }

    private static void unequip(ServerPlayer p, State st) {
        if (!OdmData.isEquipped(p)) return;
        release(st);
        st.spinLeft = 0;
        OdmData.setEquipped(p, false);
        ItemStack gear = new ItemStack(ModRegistry.ODM_GEAR);
        if (!p.getInventory().add(gear)) p.drop(gear, false);
        p.sendSystemMessage(Component.translatable("message.odmtitan.unequipped"), true);
    }

    // =============================================================================================
    //  Hooks
    // =============================================================================================
    private static void setHook(ServerPlayer p, State st, int idx, boolean down) {
        if (!OdmData.isEquipped(p)) {
            p.sendSystemMessage(Component.translatable("message.odmtitan.need_gear"), true);
            return;
        }
        ServerLevel level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(HOOK_RANGE));

        BlockHitResult bh = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        boolean blockHit = bh.getType() != HitResult.Type.MISS;
        double maxD = blockHit ? eye.distanceTo(bh.getLocation()) : HOOK_RANGE;
        Vec3 rayEnd = eye.add(look.scale(maxD));
        AABB box = p.getBoundingBox().expandTowards(look.scale(maxD)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, rayEnd, box,
                e -> !e.isSpectator() && e.isPickable() && e != p, maxD * maxD);

        Hook h = st.hooks[idx];
        if (eh != null) {
            Entity t = eh.getEntity();
            h.entityId = t.getId();
            h.point = eh.getLocation();
            h.offset = eh.getLocation().subtract(t.position());
        } else if (blockHit) {
            h.entityId = -1;
            h.point = bh.getLocation();
        } else {
            return; // nothing in range
        }
        h.active = true;
        sound(level, p.position(), SoundEvents.CROSSBOW_SHOOT, 0.8F, 1.3F);
    }

    // =============================================================================================
    //  Per-tick update
    // =============================================================================================
    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State st = state(p);
            st.tick++;
            tickPlayer(p, st);
        }
        if (server.getTickCount() % 200 == 0) {
            STATES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
    }

    private static void tickPlayer(ServerPlayer p, State st) {
        if (!p.isAlive() || p.isSpectator() || !OdmData.isEquipped(p)) {
            release(st);
            st.spinLeft = 0;
            st.lastPos = p.position();
            return;
        }
        if (st.cooldown > 0) st.cooldown--;
        if (st.safeTicks > 0) {
            st.safeTicks--;
            p.fallDistance = 0;
        }

        Vec3 pos = p.position();
        Vec3 vel = st.lastPos == null ? Vec3.ZERO : pos.subtract(st.lastPos);
        st.lastPos = pos;

        if (st.spinLeft > 0) {
            tickSpin(p, st);
            return;
        }

        ServerLevel level = p.level();
        Vec3 body = pos.add(0, p.getBbHeight() * 0.6, 0);
        Vec3 accel = Vec3.ZERO;
        int attached = 0;

        for (Hook h : st.hooks) {
            if (!h.active) continue;
            Vec3 anchor = h.point;
            if (h.entityId >= 0) {
                Entity t = level.getEntity(h.entityId);
                if (t == null || !t.isAlive()) {
                    h.active = false;
                    continue;
                }
                anchor = t.position().add(h.offset);
                h.point = anchor;
            }
            Vec3 to = anchor.subtract(body);
            double d = to.length();
            if (d < 1.6 || d > HOOK_RANGE * 1.7) {
                h.active = false;
                continue;
            }
            attached++;
            accel = accel.add(to.scale(REEL_ACCEL / d));
            if (st.tick % 2 == 0) wire(level, body, anchor, d);
        }

        boolean boost = st.boosting;
        boolean hasGas = OdmData.gas(p) > 0;
        int cost = attached * HOOK_GAS_PER_TICK + (boost ? BOOST_GAS_PER_TICK : 0);

        if (cost > 0 && !hasGas) {
            if (st.tick % 10 == 0) OdmData.showGas(p); // shows EMPTY
            return;
        }

        if (boost) accel = accel.add(p.getLookAngle().scale(BOOST_ACCEL));

        if (cost > 0) {
            OdmData.drain(p, cost);
            Vec3 nv = vel.scale(0.985).add(accel).add(0, 0.04, 0); // slight lift so you glide
            double sp = nv.length();
            if (sp > MAX_SPEED) nv = nv.scale(MAX_SPEED / sp);
            launch(p, nv);
            st.safeTicks = 40;

            if (boost) {
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.8, pos.z, 3, 0.15, 0.15, 0.15, 0.02);
                if (st.tick % 8 == 0) sound(level, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.35F, 1.5F);
            }
            if (st.tick % 4 == 0) OdmData.showGas(p);
        }
    }

    /** Particle "wire" between the player and the hook anchor. */
    private static void wire(ServerLevel level, Vec3 from, Vec3 to, double dist) {
        int steps = (int) Math.min(24, Math.max(4, dist / 2));
        for (int i = 1; i <= steps; i++) {
            Vec3 pt = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 2, 0.1, 0.1, 0.1, 0.0);
    }

    // =============================================================================================
    //  Levi-style spinning strike
    // =============================================================================================
    private static void startSpin(ServerPlayer p, State st) {
        if (!OdmData.isEquipped(p)) {
            p.sendSystemMessage(Component.translatable("message.odmtitan.need_gear"), true);
            return;
        }
        if (st.cooldown > 0 || st.spinLeft > 0) return;
        if (OdmData.gas(p) < SPIN_GAS) {
            OdmData.showGas(p);
            return;
        }
        LivingEntity target = findTarget(p, st);
        if (target == null) {
            p.sendSystemMessage(Component.translatable("message.odmtitan.no_target"), true);
            return;
        }
        OdmData.drain(p, SPIN_GAS);
        st.targetId = target.getId();
        st.spinLeft = SPIN_TICKS;
        st.spinTotal = SPIN_TICKS;
        st.cooldown = SPIN_COOLDOWN + SPIN_TICKS;
        st.spinFlip = !st.spinFlip;
        release(st);
        ServerPlayNetworking.send(p, new SpinPayload(SPIN_TICKS));
        sound(p.level(), p.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8F, 1.6F);
    }

    /** Prefers a hooked living entity, otherwise the living entity closest to the crosshair. */
    private static LivingEntity findTarget(ServerPlayer p, State st) {
        ServerLevel level = p.level();
        for (Hook h : st.hooks) {
            if (h.active && h.entityId >= 0 && level.getEntity(h.entityId) instanceof LivingEntity le && le.isAlive()) {
                return le;
            }
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = 0.55; // minimum "dot" => roughly a 57 degree cone
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(32), e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
            double d = to.length();
            if (d < 1.0) continue;
            double score = to.scale(1.0 / d).dot(look) - d * 0.004;
            if (score > bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    private static void tickSpin(ServerPlayer p, State st) {
        ServerLevel level = p.level();
        if (!(level.getEntity(st.targetId) instanceof LivingEntity target) || !target.isAlive()) {
            st.spinLeft = 0;
            return;
        }
        int i = st.spinTotal - st.spinLeft;               // 0 .. spinTotal-1
        double prog = i / (double) Math.max(1, st.spinTotal - 1);
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 me = p.position().add(0, p.getBbHeight() * 0.5, 0);
        Vec3 to = center.subtract(me);
        double d = to.length();

        if (d < 2.2 || st.spinLeft <= 1) {
            strike(p, st, target, center);
            st.spinLeft = 0;
            return;
        }

        Vec3 dir = to.scale(1.0 / d);
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        Vec3 up = dir.cross(side).normalize();
        double sgn = st.spinFlip ? 1 : -1;
        double ang = i * 0.95 * sgn;
        double swirl = Math.min(1.0, d / 7.0);            // corkscrew tightens as we close in

        // forward dash + corkscrew sideways/vertical swing
        Vec3 v = dir.scale(Math.min(2.3, 0.9 + d * 0.16))
                .add(side.scale(Math.cos(ang) * 1.1 * swirl))
                .add(up.scale(Math.sin(ang) * 0.9 * swirl));
        launch(p, v);
        st.safeTicks = 40;

        // spinning ring of blade-flashes around the player
        for (int k = 0; k < 5; k++) {
            double a = ang * 2.0 + k * (Math.PI * 2.0 / 5.0);
            Vec3 pt = me.add(side.scale(Math.cos(a) * 1.2)).add(up.scale(Math.sin(a) * 1.2));
            level.sendParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CLOUD, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (i % 2 == 0) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, me.x, me.y, me.z, 1, 0, 0, 0, 0);
            sound(level, me, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, (float) (1.0 + prog * 0.7));
        }
        if (i % 3 == 0) OdmData.showGas(p);
    }

    private static void strike(ServerPlayer p, State st, LivingEntity target, Vec3 center) {
        ServerLevel level = p.level();
        int blades = (p.getMainHandItem().is(ModRegistry.ODM_BLADE) ? 1 : 0)
                + (p.getOffhandItem().is(ModRegistry.ODM_BLADE) ? 1 : 0);
        float dmg = 10.0F + 5.0F * blades;                // dual blades = 20 damage finishing slash

        for (int s = 0; s < 3; s++) {                     // three rapid slashes
            target.invulnerableTime = 0;
            target.hurtServer(level, level.damageSources().playerAttack(p), s == 2 ? dmg : dmg * 0.35F);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    center.x + (s - 1) * 0.5, center.y, center.z, 1, 0.3, 0.3, 0.3, 0.0);
        }
        // nearby enemies get clipped too
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0),
                e -> e != p && e != target && e.isAlive())) {
            le.hurtServer(level, level.damageSources().playerAttack(p), 6.0F);
        }
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 40, 0.6, 0.6, 0.6, 0.4);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        sound(level, center, SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.8F);
        sound(level, center, SoundEvents.IRON_GOLEM_ATTACK, 0.9F, 1.4F);

        // fly on through the target, up and over
        Vec3 away = p.position().subtract(center);
        Vec3 flat = new Vec3(-away.x, 0, -away.z).normalize();
        launch(p, flat.scale(0.7).add(0, 0.55, 0));
    }

    // =============================================================================================
    //  Helpers
    // =============================================================================================
    private static void launch(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        p.connection.resetFlyingTicks();
        p.fallDistance = 0;
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }
}
