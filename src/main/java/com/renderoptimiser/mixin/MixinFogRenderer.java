package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.NoFog;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link NoFog}: setupFog builds a fresh mutable FogData each frame and is the single choke point for
 * terrain + sky + cloud fog (identical signature in 26.1.2 and 26.2). Pushing every distance to
 * Float.MAX_VALUE reproduces vanilla's own internal "fog disabled" buffer values.
 */
@Mixin(FogRenderer.class)
public abstract class MixinFogRenderer {

    @Inject(
            method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
            at = @At("RETURN"), require = 0
    )
    private void tweaky$noFog(CallbackInfoReturnable<FogData> cir) {
        try {
            if (!NoFog.INSTANCE.enabled) return;
            FogData d = cir.getReturnValue();
            if (d == null) return;
            d.environmentalStart = Float.MAX_VALUE;
            d.environmentalEnd = Float.MAX_VALUE;
            d.renderDistanceStart = Float.MAX_VALUE;
            d.renderDistanceEnd = Float.MAX_VALUE;
            d.skyEnd = Float.MAX_VALUE;
            d.cloudEnd = Float.MAX_VALUE;
        } catch (Throwable ignored) {
        }
    }
}
