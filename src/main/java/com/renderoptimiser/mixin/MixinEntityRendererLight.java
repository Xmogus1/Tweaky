package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.DynamicLights;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity half of {@link DynamicLights}: merges the dynamic light level into every rendered entity's
 * packed light. Two redundant hooks (max-merge is idempotent, so both applying is harmless):
 *  - getPackedLightCoords (final) — the computation used by e.g. the first-person hand path.
 *  - extractRenderState TAIL — boosts the extracted state.lightCoords DIRECTLY, guaranteeing every
 *    rendered mob is covered even if the other injection point ever fails to apply (it is require=0).
 * Identical signatures/fields in 26.1.2 and 26.2 (EntityRenderState.lightCoords is public).
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererLight {

    @Inject(
            method = "getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I",
            at = @At("RETURN"), cancellable = true, require = 0
    )
    private void tweaky$dynamicLight(Entity entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        try {
            if (!DynamicLights.INSTANCE.enabled) return;
            int dyn = DynamicLights.getLightLevel(entity.blockPosition());
            if (dyn <= 0) return;
            int packed = cir.getReturnValueI();
            if (LightCoordsUtil.block(packed) < dyn) {
                cir.setReturnValue(LightCoordsUtil.withBlock(packed, dyn));
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("TAIL"), require = 0
    )
    private void tweaky$dynamicLightState(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        try {
            if (!DynamicLights.INSTANCE.enabled) return;
            int dyn = DynamicLights.getLightLevel(entity.blockPosition());
            if (dyn <= 0) return;
            if (LightCoordsUtil.block(state.lightCoords) < dyn) {
                state.lightCoords = LightCoordsUtil.withBlock(state.lightCoords, dyn);
            }
        } catch (Throwable ignored) {
        }
    }
}
