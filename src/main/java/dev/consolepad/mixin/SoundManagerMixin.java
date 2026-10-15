package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import dev.consolepad.split.SplitDiag;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los sonidos de P2 los oiria P1 duplicados (el servidor ya se los manda a ambos). */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void consolepad$mutePlay(SoundInstance sound, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) {
            if (consolepad$routeExtra(sound)) {
                SplitDiag.soundsRouted++;
            } else {
                SplitDiag.soundsMuted++;
                ci.cancel();
            }
        } else if (consolepad$nearerExtra(sound)) { // lo reproducira la copia de un jugador extra que esta mas cerca
            SplitDiag.soundsMuted++;
            ci.cancel();
        } else {
            SplitDiag.soundsPlayed++;
            consolepad$occlude(sound);
        }
    }

    /** Sonido con posicion en el mundo (no musica, no interfaz). */
    private static boolean consolepad$spatial(SoundInstance sound) {
        return !sound.isRelative() && sound.getAttenuation() != SoundInstance.Attenuation.NONE
                && sound instanceof net.minecraft.client.resources.sounds.AbstractSoundInstance;
    }

    /** En P1: true si otro jugador local esta mas cerca del sonido (y por tanto lo oye el). */
    private static boolean consolepad$nearerExtra(SoundInstance sound) {
        if (!dev.consolepad.split.SplitSettings.soundPerPlayer() || !dev.consolepad.split.SecondaryPlayers.anyActive() || !consolepad$spatial(sound)) return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return false;
        int near = dev.consolepad.split.SoundRouting.nearest(dev.consolepad.split.SoundRouting.listeners(mc),
                new net.minecraft.world.phys.Vec3(sound.getX(), sound.getY(), sound.getZ()), mc.level.dimension());
        return near > 0;
    }

    /**
     * En un jugador extra: si el es el mas cercano al sonido, lo deja sonar (con la oclusion desde SU posicion) movido al marco de P1, que es el oyente
     * de OpenAL; si no, false (se silencia: lo oye otro).
     */
    private static boolean consolepad$routeExtra(SoundInstance sound) {
        if (!dev.consolepad.split.SplitSettings.soundPerPlayer() || !consolepad$spatial(sound)) return false;
        var ctx = ContextSwitcher.current();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (ctx == null || mc.level == null) return false;
        var all = dev.consolepad.split.SoundRouting.listeners(mc);
        var pos = new net.minecraft.world.phys.Vec3(sound.getX(), sound.getY(), sound.getZ());
        if (dev.consolepad.split.SoundRouting.nearest(all, pos, mc.level.dimension()) != ctx.slotIndex) return false;
        dev.consolepad.split.SoundRouting.Listener me = null, p1 = null;
        for (var l : all) {
            if (l.slot() == ctx.slotIndex) me = l;
            if (l.slot() == 0) p1 = l;
        }
        if (me == null || p1 == null) return false;
        consolepad$occlude(sound); // mc.level y mc.player son los de este jugador
        var moved = dev.consolepad.split.SoundRouting.translate(pos, me, p1);
        AbstractSoundInstanceAccessor acc = (AbstractSoundInstanceAccessor) sound;
        acc.consolepad$setX(moved.x);
        acc.consolepad$setY(moved.y);
        acc.consolepad$setZ(moved.z);
        return true;
    }

    /** Un mob detras de bloques suena mas bajo (vanilla lo oye a traves del suelo). Solo sonidos de mobs con posicion en el mundo. */
    private static void consolepad$occlude(SoundInstance sound) {
        if (!dev.consolepad.split.SplitSettings.soundOcclusion() || !(sound instanceof net.minecraft.client.resources.sounds.AbstractSoundInstance a)) return;
        var src = sound.getSource();
        if (src != net.minecraft.sounds.SoundSource.HOSTILE && src != net.minecraft.sounds.SoundSource.NEUTRAL) return;
        if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        net.minecraft.world.level.Level level = mc.level;
        float f = dev.consolepad.split.SoundOcclusion.factor(p -> level.getBlockState(p).canOcclude(), mc.player.getEyePosition(),
                new net.minecraft.world.phys.Vec3(sound.getX(), sound.getY(), sound.getZ()));
        if (f < 1f) {
            AbstractSoundInstanceAccessor acc = (AbstractSoundInstanceAccessor) a;
            acc.consolepad$setVolume(acc.consolepad$volume() * f);
            SplitDiag.soundsOccluded++;
        }
    }

    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void consolepad$mutePlayDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }

    /**
     * Sonidos continuos de entidades (abejas, guardianes, mobs de mods...): cada jugador extra tiene su propio mundo cliente con copias de los mismos mobs,
     * y sus sonidos en bucle se anadian a los del jugador 1 (el doble de fuertes).
     */
    @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
    private void consolepad$muteTicking(net.minecraft.client.resources.sounds.TickableSoundInstance sound, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) {
            SplitDiag.tickingMuted++;
            ci.cancel();
        } else {
            SplitDiag.tickingQueued++;
        }
    }
}
