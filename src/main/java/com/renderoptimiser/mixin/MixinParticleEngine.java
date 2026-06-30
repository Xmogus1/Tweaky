package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.ParticleControl;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops particles before creation for {@link ParticleControl}. createParticle is the single funnel all
 * spawns go through (verified in both 26.1.2 and 26.2), and its callers already handle a null return, so
 * cancelling with null is exactly the vanilla "no particle" path.
 */
@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Inject(
            method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0
    )
    private void tweaky$filterParticle(ParticleOptions options, double x, double y, double z,
                                       double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        try {
            if (ParticleControl.shouldDrop(options)) cir.setReturnValue(null);
        } catch (Throwable ignored) {
        }
    }
}
