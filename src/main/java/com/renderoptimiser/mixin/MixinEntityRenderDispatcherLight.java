package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.DynamicLights;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * First-person half of {@link DynamicLights}: GameRenderer.renderItemInHand lights the view model (your
 * hands + held item) via EntityRenderDispatcher.getPackedLightCoords — a SEPARATE path from
 * EntityRenderer.getPackedLightCoords (MixinEntityRendererLight), so without this hook your own held
 * torch lights the world but not your hands. Identical signature in 26.1.2 and 26.2; merging is
 * max-based, so double application via any internal delegation is harmless.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcherLight {

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
}
